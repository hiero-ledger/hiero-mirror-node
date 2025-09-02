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
  local purpose="$1"
  local gcpProject="$2"

  if [[ -z "${gcpProject}" ]]; then
    gcpProject="$(promptGcpProject "${1}")"
  fi

  local region
  region="$(readUserInput "Enter cluster region for ${purpose}: ")"

  if [[ -z "${region}" ]]; then
    log "region is not set and is required. Exiting"
    exit 1
  fi

  gcloud compute regions describe "${region}" --project "${gcpProject}" > /dev/null || {
    log "Region '${region}' does not exist in project '${gcpProject}'. Exiting"
    exit 1
  }

  echo "${region}"
}

function promptGcpClusterName() {
  local project="$1"
  local region="$2"
  local purpose="$3"

  if [[ -z "${project}" || -z "${region}" ]]; then
    log "Both GCP project and region must be provided. Exiting"
    exit 1
  fi

  local clusterName
  clusterName="$(readUserInput "Enter cluster name for ${purpose}: ")"

  if [[ -z "${clusterName}" ]]; then
    log "cluster name is not set and is required. Exiting"
    exit 1
  fi

  gcloud container clusters describe \
    --project "${project}" \
    --region "${region}" \
    "${clusterName}" > /dev/null || {
      log "Cluster '${clusterName}' not found in project '${project}', region '${region}'. Exiting"
      exit 1
    }

  echo "${clusterName}"
}

function promptSnapshotId() {
  local gcpProject="$1"

  if [[ -z "${gcpProject}" ]]; then
      log "GCP project must be provided as the first argument. Exiting"
      exit 1
  fi
  log "Listing snapshots in project ${gcpProject}"
  gcloud compute snapshots list \
    --project "${gcpProject}" \
    --format="table(name, diskSizeGb, sourceDisk, description, creationTimestamp)" \
    --filter="name~.*[0-9]{10,}$" \
    --sort-by="~creationTimestamp"

  local snapshotId
  snapshotId="$(readUserInput "Enter snapshot id (the epoch suffix of the snapshot group): ")"

  if [[ -z "${snapshotId}" ]]; then
    log "SNAPSHOT_ID is not set and is required. Please provide an identifier that is unique across all snapshots. Exiting"
    exit 1
  fi
  echo "${snapshotId}"
}
