#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils/utils.sh

TARGET_NAMESPACE="${TARGET_NAMESPACE:?TARGET_NAMESPACE is required}"
TARGET_SHARDED_CLUSTER="${TARGET_SHARDED_CLUSTER:-${HELM_RELEASE_NAME}-citus}"
TARGET_CITUS_VERSION="${TARGET_CITUS_VERSION:?TARGET_CITUS_VERSION is required, e.g. 14.0.0}"

# SQL extension version returned by:
# SELECT extversion FROM pg_extension WHERE extname = 'citus';
#
# Example:
#   TARGET_CITUS_VERSION=14.0.0
#   EXPECTED_CITUS_EXTVERSION=14.0-1
EXPECTED_CITUS_EXTVERSION="${EXPECTED_CITUS_EXTVERSION:?EXPECTED_CITUS_EXTVERSION is required, e.g. 14.0-1}"

DATABASE_NAME="${DATABASE_NAME:-mirror_node}"
DB_USER="${DB_USER:-postgres}"
RUN_VALIDATION="${RUN_VALIDATION:-true}"
STACKGRES_CLUSTER_LABEL="${STACKGRES_CLUSTER_LABEL:-app=StackGresShardedCluster}"

function versionLessThan() {
  [[ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1)" == "$1" && "$1" != "$2" ]]
}

function validateCitusUpgradeTarget() {
  local currentVersion

  if [[ "${RUN_VALIDATION}" == "false" ]]; then
    log "Skipping version validation"
    return 0
  fi

  currentVersion="$(kubectl exec -n "${TARGET_NAMESPACE}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- \
    psql -U "${DB_USER}" -d "${DATABASE_NAME}" -Atc "SELECT extversion FROM pg_extension WHERE extname = 'citus';" | xargs)"

  if [[ -z "${currentVersion}" ]]; then
    log "Unable to determine current Citus extension version"
    exit 1
  fi

  if [[ "${currentVersion}" == "${EXPECTED_CITUS_EXTVERSION}" ]]; then
    log "Citus extension is already at ${currentVersion}; expected target is ${EXPECTED_CITUS_EXTVERSION}"
    exit 1
  fi

  if ! versionLessThan "${currentVersion}" "${EXPECTED_CITUS_EXTVERSION}"; then
    log "Refusing Citus downgrade: current=${currentVersion}, expected target=${EXPECTED_CITUS_EXTVERSION}"
    exit 1
  fi

  log "Validated Citus extension upgrade: ${currentVersion} -> ${EXPECTED_CITUS_EXTVERSION}"
  log "StackGres Citus package version target: ${TARGET_CITUS_VERSION}"
}

function getSgClusters() {
  kubectl get sgclusters.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    -l "${STACKGRES_CLUSTER_LABEL}" \
    -o json | jq -r '.items | sort_by(.metadata.name)[] | .metadata.name'
}

function getShardClustersFirstThenCoordinator() {
  getSgClusters | awk '
    /coord/ { coord[++c]=$0; next }
    { print }
    END { for (i=1; i<=c; i++) print coord[i] }
  '
}

function getClusterPrimaryPod() {
  local cluster="${1}"

  kubectl get sgcluster.stackgres.io "${cluster}" \
    -n "${TARGET_NAMESPACE}" \
    -o json | jq -r '
      .status.podStatuses[]
      | select(.primary == true)
      | .name
    '
}

function patchShardedClusterCitusVersion() {
  log "Patching ${TARGET_SHARDED_CLUSTER} StackGres Citus package version to ${TARGET_CITUS_VERSION}"

  local hasCitus
  hasCitus="$(kubectl get sgshardedcluster "${TARGET_SHARDED_CLUSTER}" -n "${TARGET_NAMESPACE}" -o json \
    | jq '[.spec.postgres.extensions[]?.name == "citus"] | any')"

  if [[ "${hasCitus}" != "true" ]]; then
    log "Citus extension not found in cluster spec. Refusing to continue."
    exit 1
  fi

  kubectl get sgshardedcluster "${TARGET_SHARDED_CLUSTER}" -n "${TARGET_NAMESPACE}" -o json \
    | jq --arg citusVersion "${TARGET_CITUS_VERSION}" '
        .spec.postgres.extensions |= map(
          if .name == "citus" then
            .version = $citusVersion
          else
            .
          end
        )
      ' \
    | kubectl replace -f -
}

