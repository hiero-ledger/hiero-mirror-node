#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils.sh

versionGreater() {
  local raw1="$1"
  local raw2="$2"

  local core1="${raw1%%-*}"
  local core2="${raw2%%-*}"

  if [[ "$(printf '%s\n' "$core1" "$core2" | sort -V | head -n1)" != "$core1" ]]; then
    return 0
  elif [[ "$core1" == "$core2" ]]; then
    local build1 build2
    build1="$(echo "$raw1" | sed -n 's/.*gke\.//p')"
    build2="$(echo "$raw2" | sed -n 's/.*gke\.//p')"

    if [[ -n "$build1" && -n "$build2" ]]; then
      if [[ "$build1" -gt "$build2" ]]; then
        return 0
      fi
    fi
  fi

  return 1
}

NAMESPACES=($(kubectl get sgshardedclusters.stackgres.io -A -o jsonpath='{.items[*].metadata.namespace}'))

GCP_TARGET_PROJECT="$(readUserInput "Enter GCP Project for target: ")"
if [[ -z "${GCP_TARGET_PROJECT}" ]]; then
  log "GCP_TARGET_PROJECT is not set and is required. Exiting"
  exit 1
else
  gcloud projects describe "${GCP_TARGET_PROJECT}" >/dev/null
fi

GCP_K8S_TARGET_CLUSTER_REGION="$(readUserInput "Enter target cluster region: ")"
if [[ -z "${GCP_K8S_TARGET_CLUSTER_REGION}" ]]; then
  log "GCP_K8S_TARGET_CLUSTER_REGION is not set and is required. Exiting"
  exit 1
else
  gcloud compute regions describe "${GCP_K8S_TARGET_CLUSTER_REGION}" --project "${GCP_TARGET_PROJECT}" >/dev/null
fi

GCP_K8S_TARGET_CLUSTER_NAME="$(readUserInput "Enter target cluster name: ")"
if [[ -z "${GCP_K8S_TARGET_CLUSTER_NAME}" ]]; then
  log "GCP_K8S_TARGET_CLUSTER_NAME is not set and is required. Exiting"
  exit 1
else
  gcloud container clusters describe --project "${GCP_TARGET_PROJECT}" \
    --region="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    "${GCP_K8S_TARGET_CLUSTER_NAME}" >/dev/null
fi

VERSION="$(readUserInput "Enter the new Kubernetes version: ")"
if [[ -z "${VERSION}" ]]; then
  log "VERSION is not set and is required. Exiting"
  exit 1
fi

UPGRADE_MASTER="$(readUserInput "Do you want to upgrade the master to ${VERSION}? (y/n): ")"
UPGRADE_MASTER=$(echo "${UPGRADE_MASTER}" | tr '[:upper:]' '[:lower:]')

if [[ "${UPGRADE_MASTER}" == "yes" || "${UPGRADE_MASTER}" == "y" ]]; then
  log "Checking if version ${VERSION} is valid for the cluster master"
  MASTER_SUPPORTED=$(gcloud container get-server-config \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --project="${GCP_TARGET_PROJECT}" \
    --format="json(validMasterVersions)" |
    jq -r --arg VERSION "${VERSION}" 'any(.validMasterVersions[]; . == $VERSION)')

  if [[ "${MASTER_SUPPORTED}" != "true" ]]; then
    log "Version ${VERSION} is not supported by the cluster master. Exiting."
    exit 1
  fi

  CURRENT_MASTER_VERSION=$(gcloud container clusters describe "${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --region="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --project="${GCP_TARGET_PROJECT}" \
    --format="value(currentMasterVersion)")

  if ! versionGreater "${VERSION}" "${CURRENT_MASTER_VERSION}"; then
    log "Version ${VERSION} must be greater than the current master version ${CURRENT_MASTER_VERSION}. Exiting."
    exit 1
  fi
fi

log "Checking if version ${VERSION} is valid for node pools..."
POOLS_SUPPORTED=$(gcloud container get-server-config \
  --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
  --project="${GCP_TARGET_PROJECT}" \
  --format="json(validNodeVersions)" |
  jq -r --arg VERSION "${VERSION}" 'any(.validNodeVersions[]; . == $VERSION)')

if [[ "${POOLS_SUPPORTED}" != "true" ]]; then
  log "Version ${VERSION} is not supported by node pools. Exiting."
  exit 1
