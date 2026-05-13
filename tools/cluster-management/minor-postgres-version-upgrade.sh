#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

source ./utils/utils.sh

RUN_STACKGRES_UPGRADE=${RUN_STACKGRES_UPGRADE:-false}
SGDBOPS_NAME="${SGDBOPS_NAME:-stackgres-upgrade}"
TARGET_NAMESPACE="${TARGET_NAMESPACE:?TARGET_NAMESPACE is required}"
TARGET_SHARDED_CLUSTER="${TARGET_SHARDED_CLUSTER:-${HELM_RELEASE_NAME}-citus}"
TARGET_POSTGRES_VERSION="${TARGET_POSTGRES_VERSION:?TARGET_POSTGRES_VERSION is required, e.g. 16.13}"
UPGRADE_COMMON_CHART="${UPGRADE_COMMON_CHART:-false}"

#TODO maybe remove this and expect common to be deployed already (already removed function but need to include this in .md)

function patchPostgresVersion() {
  log "Patching SGShardedCluster ${TARGET_SHARDED_CLUSTER} to Postgres ${TARGET_POSTGRES_VERSION}"

  kubectl patch sgshardedcluster.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    "${TARGET_SHARDED_CLUSTER}" \
    --type='merge' \
    -p "{\"spec\":{\"postgres\":{\"version\":\"${TARGET_POSTGRES_VERSION}\"}}}"
}

function waitForDbOps() {
  log "Waiting for SGShardedDbOps ${SGDBOPS_NAME} to complete"

  while true; do
    local json completed failed phase

    json="$(kubectl get sgshardeddbops.stackgres.io \
      -n "${TARGET_NAMESPACE}" \
      "${SGDBOPS_NAME}" \
      -o json)"

    completed="$(jq -r '
      [.status.conditions[]? | select(.type == "Completed" and .status == "True")] | length
    ' <<< "${json}")"

    failed="$(jq -r '
      [.status.conditions[]? | select((.type == "Failed" or .type == "Failure") and .status == "True")] | length
    ' <<< "${json}")"

    phase="$(jq -r '.status.opStarted // .status.phase // .status.securityUpgrade.phase // "unknown"' <<< "${json}")"

    if [[ "${completed}" == "1" ]]; then
      log "SGShardedDbOps ${SGDBOPS_NAME} completed"
      break
    fi

    if [[ "${failed}" != "0" ]]; then
      log "SGShardedDbOps ${SGDBOPS_NAME} failed"
      kubectl describe sgshardeddbops.stackgres.io -n "${TARGET_NAMESPACE}" "${SGDBOPS_NAME}" || true
      exit 1
    fi

    log "SGShardedDbOps ${SGDBOPS_NAME} still running. Phase/status: ${phase}"
    sleep 10
  done
}

function createSecurityUpgradeDbOps() {
  if [[ "${RUN_STACKGRES_UPGRADE}" == "false" ]]; then
    log "Skipping sg upgrade op"
    return 0
  fi

  log "Creating SGShardedDbOps ${SGDBOPS_NAME}"

  kubectl delete sgshardeddbops.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    "${SGDBOPS_NAME}" \
    --ignore-not-found=true

  cat <<EOF | kubectl apply -n "${TARGET_NAMESPACE}" -f -
apiVersion: stackgres.io/v1
kind: SGShardedDbOps
metadata:
  name: ${SGDBOPS_NAME}
spec:
  maxRetries: 1
  op: securityUpgrade
  scheduling:
    priorityClassName: critical
  securityUpgrade:
    method: InPlace
  sgShardedCluster: ${TARGET_SHARDED_CLUSTER}
EOF
  waitForDbOps
}

function verifyPostgresVersion() {
  log "Verifying Postgres version from coordinator"

  kubectl exec -n "${TARGET_NAMESPACE}" \
    "${HELM_RELEASE_NAME}-citus-coord-0" \
    -c postgres-util -- \
    psql -U postgres -d mirror_node -c "SHOW server_version;"

  kubectl get sgshardedcluster.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    "${TARGET_SHARDED_CLUSTER}" \
    -o jsonpath='{.status.postgresVersion}{"\n"}'
}

function versionMajor() {
  echo "$1" | cut -d. -f1
}

function versionSortHead() {
  printf '%s\n%s\n' "$1" "$2" | sort -V | head -n1
}

