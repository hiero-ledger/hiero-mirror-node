#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils.sh

GCP_PROJECT="$(readUserInput "Enter GCP Project for target: ")"
if [[ -z "${GCP_PROJECT}" ]]; then
  log "GCP_PROJECT is not set and is required. Exiting"
  exit 1
else
  gcloud projects describe "${GCP_PROJECT}" > /dev/null
fi

getDiskPrefix
log "Finding disks with prefix ${DISK_PREFIX}"
DISKS_TO_SNAPSHOT=$(gcloud compute disks list --project "${GCP_PROJECT}"  --filter="name~${DISK_PREFIX}.*-zfs" --format="json(name, sizeGb, users, zone)")
if [[ "${DISKS_TO_SNAPSHOT}" == "[]" ]]; then
  log "No disks found for prefix. Exiting"
  exit 1
fi

DISK_NAMES=($(echo $DISKS_TO_SNAPSHOT | jq -r '.[].name'))
log "Will snapshot disks ${DISK_NAMES[*]}"
doContinue

ZFS_VOLUMES=$(getZFSVolumes)

NAMESPACES=($(echo $ZFS_VOLUMES | jq -r '.[].namespace' | tr ' ' '\n' | sort -u | tr '\n' ' '))

for namespace in "${NAMESPACES[@]}"
do
   unrouteTraffic "${namespace}"
   pauseCitus "${namespace}"
done

EPOCH_SECONDS=$(date +%s)
CITUS_CLUSTERS=$(getCitusClusters)
for diskName in "${DISK_NAMES[@]}"
do
  DISK_NODE_ID=${diskName#"$DISK_PREFIX"-}
  DISK_NODE_ID=${DISK_NODE_ID%"-zfs"}
  NODE_VOLUMES=$(echo "${ZFS_VOLUMES}" |
                 jq -r --arg NODE_ID "${DISK_NODE_ID}" 'map(select(.nodeId == $NODE_ID))')
  SNAPSHOT_DESCRIPTION=$(echo -e "${CITUS_CLUSTERS}\n${NODE_VOLUMES}" |
                        jq -r -s --arg NODE_ID "${DISK_NODE_ID}" \
                         '.[0] as $clusters |
                          .[1] as $volumes |
                          $volumes |
                          map(. as $volume|
                              $clusters[]|
                              select(.pvcName == $volume.pvcName and .namespace == $volume.namespace)|
                              {
                                pvcName: $volume.pvcName,
                                volumeName: $volume.volumeName,
                                pvcSize: $volume.pvcSize,
                                namespace: $volume.namespace,
                                primary: .primary,
                                pgVersion: .pgVersion
                              })')
  SNAPSHOT_NAME="${diskName}-${EPOCH_SECONDS}"
  SNAPSHOT_REGION=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-3)
  DISK_ZONE=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-4)
  log "Creating snapshot ${SNAPSHOT_NAME} for ${diskName} with ${SNAPSHOT_DESCRIPTION} in ${SNAPSHOT_REGION}"
  watchInBackground "$$" gcloud compute snapshots create "${SNAPSHOT_NAME}" \
  --project="${GCP_PROJECT}"  \
  --source-disk="${diskName}" \
  --source-disk-zone="${DISK_ZONE}" \
  --storage-location="${SNAPSHOT_REGION}" \
  --description="${SNAPSHOT_DESCRIPTION}" &
done
log "Waiting for snapshots to finish"
wait
log "Snapshots finished"

for namespace in "${NAMESPACES[@]}"
do
  unpauseCitus "${namespace}"
  routeTraffic "${namespace}"
done