fi

AVAILABLE_POOLS="$(gcloud container node-pools list --project="${GCP_TARGET_PROJECT}" --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" --region="${GCP_K8S_TARGET_CLUSTER_REGION}" --format="json(name)" | jq -r '.[].name' | tr '\n' ' ')"
POOLS_TO_UPDATE_INPUT="$(readUserInput "Enter the node pools(${AVAILABLE_POOLS}) to update (space-separated): ")"
if [[ -z "${POOLS_TO_UPDATE_INPUT}" ]]; then
  log "POOLS_TO_UPDATE_INPUT is not set and is required. Exiting"
  exit 1
else
  IFS=', ' read -r -a POOLS_TO_UPDATE <<<"${POOLS_TO_UPDATE_INPUT}"
  for pool in "${POOLS_TO_UPDATE[@]}"; do
    gcloud container node-pools describe "${pool}" --project="${GCP_TARGET_PROJECT}" --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" --region="${GCP_K8S_TARGET_CLUSTER_REGION}" >/dev/null
  done
fi

POOLS_WITH_CITUS_ROLE=()
POOLS_WITHOUT_CITUS_ROLE=()

for pool in "${POOLS_TO_UPDATE[@]}"; do
  CURRENT_POOL_VERSION=$(gcloud container node-pools describe "${pool}" \
      --project="${GCP_TARGET_PROJECT}" \
      --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" \
      --region="${GCP_K8S_TARGET_CLUSTER_REGION}" \
      --format="value(version)")

  if ! versionGreater "${VERSION}" "${CURRENT_POOL_VERSION}"; then
    log "Version ${VERSION} must be greater than current version ${CURRENT_POOL_VERSION} for pool ${pool}. Skipping."
    exit 1
  fi
  LABELS_JSON=$(gcloud container node-pools describe "${pool}" \
    --project="${GCP_TARGET_PROJECT}" \
    --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --region="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --format="json(config.labels)")

  if echo "${LABELS_JSON}" | jq -e '.config.labels["citus-role"]' >/dev/null; then
    POOLS_WITH_CITUS_ROLE+=("${pool}")
  else
    POOLS_WITHOUT_CITUS_ROLE+=("${pool}")
  fi
done

while true; do
  SYSTEM_CONFIG_FILE="$(readUserInput 'Enter path to Linux config file (leave blank to skip): ')"

  if [[ -z "${SYSTEM_CONFIG_FILE}" ]]; then
    break
  elif [[ -f "${SYSTEM_CONFIG_FILE}" ]]; then
    break
  else
    log "File '${SYSTEM_CONFIG_FILE}' does not exist. Please enter a valid path or leave blank to skip."
  fi
done

function upgradePool() {
  local pool="$1"
  log "Upgrading node pool: ${pool}"
  local args=(
    "${GCP_K8S_TARGET_CLUSTER_NAME}"
    --node-pool="${pool}"
    --cluster-version="${VERSION}"
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}"
    --project="${GCP_TARGET_PROJECT}"
  )

  if [[ -n "${SYSTEM_CONFIG_FILE}" ]]; then
    args+=(--system-config-from-file="${SYSTEM_CONFIG_FILE}")
  fi

  gcloud container clusters upgrade "${args[@]}"
}

function upgradeCitusPools() {
  for namespace in "${NAMESPACES[@]}"; do
    unrouteTraffic "${namespace}"
    pauseCitus "${namespace}"
  done

  for pool in "${POOLS_WITH_CITUS_ROLE[@]}"; do
    upgradePool "${pool}"
  done

  for namespace in "${NAMESPACES[@]}"; do
    unpauseCitus "${namespace}"
    routeTraffic "${namespace}"
  done
}

if [[ "${UPGRADE_MASTER}" == "yes" || "${UPGRADE_MASTER}" == "y" ]]; then
  log "Upgrading master to Kubernetes version ${VERSION}"
  gcloud container clusters upgrade "${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --master \
    --cluster-version="${VERSION}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --project="${GCP_TARGET_PROJECT}"
else
  log "Skipping master upgrade as requested."
fi

for pool in "${POOLS_WITHOUT_CITUS_ROLE[@]}"; do
  upgradePool "${pool}"
done

upgradeCitusPools
