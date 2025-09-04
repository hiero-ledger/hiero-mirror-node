#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils.sh

REPLACE_DISKS="${REPLACE_DISKS:-true}"
STACKGRES_MINIO_ROOT="${STACKGRES_MINIO_ROOT:-sgbackups.stackgres.io}"
ZFS_POOL_NAME="${ZFS_POOL_NAME:-zfspv-pool}"

function configureAndValidate() {
  GCP_PROJECT="$(readUserInput "Enter GCP Project for target: ")"
  if [[ -z "${GCP_PROJECT}" ]]; then
    log "GCP_PROJECT is not set and is required. Exiting"
    exit 1
  else
    gcloud projects describe "${GCP_PROJECT}" > /dev/null
  fi

  GCP_SNAPSHOT_PROJECT="$(readUserInput "Enter GCP Project for snapshot source: ")"
  if [[ -z "${GCP_SNAPSHOT_PROJECT}" ]]; then
      log "GCP_SNAPSHOT_PROJECT is not set and is required. Exiting"
      exit 1
    else
      gcloud projects describe "${GCP_SNAPSHOT_PROJECT}" > /dev/null
  fi

  GCP_K8S_CLUSTER_REGION="$(readUserInput "Enter target cluster region: ")"
    if [[ -z "${GCP_K8S_CLUSTER_REGION}" ]]; then
      log "GCP_K8S_CLUSTER_REGION is not set and is required. Exiting"
      exit 1
    else
      gcloud compute regions describe "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}"  > /dev/null
    fi

  GCP_K8S_CLUSTER_NAME="$(readUserInput "Enter target cluster name: ")"
  if [[ -z "${GCP_K8S_CLUSTER_NAME}" ]]; then
    log "GCP_K8S_CLUSTER_NAME is not set and is required. Exiting"
    exit 1
  else
    gcloud container clusters describe --project "${GCP_PROJECT}" \
                                       --region="${GCP_K8S_CLUSTER_REGION}" \
                                       "${GCP_K8S_CLUSTER_NAME}" > /dev/null
  fi

  log "Listing snapshots in project ${GCP_SNAPSHOT_PROJECT}"
  gcloud compute snapshots list --project "${GCP_SNAPSHOT_PROJECT}" --format="table(name, diskSizeGb, sourceDisk, description, creationTimestamp)" --filter="name~.*[0-9]{10,}$" --sort-by="~creationTimestamp"

  SNAPSHOT_ID="$(readUserInput "Enter snapshot id (the epoch suffix of the snapshot group): ")"
  if [[ -z "${SNAPSHOT_ID}" ]]; then
    log "SNAPSHOT_ID is not set and is required. Please provide an identifier that is unique across all snapshots. Exiting"
    exit 1
  else
    SNAPSHOTS_TO_RESTORE=$(gcloud compute snapshots list --project "${GCP_SNAPSHOT_PROJECT}" \
        --filter="name~.*${SNAPSHOT_ID}$" \
        --format="json(name, description)" |
        jq -r 'map(select(.description != null) | {name: .name, description: (.description|fromjson|sort_by(.volumeName))})')
    if [[ -z "${SNAPSHOTS_TO_RESTORE}" ]]; then
      log "No snapshots found for snapshot id ${SNAPSHOT_ID} in project ${GCP_SNAPSHOT_PROJECT}. Exiting"
      exit 1
    else
      log "Found snapshots to restore: ${SNAPSHOTS_TO_RESTORE}"
      doContinue
    fi
  fi

  getDiskPrefix
  log "Target disk prefix is ${DISK_PREFIX}"

  if [[ -z "${ZFS_POOL_NAME}" ]]; then
    log "Unable to find zfs pool name. set ZFS_POOL_NAME to value of zfs.paramaters.poolname in common values.yaml"
    exit 1
  fi

  ZFS_VOLUMES=$(getZFSVolumes)
  NAMESPACES=($(echo $ZFS_VOLUMES | jq -r '.[].namespace' | tr ' ' '\n' | sort -u | tr '\n' ' '))
  NODE_ID_MAP=$(echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" |
    jq -s '.[0] as $snapshots |
           .[1] as $volumes |
           $volumes | group_by(.nodeId) |
           map((.[0].nodeId) as $nodeId |
             map(.)|sort_by(.volumeName) as $pvcs |
             $pvcs |
             map(
               {
                 pvcName: .pvcName,
                 namespace: .namespace
               }
             ) as $pvcMatchData|
             {
               ($nodeId) :  {
                  pvcs: ($pvcs),
                  snapshot: ($snapshots | map(select(.description|contains($pvcMatchData))))
               }
             }
          )|add')

  UNIQUE_NODE_IDS=($(echo "${NODE_ID_MAP}" | jq -r 'keys[]'))
  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local diskName="${DISK_PREFIX}-${nodeId}-zfs"
    local diskZone="$(echo "${nodeId}" | cut -d '-' -f 2-4)"

    if ! gcloud compute disks describe "${diskName}" --project "${GCP_PROJECT}" --zone "${diskZone}"> /dev/null; then
      log "Disk ${diskName} does not exist in project ${GCP_PROJECT} Please confirm the input for disk prefix. Exiting"
      exit 1
    fi
    local nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    HAS_VALID_SNAPSHOT=$(echo "${nodeInfo}" | jq -r '.snapshot|length == 1')
    if [[ "${HAS_VALID_SNAPSHOT}" == "false" ]]; then
      log "Unable to find valid snapshot for node id ${nodeId} in snapshot id ${SNAPSHOT_ID}.
                  Please verify snapshots contain same namespace, pvc name and postgres version.
                  ${nodeInfo}"
      exit 1
    else
      log "Snapshot contains all pvcs for node ${nodeId}"
    fi
  done
}

function prepareDiskReplacement() {
  for namespace in "${NAMESPACES[@]}"; do
    unrouteTraffic "${namespace}"
    kubectl delete sgshardedbackups.stackgres.io -n "${namespace}" --all
    pauseCitus "${namespace}" "true"
  done

  # Spin down existing citus node pools
  resizeCitusNodePools 0
}

function renameZfsVolumes() {
  log "Waiting for zfs pods to be ready"
  kubectl_common wait --for=condition=Ready pod -l 'component=openebs-zfs-node' --timeout=-1s

  local zfsNodePods=$(kubectl get pods -A -o wide -o json -l 'component=openebs-zfs-node' | jq -r '.items|map({node: (.spec.nodeName), podName: (.metadata.name)})')
  local zfsNodes=$(kubectl get zfsnodes.zfs.openebs.io -A -o json | jq -r '.items|map({nodeId: .metadata.name, node: .metadata.ownerReferences[0].name})')
  local nodeIdToPodMap=$(echo -e "${zfsNodePods}\n${zfsNodes}" |
    jq -s '.[0] as $zfsPods |
           .[1] |
           map(.node as $nodeName |
               {
                 (.nodeId) :  ($zfsPods[] | select(.node == $nodeName).podName)
               }
           )|
           add')

  log "Renaming zfs datasets"
  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local podInfo=$(echo "${nodeIdToPodMap}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local nodeData=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local pvcsCount=$(echo "${nodeData}" | jq -r '.pvcs|length')
    for pvcIndex in $(seq 0 $((pvcsCount - 1))); do
      local pvcVolumeName=$(echo "${nodeData}" | jq -r --arg PVC_INDEX "${pvcIndex}" '.pvcs[$PVC_INDEX|tonumber].volumeName')
      local snapshotPvcVolumeName=$(echo "${nodeData}" | jq -r --arg PVC_INDEX "${pvcIndex}" '.snapshot[0].description[$PVC_INDEX|tonumber].volumeName')

      if [[ "${pvcVolumeName}" != "${snapshotPvcVolumeName}" ]]; then
        log "Renaming snapshot pvc ${ZFS_POOL_NAME}/${snapshotPvcVolumeName} to ${ZFS_POOL_NAME}/${pvcVolumeName}"
        kubectl_common exec "${podInfo}" -c openebs-zfs-plugin -- zfs rename "${ZFS_POOL_NAME}/${snapshotPvcVolumeName}" "${ZFS_POOL_NAME}/${pvcVolumeName}"
      else
        log "Snapshot pvc ${ZFS_POOL_NAME}/${snapshotPvcVolumeName} already matches pvc ${ZFS_POOL_NAME}/${pvcVolumeName}"
      fi
    done

    local zfsSnapshots="$(kubectl_common exec "${podInfo}" -c openebs-zfs-plugin -- bash -c 'zfs list -H -o name -t snapshot')"

    if [[ -z "${zfsSnapshots}" ]]; then
      log "No snapshots found for pool ${ZFS_POOL_NAME} and node ${nodeId}"
    else
      log "Deleting snapshots ${zfsSnapshots}"
      kubectl_common exec "${podInfo}" -c openebs-zfs-plugin -- bash -c "echo \"${zfsSnapshots}\" | xargs -n1 zfs destroy"
    fi
   kubectl_common exec "${podInfo}" -c openebs-zfs-plugin -- zfs list -t filesystem,snapshot
  done
  log "ZFS datasets renamed"
}

function replaceDisks() {
  log "Will delete disks ${DISK_PREFIX}-(${UNIQUE_NODE_IDS[*]})-zfs in project ${GCP_PROJECT}"
  doContinue

  prepareDiskReplacement

  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local diskName="${DISK_PREFIX}-${nodeId}-zfs"
    local diskZone=$(echo "${nodeId}" | cut -d '-' -f 2-4)
    local snapshotName=$(echo "${nodeInfo}" | jq -r '.snapshot[0].name')
    local snapshotFullName="projects/${GCP_SNAPSHOT_PROJECT}/global/snapshots/${snapshotName}"
    log "Recreating disk ${diskName} in ${GCP_PROJECT} with snapshot ${snapshotName}"
    gcloud compute disks delete "${diskName}" --project "${GCP_PROJECT}" --zone "${diskZone}" --quiet
    watchInBackground "$$" gcloud compute disks create "${diskName}" --project "${GCP_PROJECT}" --zone "${diskZone}" --source-snapshot "${snapshotFullName}" --type=pd-balanced --quiet &
  done

  log "Waiting for disks to be created"
  wait

  resizeCitusNodePools 1
  renameZfsVolumes
}

function cleanupBackupStorage() {
  local namespace="${1}"
  local shardedClusterName="${2}"
  local minioPod=$(kubectl_common get pods -l 'app.kubernetes.io/name=minio' -o json | jq -r '.items[0].metadata.name')
  if [[ "${minioPod}" == "null" ]]; then
    echo "Minio pod not found. Skipping cleanup"
  else
    local backups=$(kubectl get sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" -o json | jq -r '.spec.configurations.backups')
    if [[ "${backups}" == "null" ]]; then
      echo "No backup configuration found for sharded cluster ${shardedClusterName} in namespace ${namespace}. Skipping cleanup"
      return
    fi

    kubectl patch sgshardedclusters.stackgres.io "${shardedClusterName}" -n "${namespace}" --type='json' -p '[{"op": "remove", "path": "/spec/configurations/backups"}]';

    local minioDataPath=$(kubectl_common exec "${minioPod}" -- sh -c 'echo $MINIO_DATA_DIR')
    local backupStorages=($(echo "${backups}" | jq -r '.[].sgObjectStorage'))
    for backupStorage in "${backupStorages[@]}"; do
      local minioBucket=$(kubectl get sgObjectStorage.stackgres.io -n "${namespace}" "${backupStorage}" -o json | jq -r '.spec.s3Compatible.bucket')
      local pathToDelete="${minioDataPath}/${minioBucket}/${STACKGRES_MINIO_ROOT}/${namespace}"
      echo "Cleaning up wal files in minio bucket ${minioBucket}. Will delete all files at path ${pathToDelete}"
      doContinue
      kubectl_common exec "${minioPod}" -- mc rm --recursive --force "${pathToDelete}"
    done
  fi
}

function configureShardedClusterResource() {
  local pvcsInNamespace="${1}"
  local shardedClusterName="${2}"
  local namespace="${3}"

  local coordinatorPvcSize=$(echo "${pvcsInNamespace}" |
    jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator))|
                                  map(.snapshotPvcSize)|first')
  local workerPvcOverrides=$(echo "${pvcsInNamespace}" |
    jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator == false))|
                                sort_by(.citusCluster.citusGroup, .citusCluster.podName)|
                                to_entries|
                                map({index: .key, pods: {persistentVolume: {size: .value.snapshotPvcSize}}})')

  log "Patching sharded cluster ${shardedClusterName} in namespace ${namespace}"
  local shardedCluster=$(kubectl get sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" -o json)
  local shardedClusterPatch=$(echo "${shardedCluster} ${workerPvcOverrides}" |
     jq -s --arg COORDINATOR_PVC_SIZE "${coordinatorPvcSize}" \
       '.[0] as $cluster |
        .[1] as $overrides |
        $cluster |
        if(.spec.configurations | has("backups"))
          then .spec.configurations.backups | map(del(.paths))
        else
          []
        end |
        {
          spec: {
            configurations: {
              backups: (.)
            },
            coordinator: {
              pods: {
                persistentVolume: {
                  size: $COORDINATOR_PVC_SIZE
                }
              }
            },
            shards: {
              overrides: $overrides
            }
          }
        }')
  cleanupBackupStorage "${namespace}" "${shardedClusterName}"
  kubectl patch sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" --type merge -p "${shardedClusterPatch}"
  log "
  **** IMPORTANT ****
  Please configure your helm values.yaml for namespace ${namespace} to have the following values:

  stackgres.coordinator.pods.persistentVolume.size=${coordinatorPvcSize}

  stackgres.worker.overrides=${workerPvcOverrides}
  "
  log "Continue to acknowledge config change is saved (do not need to apply the config change yet)"
  doContinue
}

function patchCitusClusters() {
  log "Patching Citus clusters in namespaces ${NAMESPACES[*]}"
  local pvcsByNamespace=$(echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" |
    jq -s '(.[0] | map(.description)| flatten) as $snapshots|
            .[1] as $volumes|
            $volumes | group_by(.namespace)|
            map((.[0].namespace) as $namespace |
              {
                ($namespace):
                   map(. as $pvc |
                       $snapshots[]|
                       select(.pvcName == $pvc.pvcName and .namespace == $pvc.namespace) as $snapshotPvc|
                    $pvc + {snapshotPvcSize: $snapshotPvc.pvcSize, snapshotPrimary: $snapshotPvc.primary})
              }
            )|
            add')
  for namespace in "${NAMESPACES[@]}"; do
    local pvcsInNamespace=$(echo "${pvcsByNamespace}" | jq -r --arg namespace "${namespace}" '.[$namespace]')
    local shardedClusterName=$(echo "${pvcsInNamespace}" | jq -r '.[0].citusCluster.shardedClusterName')

    configureShardedClusterResource "${pvcsInNamespace}" "${shardedClusterName}" "${namespace}"
    unpauseCitus "${namespace}" "true"
    updateStackgresCreds "${shardedClusterName}" "${namespace}"
    routeTraffic "${namespace}"
  done
}

configureAndValidate

if [[ "${REPLACE_DISKS}" == "true" ]]; then
  replaceDisks
else
  log "REPLACE_DISKS is set to false. Skipping disk replacement"
fi

patchCitusClusters
