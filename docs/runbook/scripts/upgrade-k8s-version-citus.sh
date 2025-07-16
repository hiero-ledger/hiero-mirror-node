#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euox pipefail

source ./utils.sh

NAMESPACES=($(kubectl get sgshardedclusters.stackgres.io -A -o jsonpath='{.items[*].metadata.namespace}'))

GCP_PROJECT="$(readUserInput "Enter GCP Project for target: ")"
if [[ -z "${GCP_PROJECT}" ]]; then
  log "GCP_PROJECT is not set and is required. Exiting"
  exit 1
else
  gcloud projects describe "${GCP_PROJECT}" >/dev/null
fi

GCP_K8S_CLUSTER_REGION="$(readUserInput "Enter target cluster region: ")"
if [[ -z "${GCP_K8S_CLUSTER_REGION}" ]]; then
  log "GCP_K8S_CLUSTER_REGION is not set and is required. Exiting"
  exit 1
else
  gcloud compute regions describe "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" >/dev/null
fi

GCP_K8S_CLUSTER_NAME="$(readUserInput "Enter target cluster name: ")"
if [[ -z "${GCP_K8S_CLUSTER_NAME}" ]]; then
  log "GCP_K8S_CLUSTER_NAME is not set and is required. Exiting"
  exit 1
else
  gcloud container clusters describe --project "${GCP_PROJECT}" \
    --region="${GCP_K8S_CLUSTER_REGION}" \
    "${GCP_K8S_CLUSTER_NAME}" >/dev/null
fi

VERSION="$(readUserInput "Enter the new Kubernetes version: ")"
if [[ -z "${VERSION}" ]]; then
  log "VERSION is not set and is required. Exiting"
  exit 1
else
  log "Checking if version ${VERSION} is valid for the cluster master"
  MASTER_SUPPORTED=$(gcloud container get-server-config \
    --location="${GCP_K8S_CLUSTER_REGION}" \
    --project="${GCP_PROJECT}" \
    --format="json(validMasterVersions)" |
    jq -r --arg VERSION "${VERSION}" 'any(.validMasterVersions[]; . == $VERSION)')

  if [[ "${MASTER_SUPPORTED}" != "true" ]]; then
    log "Version ${VERSION} is not supported by the cluster master. Exiting."
    exit 1
  fi

  log "Checking if version ${VERSION} is valid for node pools..."
  POOLS_SUPPORTED=$(gcloud container get-server-config \
    --location="${GCP_K8S_CLUSTER_REGION}" \
    --project="${GCP_PROJECT}" \
    --format="json(validNodeVersions)" |
    jq -r --arg VERSION "${VERSION}" 'any(.validNodeVersions[]; . == $VERSION)')

  if [[ "${POOLS_SUPPORTED}" != "true" ]]; then
    log "Node pool '${pool}' does not support version ${VERSION}. Exiting."
    exit 1
  fi
fi

AVAILABLE_POOLS="$(gcloud container node-pools list --project="${GCP_PROJECT}" --cluster="${GCP_K8S_CLUSTER_NAME}" --region="${GCP_K8S_CLUSTER_REGION}" --format="json(name)" | jq -r '.[].name' | tr '\n' ' ')"
POOLS_TO_UPDATE_INPUT="$(readUserInput "Enter the node pools(${AVAILABLE_POOLS}) to update (space-separated): ")"
if [[ -z "${POOLS_TO_UPDATE_INPUT}" ]]; then
  log "POOLS_TO_UPDATE_INPUT is not set and is required. Exiting"
  exit 1
else
  IFS=', ' read -r -a POOLS_TO_UPDATE <<<"${POOLS_TO_UPDATE_INPUT}"
  for pool in "${POOLS_TO_UPDATE[@]}"; do
    gcloud container node-pools describe "${pool}" --project="${GCP_PROJECT}" --cluster="${GCP_K8S_CLUSTER_NAME}" --region="${GCP_K8S_CLUSTER_REGION}" >/dev/null
  done
fi

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

upgradePool() {
  local pool="$1"
  log "Upgrading node pool: ${pool}"

  local args=(
    "${GCP_K8S_CLUSTER_NAME}"
    --node-pool="${pool}"
    --cluster-version="${VERSION}"
    --location="${GCP_K8S_CLUSTER_REGION}"
    --project="${GCP_PROJECT}"
  )

  if [[ -n "${SYSTEM_CONFIG_FILE}" ]]; then
    args+=(--system-config-from-file="${SYSTEM_CONFIG_FILE}")
  fi

  gcloud container clusters upgrade "${args[@]}"
}

POOLS_WITH_CITUS_ROLE=()
POOLS_WITHOUT_CITUS_ROLE=()

for pool in "${POOLS_TO_UPDATE[@]}"; do
  LABELS_JSON=$(gcloud container node-pools describe "${pool}" \
    --project="${GCP_PROJECT}" \
    --cluster="${GCP_K8S_CLUSTER_NAME}" \
    --region="${GCP_K8S_CLUSTER_REGION}" \
    --format="json(config.labels)")

  if echo "${LABELS_JSON}" | jq -e '.config.labels["citus-role"]' >/dev/null; then
    POOLS_WITH_CITUS_ROLE+=("${pool}")
  else
    POOLS_WITHOUT_CITUS_ROLE+=("${pool}")
  fi
done

function upgradePool() {
  local pool="$1"
  log "Upgrading node pool: ${pool}"
  gcloud container clusters upgrade "${GCP_K8S_CLUSTER_NAME}" \
    --node-pool="${pool}" \
    --cluster-version="${VERSION}" \
    --location="${GCP_K8S_CLUSTER_REGION}" \
    --project="${GCP_PROJECT}"
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

log "Upgrading master to Kubernetes version ${VERSION}"
gcloud container clusters upgrade "${GCP_K8S_CLUSTER_NAME}" \
  --master \
  --cluster-version="${VERSION}" \
  --location="${GCP_K8S_CLUSTER_REGION}" \
  --project="${GCP_PROJECT}"

for pool in "${POOLS_WITHOUT_CITUS_ROLE[@]}"; do
  upgradePool "${pool}"
done

upgradeCitusPools