function waitForClusterPrimary() {
  local cluster="${1}"
  local primaryPod

  log "Waiting for ${cluster} primary pod to appear"

  while true; do
    primaryPod="$(getClusterPrimaryPod "${cluster}" || true)"
    if [[ -n "${primaryPod}" && "${primaryPod}" != "null" ]]; then
      break
    fi

    log "No primary found for ${cluster}. Retrying"
    sleep 5
  done

  waitForPodReady "${TARGET_NAMESPACE}" "${primaryPod}"
  waitUntilOutOfRecovery "${TARGET_NAMESPACE}" "${primaryPod}"

  echo "${primaryPod}"
}

function unpauseSgCluster() {
  local cluster="${1}"

  log "Unpausing SGCluster ${cluster}"
  kubectl annotate sgclusters.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    "${cluster}" \
    stackgres.io/reconciliation-pause- \
    --overwrite

  waitForSGClusterReady "${TARGET_NAMESPACE}" "${cluster}"
}

function updateCitusExtensionOnPod() {
  local pod="${1}"

  log "Running ALTER EXTENSION citus UPDATE on ${pod}"

  while true; do
    if kubectl exec -n "${TARGET_NAMESPACE}" "${pod}" -c postgres-util -- \
      psql -v ON_ERROR_STOP=1 \
        -U "${DB_USER}" \
        -d "${DATABASE_NAME}" \
        -c "SET lock_timeout = '30s'; SET statement_timeout = '10min'; ALTER EXTENSION citus UPDATE;"; then
      return 0
    fi

    log "ALTER EXTENSION failed on ${pod}. Fix manually if needed; retrying in 15s"
    sleep 15
  done
}

function waitForCitusVersionOnPod() {
  local pod="${1}"
  local version

  log "Waiting for ${pod} Citus SQL extension version to become ${EXPECTED_CITUS_EXTVERSION}"

  while true; do
    version="$(kubectl exec -n "${TARGET_NAMESPACE}" "${pod}" -c postgres-util -- \
      psql -v ON_ERROR_STOP=1 \
        -U "${DB_USER}" \
        -d "${DATABASE_NAME}" \
        -Atc "SELECT extversion FROM pg_extension WHERE extname = 'citus';" \
      2>/dev/null | xargs || true)"

    if [[ "${version}" == "${EXPECTED_CITUS_EXTVERSION}" ]]; then
      log "Pod ${pod} Citus extversion=${version}"
      return 0
    fi

    log "Pod ${pod} Citus version is ${version:-unknown}; expected ${EXPECTED_CITUS_EXTVERSION}. Retrying in 15s"
    sleep 15
  done
}

function updateEachCluster() {
  local clusters cluster primaryPod

  mapfile -t clusters < <(getShardClustersFirstThenCoordinator)

  log "Will update Citus extension in this order:"
  printf ' - %s\n' "${clusters[@]}" >&2
  doContinue

  for cluster in "${clusters[@]}"; do
    unpauseSgCluster "${cluster}"

    primaryPod="$(waitForClusterPrimary "${cluster}")"
    log "Primary for ${cluster}: ${primaryPod}"

    updateCitusExtensionOnPod "${primaryPod}"
    waitForCitusVersionOnPod "${primaryPod}"
  done
}

function upgradeCitusVersion() {
  log "Starting Citus extension upgrade"
  log "Namespace: ${TARGET_NAMESPACE}"
  log "Sharded cluster: ${TARGET_SHARDED_CLUSTER}"
  log "StackGres Citus package version: ${TARGET_CITUS_VERSION}"
  log "Expected Citus SQL extension version: ${EXPECTED_CITUS_EXTVERSION}"
  doContinue

  validateCitusUpgradeTarget

  unrouteTraffic "${TARGET_NAMESPACE}"

  pauseCitus "${TARGET_NAMESPACE}"
  patchShardedClusterCitusVersion
  updateEachCluster

  routeTraffic "${TARGET_NAMESPACE}"

  log "Citus extension upgrade completed"
}

upgradeCitusVersion
