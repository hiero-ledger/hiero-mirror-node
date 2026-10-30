#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils/utils.sh

requireCommand gcloud

COMPACT_FAMILIES=(a2 a3 a4 c2 c2d c3 c3d c4 c4d g2 g4 h3 h4d n2 n2d)
HYPERDISK_ONLY_FAMILIES=(c4 c4a c4d c4n h4d m4 n4 n4a n4d x4)

GCP_TARGET_PROJECT="$(readUserInput "Enter GCP Project for target: ")"
if [[ -z "${GCP_TARGET_PROJECT}" ]]; then
  log "GCP_TARGET_PROJECT is not set and is required. Exiting"
  exit 1
else
  gcloud projects describe "${GCP_TARGET_PROJECT}" > /dev/null
fi

GCP_K8S_TARGET_CLUSTER_REGION="$(readUserInput "Enter target cluster region: ")"
if [[ -z "${GCP_K8S_TARGET_CLUSTER_REGION}" ]]; then
  log "GCP_K8S_TARGET_CLUSTER_REGION is not set and is required. Exiting"
  exit 1
else
  gcloud compute regions describe "${GCP_K8S_TARGET_CLUSTER_REGION}" --project "${GCP_TARGET_PROJECT}"  > /dev/null
fi

GCP_K8S_TARGET_CLUSTER_NAME="$(readUserInput "Enter target cluster name: ")"
if [[ -z "${GCP_K8S_TARGET_CLUSTER_NAME}" ]]; then
  log "GCP_K8S_TARGET_CLUSTER_NAME is not set and is required. Exiting"
  exit 1
else
  gcloud container clusters describe --project "${GCP_TARGET_PROJECT}" \
                                     --region="${GCP_K8S_TARGET_CLUSTER_REGION}" \
                                     "${GCP_K8S_TARGET_CLUSTER_NAME}" > /dev/null
fi

MACHINE_TYPE="$(readUserInput "Enter new machine type: ")"
if [[ -z "${MACHINE_TYPE}" ]]; then
  log "MACHINE_TYPE is not set and is required. Exiting"
  exit 1
fi

AVAILABLE_POOLS="$(gcloud container node-pools list --project="${GCP_TARGET_PROJECT}" --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" --region="${GCP_K8S_TARGET_CLUSTER_REGION}" --format="json(name)"| jq -r '.[].name' | tr '\n' ' ')"
POOLS_TO_UPDATE_INPUT="$(readUserInput "Enter the node pools(${AVAILABLE_POOLS}) to update (space-separated): ")"
if [[ -z "${POOLS_TO_UPDATE_INPUT}" ]]; then
  log "POOLS_TO_UPDATE_INPUT is not set and is required. Exiting"
  exit 1
else
  IFS=', ' read -r -a POOLS_TO_UPDATE <<< "${POOLS_TO_UPDATE_INPUT}"
  MACHINE_FAMILY="${MACHINE_TYPE%%-*}"
  for pool in "${POOLS_TO_UPDATE[@]}"; do
    POOL_JSON="$(gcloud container node-pools describe "${pool}" --project="${GCP_TARGET_PROJECT}" --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" --region="${GCP_K8S_TARGET_CLUSTER_REGION}" --format="json")"
    POOL_LOCATIONS=($(echo "${POOL_JSON}" | jq -r '.locations[]'))
    for location in "${POOL_LOCATIONS[@]}"; do
      gcloud compute machine-types describe "${MACHINE_TYPE}" --project="${GCP_TARGET_PROJECT}" --zone="${location}" > /dev/null
    done

    POOL_DISK_TYPE="$(echo "${POOL_JSON}" | jq -r '.config.diskType // ""')"
    if [[ " ${HYPERDISK_ONLY_FAMILIES[*]} " == *" ${MACHINE_FAMILY} "* && "${POOL_DISK_TYPE}" == pd-* ]]; then
      log "Machine type ${MACHINE_TYPE} (${MACHINE_FAMILY}) only supports Hyperdisk but pool ${pool} uses ${POOL_DISK_TYPE}. Moving between Persistent Disk and Hyperdisk machine families is not supported by this script; no changes were made. Exiting"
      exit 1
    fi

    POOL_PLACEMENT_TYPE="$(echo "${POOL_JSON}" | jq -r '.placementPolicy.type // ""')"
    if [[ "${POOL_PLACEMENT_TYPE}" == "COMPACT" && " ${COMPACT_FAMILIES[*]} " != *" ${MACHINE_FAMILY} "* ]]; then
      log "Machine type ${MACHINE_TYPE} (${MACHINE_FAMILY}) does not support the compact placement used by pool ${pool}. Choose a machine family that supports compact placement or remove the pool's placement policy first; no changes were made. Exiting"
      exit 1
    fi
  done
fi

NAMESPACES=($(kubectl get sgshardedclusters.stackgres.io -A -o jsonpath='{.items[*].metadata.namespace}'))
for namespace in "${NAMESPACES[@]}"
do
 unrouteTraffic "${namespace}"
 pauseCitus "${namespace}"
done
captureCitusPoolTotals
resizeCitusNodePools 0
for pool in "${POOLS_TO_UPDATE[@]}"
do
gcloud container node-pools update "${pool}" --project="${GCP_TARGET_PROJECT}" --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" --location="${GCP_K8S_TARGET_CLUSTER_REGION}" --machine-type="${MACHINE_TYPE}"
done
resizeCitusNodePools 1
for namespace in "${NAMESPACES[@]}"
do
 unpauseCitus "${namespace}"
 routeTraffic "${namespace}"
done