function getCurrentPostgresVersion() {
  kubectl get sgshardedcluster.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    "${TARGET_SHARDED_CLUSTER}" \
    -o jsonpath='{.status.postgresVersion}'
}

function validateMinorUpgradeTarget() {
  local currentVersion currentMajor targetMajor lowest

  currentVersion="$(getCurrentPostgresVersion)"
  currentMajor="$(versionMajor "${currentVersion}")"
  targetMajor="$(versionMajor "${TARGET_POSTGRES_VERSION}")"

  if [[ -z "${currentVersion}" ]]; then
    log "Unable to determine current Postgres version"
    exit 1
  fi

  if [[ "${currentMajor}" != "${targetMajor}" ]]; then
    log "TARGET_POSTGRES_VERSION=${TARGET_POSTGRES_VERSION} is not a minor upgrade from current=${currentVersion}. Major versions differ."
    exit 1
  fi

  if [[ "${currentVersion}" == "${TARGET_POSTGRES_VERSION}" ]]; then
    log "TARGET_POSTGRES_VERSION=${TARGET_POSTGRES_VERSION} is already the current version"
    exit 1
  fi

  lowest="$(versionSortHead "${currentVersion}" "${TARGET_POSTGRES_VERSION}")"
  if [[ "${lowest}" != "${currentVersion}" ]]; then
    log "TARGET_POSTGRES_VERSION=${TARGET_POSTGRES_VERSION} is lower than current=${currentVersion}; refusing downgrade"
    exit 1
  fi

  log "Validated minor Postgres upgrade: ${currentVersion} -> ${TARGET_POSTGRES_VERSION}"
}

function waitForShardedClusterVersion() {
  log "Waiting for SGShardedCluster status.postgresVersion=${TARGET_POSTGRES_VERSION}"

  until [[ "$(getCurrentPostgresVersion)" == "${TARGET_POSTGRES_VERSION}" ]]; do
    log "Current SGShardedCluster version is $(getCurrentPostgresVersion); waiting"
    sleep 10
  done
}

function validateSgClusterVersions() {
  log "Validating all SGCluster statuses are Postgres ${TARGET_POSTGRES_VERSION}"

  local mismatches
  mismatches="$(kubectl get sgclusters.stackgres.io \
    -n "${TARGET_NAMESPACE}" \
    -l "stackgres.io/shardedcluster-name=${TARGET_SHARDED_CLUSTER}" \
    -o json | jq -r --arg target "${TARGET_POSTGRES_VERSION}" '
      .items[]
      | select(.status.postgresVersion != $target)
      | "\(.metadata.name)=\(.status.postgresVersion // "null")"
    ')"

  if [[ -n "${mismatches}" ]]; then
    log "Some SGClusters are not at ${TARGET_POSTGRES_VERSION}: ${mismatches}"
    exit 1
  fi
}

function validateRunningPostgresVersions() {
  log "Validating running Postgres server versions on all StackGres primary pods"

  local pods
  mapfile -t pods < <(waitForPrimaryPods "${TARGET_NAMESPACE}")

  for pod in "${pods[@]}"; do
    local serverVersion
    serverVersion="$(kubectl exec -n "${TARGET_NAMESPACE}" "${pod}" -c postgres-util -- \
      psql -U postgres -d mirror_node -Atc "SHOW server_version" | xargs)"

    if [[ "${serverVersion}" != "${TARGET_POSTGRES_VERSION}"* ]]; then
      log "Pod ${pod} has server_version=${serverVersion}, expected ${TARGET_POSTGRES_VERSION}"
      exit 1
    fi

    log "Pod ${pod} is running PostgreSQL ${serverVersion}"
  done
}

function validatePostgresUpgrade() {
  waitForShardedClusterVersion
  validateSgClusterVersions
  validateRunningPostgresVersions
}

function upgradePostgresMinorVersion() {
  log "Starting minor Postgres upgrade for ${TARGET_SHARDED_CLUSTER} in ${TARGET_NAMESPACE}"
  log "Target Postgres version: ${TARGET_POSTGRES_VERSION}"

  validateMinorUpgradeTarget

  unrouteTraffic "TARGET_NAMESPACE"
  patchPostgresVersion

  createSecurityUpgradeDbOps
  validatePostgresUpgrade
  routeTraffic "${TARGET_NAMESPACE}"

  log "Minor Postgres upgrade completed"
}

upgradePostgresMinorVersion
