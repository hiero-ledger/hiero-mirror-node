#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

function promptGcpProject() {
  local projectPurpose="${1:-source}"
  local gcpProject

  gcpProject="$(readUserInput "Enter GCP Project for ${projectPurpose}: ")"

  if [[ -z "${gcpProject}" ]]; then
    log "gcpProject is not set and is required. Exiting"
    exit 1
  fi
  gcloud projects describe "${gcpProject}" > /dev/null
  echo "${gcpProject}"
}

function promptGcpClusterRegion() {

  local region
  region="$(readUserInput "Enter cluster region for target: ")"

  if [[ -z "${region}" ]]; then
    log "region is not set and is required. Exiting"
    exit 1
  fi

  gcloud compute regions describe "${region}" --project "${GCP_TARGET_PROJECT}" > /dev/null || {
    log "Region '${region}' does not exist in project. Exiting"
    exit 1
  }

  echo "${region}"
}

function promptGcpClusterName() {
  if [[ -z "${GCP_TARGET_PROJECT}" || -z "${GCP_K8S_TARGET_CLUSTER_REGION}" ]]; then
    log "Both GCP_TARGET_PROJECT and GCP_K8S_TARGET_CLUSTER_REGION must be set. Exiting"
    exit 1
  fi

  local clusterName
  clusterName="$(readUserInput "Enter cluster name for target: ")"

  if [[ -z "${clusterName}" ]]; then
    log "cluster name is not set and is required. Exiting"
    exit 1
  fi

  gcloud container clusters describe \
    --project "${GCP_TARGET_PROJECT}" \
    --region "${GCP_K8S_TARGET_CLUSTER_REGION}" \
    "${clusterName}" > /dev/null || {
      log "Cluster '${clusterName}' not found. Exiting"
      exit 1
    }

  echo "${clusterName}"
}

function promptSnapshotId() {
  if [[ -z "${GCP_SNAPSHOT_PROJECT}" ]]; then
      log "GCP_SNAPSHOT_PROJECT must be set. Exiting"
      exit 1
  fi
  log "Listing snapshots in project"
  gcloud compute snapshots list \
    --project "${GCP_SNAPSHOT_PROJECT}" \
    --format="table(name, diskSizeGb, sourceDisk, description, creationTimestamp)" \
    --filter="name~.*[0-9]{10,}$" \
    --sort-by="~creationTimestamp"

  local snapshotId
  snapshotId="$(readUserInput "Enter snapshot id (the epoch suffix of the snapshot group): ")"

  if [[ -z "${snapshotId}" ]]; then
    log "snapshotId is not set and is required. Please provide an identifier that is unique across all snapshots."
    exit 1
  fi
  echo "${snapshotId}"
}
