#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euox pipefail

source ./utils.sh

function waitUntilOutOfRecovery() {
  local namespace="$1" pod="$2"

  log "Waiting for ${pod} to exit recovery"

  while true; do
    local res
    res="$(kubectl exec -n "${namespace}" "${pod}" -c postgres-util -- \
      psql -U mirror_node -d mirror_node -Atc "select pg_is_in_recovery();" 2>/dev/null || true)"

    case "${res}" in
      f)
        log "${pod} is out of recovery"
        return 0
        ;;
      t)
        log "${pod} still in recovery"
        ;;
      *)
        log "Query failed or unexpected output: '${res:-<empty>}' (retrying)"
        ;;
    esac

    sleep 60
  done
}

function setupZfsVolumeForRecovery() {
  local namespace="${1}"  pvcName="${2}" backupLabel="${3}"

  if [[ -z "${pvcName}" ]]; then
    log "pvcName is required"
    return 1
  fi
  local jobName="${pvcName}-recovery"

  log "Setting up volume for ${pvcName}"
  cat <<EOF | kubectl apply -n "${namespace}" -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: ${jobName}
spec:
  backoffLimit: 0
  template:
    metadata:
      labels:
        job: ${jobName}
    spec:
      containers:
      - name: cleaner
        image: debian:bookworm-slim
        env:
        - name: BACKUP_LABEL_B64
          value: "${backupLabel}"
        command:
        - /bin/bash
        - -ec
        - |
          set -euox pipefail
          echo "Removing snapshot marker files..."
          rm -f /pgdata/data/.already_restored_from_volume_snapshot_*

          echo "Removing signal files"
          rm -f /pgdata/data/*.signal

          echo "Cleaning pg_wal directory..."
          rm -rf /pgdata/data/pg_wal/*

          touch /pgdata/data/recovery.signal
          chown 999:999 /pgdata/data/recovery.signal

          if [[ -n "\${BACKUP_LABEL_B64:-}" ]]; then
            echo "Writing backup_label from provided base64..."
            # remove any existing file first
            rm -f /pgdata/data/backup_label
            printf "%s" "\${BACKUP_LABEL_B64}" | base64 -d > /pgdata/data/backup_label
            chown 999:999 /pgdata/data/backup_label
          else
            echo "No backup label provided"
            exit 1
          fi

          ls -al /pgdata/data
        volumeMounts:
        - mountPath: /pgdata
          name: pgdata
      nodeSelector:
        csi-type: zfs
      restartPolicy: Never
      tolerations:
      - effect: NoSchedule
        key: zfs
        operator: Equal
        value: "true"
      volumes:
      - name: pgdata
        persistentVolumeClaim:
          claimName: ${pvcName}
EOF
  kubectl wait  -n "${namespace}" --for=condition=complete "job/${jobName}" --timeout=-1s
  kubectl logs -n "${namespace}" -l "job=${jobName}" --tail=-1
  kubectl delete  -n "${namespace}" job "${jobName}"
  kubectl wait --for=delete job -n "${namespace}" "${jobName}" --timeout=-1s
}

function createSnapshot() {
  local gcpProject="$1" diskName="$2" epochSeconds="$3" snapshotDescription="$4" \
  snapshotName="$5" snapshotRegion="$6" diskZone="$7"

  log "Creating snapshot ${snapshotName} for ${diskName} with ${snapshotDescription} in ${snapshotRegion}"
  watchInBackground "$$" gcloud compute snapshots create "${snapshotName}" \
  --project="${gcpProject}" \
  --source-disk="${diskName}" \
  --source-disk-zone="${diskZone}" \
  --storage-location="${snapshotRegion}" \
  --description="${snapshotDescription}" &
}

function getMinioDiskName() {
  local minioPvNames
  mapfile -t minioPvNames < <(
  kubectl_common get pvc -l app.kubernetes.io/component=minio \
    -o jsonpath='{range .items[*]}{.spec.volumeName}{"\n"}{end}'
  )

  if ((${#minioPvNames[@]} != 1)); then
    log "Expected exactly 1 pv, but found ${#minioPvNames[@]}"
    exit 1
  fi
  echo "${minioPvNames[0]}"
}

function getZoneFromPv() {
  local pv="$1"
  if [[ -z "$pv" ]]; then
    log "pv name required"
    return 1
  fi

  local pv_json
  if ! pv_json="$(kubectl get pv "$pv" -o json 2>/dev/null)"; then
    log "failed to fetch PV $pv"
    return 1
  fi

  local zone
  zone="$(jq -r '
  (.spec.csi.volumeHandle // "") as $vh
  | if ($vh | test("/zones/")) then
      ($vh | capture("/zones/(?<z>[^/]+)/disks/").z)
    else empty end
  ' <<<"$pv_json")"

  if [[ -z "$zone" || "$zone" == "null" ]]; then
    zone="$(jq -r '
      try
        .spec.nodeAffinity.required.nodeSelectorTerms[]
        .matchExpressions[] | select(.key=="topology.gke.io/zone")
        | .values[0]
      catch "" end
    ' <<<"$pv_json")"
  fi

  if [[ -z "$zone" || "$zone" == "null" ]]; then
    log "could not determine zone for PV $pv"
    return 1
  fi

  printf '%s\n' "$zone"
}

#TODO use snapshot source project instead of gcpProject
function snapshotMinioDisk() {
  if [[  "${KEEP_SNAPSHOTS}" != "true" ]]; then
    log "KEEP_SNAPSHOTS is set to false. Skipping MinIO disk snapshot"
    return
  fi

  local gcpProject="$1" epochSeconds="$2"
  local diskName snapshotDescription diskRegion diskZone

  diskName="$(getMinioDiskName)"
  diskZone="$(getZoneFromPv "${diskName}")"
  snapshotName="${MINIO_SNAPSHOT_PREFIX}-${epochSeconds}"
  snapshotDescription="[{\"purpose\": \"${MINIO_SNAPSHOT_PREFIX}\"}]"
  diskRegion="${diskZone%-*}"

  createSnapshot "${gcpProject}" \
  "${diskName}" \
  "${epochSeconds}" \
  "${snapshotDescription}" \
  "${snapshotName}" \
  "${diskRegion}" \
  "${diskZone}"
}

function getCitusDiskNames() {
  local project="$1"
  local prefix="$2"
  gcloud compute disks list --project "${project}" \
  --filter="name~${prefix}.*-zfs" \
  --format="json(name, sizeGb, users, zone)"
}

function snapshotCitusDisks() {
  local pauseCluster="${1:-true}"
  local diskPrefix disksToSnapshot diskNames

  if [[ -z "${GCP_SNAPSHOT_PROJECT}" ]]; then
    GCP_SNAPSHOT_PROJECT=$(promptGcpProject "source")
  fi
  diskPrefix="$(getDiskPrefix)"
  disksToSnapshot=$(getCitusDiskNames "${GCP_SNAPSHOT_PROJECT}" "${diskPrefix}")

  if [[ "${disksToSnapshot}" == "[]" ]]; then
    log "No disks found for prefix. Exiting"
    exit 1
  fi

  mapfile -t diskNames < <(echo "${disksToSnapshot}" | jq -r '.[].name')
  log "Will snapshot citus disks ${diskNames[*]}"
  doContinue

  local zfsVolumes
  zfsVolumes=$(getZFSVolumes)

  mapfile -t CITUS_NAMESPACES < <(echo "${zfsVolumes}" | jq -r '.[].namespace' | sort -u)

  pauseClustersIfNeeded "${pauseCluster}"

  local epochSeconds citusClusters
  epochSeconds=$(date +%s)
  citusClusters=$(getCitusClusters)

  snapshotMinioDisk "${GCP_SNAPSHOT_PROJECT}" "${epochSeconds}"

  for diskName in "${diskNames[@]}"; do
    local diskNodeId nodeVolumes snapshotDescription snapshotName snapshotRegion diskZone
    diskNodeId="${diskName#"$diskPrefix"-}"
    diskNodeId="${diskNodeId%"-zfs"}"
    snapshotName="${diskName}-${epochSeconds}"
    snapshotRegion=$(echo "${diskNodeId}" | cut -d '-' -f 2-3)
    diskZone=$(echo "${diskNodeId}" | cut -d '-' -f 2-4)
    nodeVolumes=$(echo "${zfsVolumes}" | jq -r --arg diskNodeId "${diskNodeId}" 'map(select(.nodeId == $diskNodeId))')
    snapshotDescription=$(echo -e "${citusClusters}\n${nodeVolumes}" |
      jq -r -s '
        .[0] as $clusters |
        .[1] as $volumes |
        $volumes |
        map(. as $volume |
            $clusters[] |
            select(.pvcName == $volume.pvcName and .namespace == $volume.namespace) |
            {
              pvcName: $volume.pvcName,
              volumeName: $volume.volumeName,
              pvcSize: $volume.pvcSize,
              namespace: $volume.namespace,
              primary: .primary,
              pgVersion: .pgVersion
            })')

    createSnapshot "${GCP_SNAPSHOT_PROJECT}" \
    "${diskName}" \
    "${epochSeconds}" \
    "${snapshotDescription}" \
    "${snapshotName}" \
    "${snapshotRegion}" \
    "${diskZone}"
  done

  log "Waiting for snapshots with id ${epochSeconds} to finish"
  wait
  log "Snapshots finished for id ${epochSeconds}"

  resumeClustersIfNeeded "${pauseCluster}"
  echo "${epochSeconds}"
}

function getSnapshotsById() {
  if [[ -z "${GCP_SNAPSHOT_PROJECT}" ]]; then
    GCP_SNAPSHOT_PROJECT="$(promptGcpProject "snapshot source")"
  fi

  if [[ -z "${SNAPSHOT_ID}" ]]; then
    SNAPSHOT_ID="$(promptSnapshotId "${GCP_SNAPSHOT_PROJECT}")"
  fi

  local snapshots
  snapshots=$(gcloud compute snapshots list \
  --project "${GCP_SNAPSHOT_PROJECT}" \
  --filter="name~.*${SNAPSHOT_ID}$" \
  --format="json(name, description)" |
  jq -r 'map(select(.description != null) | {
    name: .name,
    description: (.description | fromjson | sort_by(.volumeName))
  })')

  if [[ -z "${snapshots}" || "${snapshots}" == "null" ]]; then
    log "No snapshots found for snapshot id ${SNAPSHOT_ID} in project ${GCP_SNAPSHOT_PROJECT}. Exiting"
    exit 1
  fi

  echo "${snapshots}"
}

function getNodeIdToPvcSnapshotsMap() {
  if [[ -z "${SNAPSHOTS_TO_RESTORE}" ]]; then
    SNAPSHOTS_TO_RESTORE="$(getSnapshotsById)"
  fi

  if [[ -z "${ZFS_VOLUMES}" ]]; then
    ZFS_VOLUMES="$(getZFSVolumes)"
  fi

  echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" \
  | jq -s --arg swapWithPrimary "${KEEP_SNAPSHOTS}" '
      .[0] as $snapshots |
      .[1] as $volumes   |
      $volumes
      | group_by(.nodeId)
      | map(
          (.[0].nodeId) as $nodeId
          | (map(.) | sort_by(.volumeName)) as $pvcs
          | ($pvcs | map({pvcName, namespace})) as $pvcMatchData
          | ($snapshots
              | map(
                  select(
                    (.description | any(
                      . as $d
                      | any($pvcMatchData[]; (.pvcName == $d.pvcName and .namespace == $d.namespace))
                    ))
                  )
                )
            ) as $nodeSnaps
          | ($pvcs[0].citusCluster.clusterName // null) as $clusterName
          | ($nodeSnaps[0]) as $currSnap
          | (($currSnap.description // []) | any(.primary == false)) as $hasNonPrimary
          | (($swapWithPrimary // "" | ascii_downcase) == "true") as $wantSwap
          | ($wantSwap and $hasNonPrimary and ($clusterName != null)) as $shouldSwap
          | (
              if $shouldSwap then
                (
                  $snapshots
                  | map(
                      select(
                        (.description | any(.primary == true and (.pvcName | contains($clusterName))))
                      )
                    )
                  | first
                ) as $primarySnap
                |
                if $primarySnap != null then
                  # Pick the primary description entry for this cluster
                  ($primarySnap.description
                    | map(select(.primary == true and (.pvcName | contains($clusterName))))
                    | first
                  ) as $primDesc
                  |
                  {
                    ($nodeId): {
                      pvcs: $pvcs,
                      snapshot: [
                        {
                          name: $primarySnap.name,
                          description: [
                            $pvcs[] |
                            {
                              pvcName: .pvcName,
                              namespace: $primDesc.namespace,
                              volumeName: $primDesc.volumeName,
                              pvcSize: $primDesc.pvcSize,
                              primary: true,
                              pgVersion: $primDesc.pgVersion
                            }
                          ]
                        }
                      ]
                    }
                  }
                else
                  { ($nodeId): { pvcs: $pvcs, snapshot: $nodeSnaps } }
                end
              else
                { ($nodeId): { pvcs: $pvcs, snapshot: $nodeSnaps } }
              end
            )
        )
      | add
    '
}

function renameDataset() {
  local pod="$1" oldName="$2" newName="$3"
  if [[ "${oldName}" == "${newName}" ]]; then
    log "No rename needed: '${oldName}' already matches target name"
    return 0
  fi

  if kubectl_common exec "${pod}" -c openebs-zfs-plugin -- \
       zfs list -H -o name "${newName}" >/dev/null 2>&1; then
    log "Target dataset '${newName}' already exists; skipping rename from '${oldName}'."
    return 0
  fi

  if ! kubectl_common exec "${pod}" -c openebs-zfs-plugin -- \
        zfs list -H -o name "${oldName}" >/dev/null 2>&1; then
    log "Source dataset '${oldName}' not found; nothing to rename."
    return 1
  fi

  log "Renaming dataset '${oldName}' -> '${newName}'"
  if kubectl_common exec "${pod}" -c openebs-zfs-plugin -- \
       zfs rename "${oldName}" "${newName}"; then
    log "Rename complete: '${newName}'"
    return 0
  else
    log "ERROR: Failed to rename '${oldName}' -> '${newName}'"
    return 1
  fi
}

function rollbackToSnapshot() {
  local pod="$1" sourceVolumeName="$2" targetVolumeName="$3" pvcNamespace="$4" pvcName="$5"
  local snapshotHandle backupLabel

  read -r snapshotHandle backupLabel < <(
    echo "${SOURCE_LATEST_BACKUPS}" | jq -r --arg vh "${sourceVolumeName}" '
      [to_entries[].value[] | select(.volumeHandle == $vh)][0]
      | [ .snapshotHandle, .backupLabel ]
      | @tsv
    '
  )

  snapshotHandle="${snapshotHandle/${sourceVolumeName}/${targetVolumeName}}"
  log "Rolling back ${targetVolumeName} to snapshot ${snapshotHandle}"
  kubectl_common exec "${pod}" -c openebs-zfs-plugin -- zfs rollback -r "${ZFS_POOL_NAME}/${snapshotHandle}"
  setupZfsVolumeForRecovery "${pvcNamespace}" "${pvcName}" "${backupLabel}"
}

function renameZfsPvcVolumes() {
  local pod="$1" nodeData="$2" nodeId="$3"
  local pvcCount
  pvcCount=$(echo "${nodeData}" | jq -r '.pvcs | length')

  for i in $(seq 0 $((pvcCount - 1))); do
    local currentVolumeName sourceVolumeName pvcNamespace isPrimary pvcName
    currentVolumeName=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.pvcs[$i].volumeName')
    sourceVolumeName=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.snapshot[0].description[$i].volumeName')
    pvcNamespace=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.pvcs[$i].namespace')
    pvcName=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.snapshot[0].description[$i].pvcName')

    renameDataset "${pod}" "${ZFS_POOL_NAME}/${sourceVolumeName}" "${ZFS_POOL_NAME}/${currentVolumeName}"
    renameZfsSnapshots "${pod}" "${nodeData}"
    if [[ -n "${SOURCE_LATEST_BACKUPS}" && "${KEEP_SNAPSHOTS}" == "true" ]]; then
      rollbackToSnapshot "${pod}" "${sourceVolumeName}" "${currentVolumeName}" "${pvcNamespace}" "${pvcName}"
    fi
  done
}

function cleanupBackupStorage() {
  local namespace="${1}"
  local shardedClusterName="${2}"
  local minioPod
  minioPod=$(kubectl_common get pods -l 'app.kubernetes.io/name=minio' -o json | jq -r '.items[0].metadata.name')
  if [[ "${minioPod}" == "null" ]]; then
  echo "Minio pod not found. Skipping cleanup"
  else
  local backups minioDataPath backupStorages
  backups=$(kubectl get sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" -o json | jq -r '.spec.configurations.backups')
  if [[ "${backups}" == "null" ]]; then
    echo "No backup configuration found for sharded cluster ${shardedClusterName} in namespace ${namespace}. Skipping cleanup"
    return
  fi

  kubectl patch sgshardedclusters.stackgres.io "${shardedClusterName}" -n "${namespace}" --type='json' -p '[{"op": "remove", "path": "/spec/configurations/backups"}]';

  minioDataPath=$(kubectl_common exec "${minioPod}" -- sh -c 'echo $MINIO_DATA_DIR')
  mapfile -t backupStorages < <(echo "${backups}" | jq -r '.[].sgObjectStorage')
  for backupStorage in "${backupStorages[@]}"; do
    local minioBucket pathToDelete

    minioBucket=$(kubectl get sgObjectStorage.stackgres.io -n "${namespace}" "${backupStorage}" -o json | jq -r '.spec.s3Compatible.bucket')
    pathToDelete="${minioDataPath}/${minioBucket}/${STACKGRES_MINIO_ROOT}/${namespace}"

    echo "Cleaning up wal files in minio bucket ${minioBucket}. Will delete all files at path ${pathToDelete}"
    doContinue
    kubectl_common exec "${minioPod}" -- mc rm --recursive --force "${pathToDelete}"
  done
  fi
}

function prepareCitusDisksForReplacement() {
  for namespace in "${CITUS_NAMESPACES[@]}"; do
    unrouteTraffic "${namespace}"
    pauseCitus "${namespace}" "true"
  done
}

waitForDiskNotInUse() {
  local diskName="$1" diskZone="$2"

  while true; do
    local output
    if ! output="$(gcloud compute disks describe "${diskName}" \
                    --project "${GCP_TARGET_PROJECT}" \
                    --zone "${diskZone}" \
                    --format='csv[no-heading](users,status)' 2>/dev/null)"; then
      log "Disk ${diskName} not found in ${diskZone}; assuming not in use."
      return 0
    fi

    local users status
    IFS=$',' read -r users status <<<"${output}"

    if [[ -z "${users}" && "${status}" == "READY" ]]; then
      log "Disk ${diskName} is not in use and status is READY."
      return 0
    fi

    log "Disk ${diskName} still in use (users='${users}', status='${status}'). Retrying in 5s..."
    sleep 5
  done
}

function replaceDiskFromSnapshot() {
  local diskName="$1" diskZone="$2" snapshotName="$3" diskType="${4:-pd-balanced}"
  local createPidRef="${5:-}"
  if gcloud compute disks describe "${diskName}" \
          --project "${GCP_TARGET_PROJECT}" \
          --zone "${diskZone}" \
          --format="value(name)" >/dev/null 2>&1; then
    log "Deleting existing disk ${diskName} in ${diskZone}"
    waitForDiskNotInUse "${diskName}" "${diskZone}"
    gcloud compute disks delete "${diskName}" \
      --project "${GCP_TARGET_PROJECT}" \
      --zone "${diskZone}" \
      --quiet
  else
    log "Disk ${diskName} does not exist in ${diskZone}, skipping delete"
  fi
  local snapshotFullName
  snapshotFullName="projects/${GCP_SNAPSHOT_PROJECT}/global/snapshots/${snapshotName}"

  log "Recreating disk ${diskName} in ${GCP_TARGET_PROJECT} with snapshot ${snapshotFullName} in zone ${diskZone}"
  watchInBackground "$$" gcloud compute disks create "${diskName}" --project "${GCP_TARGET_PROJECT}" --zone "${diskZone}" --source-snapshot "${snapshotFullName}" --type="${diskType}" --quiet &
  local pid=$!
  if [[ -n "${createPidRef}" ]]; then
      printf -v "${createPidRef}" '%s' "${pid}"
  fi
}

function replaceMinioDisk() {
  if [[  "${KEEP_SNAPSHOTS}" != "true" ]]; then
    log "KEEP_SNAPSHOTS is set to false. Skipping MinIO restore from snapshot"
    return
  fi

  scaleDeployment "${COMMON_NAMESPACE}" 0 "${MINIO_RESOURCE_SELECTOR}"
  local diskName diskZone snapshotName bgPid
  diskName="$(getMinioDiskName)"
  diskZone="$(getZoneFromPv "${diskName}")"
  snapshotName="${MINIO_SNAPSHOT_PREFIX}-${SNAPSHOT_ID}"
  replaceDiskFromSnapshot "${diskName}" "${diskZone}" "${snapshotName}" "pd-ssd" bgPid
  log "Waiting for MinIO disk to be created"
  wait "${bgPid}"
  scaleDeployment "${COMMON_NAMESPACE}" 1 "${MINIO_RESOURCE_SELECTOR}"
}

function renameZfsSnapshots() {
  local pod="$1" nodeData="$2"
  local snapshots
  snapshots=$(kubectl_common exec "${pod}" -c openebs-zfs-plugin -- bash -c 'zfs list -H -o name -t snapshot')
  local pvcCount
  pvcCount=$(echo "${nodeData}" | jq -r '.pvcs | length')

  while read -r snapshot; do
    local dataset snapName currentVolume
    dataset=$(cut -d@ -f1 <<< "$snapshot")
    snapName=$(cut -d@ -f2 <<< "$snapshot")
    currentVolume=$(basename "$dataset")

    for i in $(seq 0 $((pvcCount - 1))); do
      local pvcName snapshotName
      pvcName=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.pvcs[$i].volumeName')
      snapshotName=$(echo "${nodeData}" | jq -r --argjson i "${i}" '.snapshot[0].description[$i].volumeName')

      if [[ "${currentVolume}" == "${snapshotName}" ]]; then
        local newSnapshot="${ZFS_POOL_NAME}/${pvcName}@${snapName}"
        renameDataset "${pod}" "${snapshot}" "${newSnapshot}"
      fi
    done
  done <<< "${snapshots}"
}

function renameZfsVolumes() {
  waitForZfsPodsReady

  local nodeIdToPodMap uniqueNodeIds
  nodeIdToPodMap=$(buildNodeIdToPodMap)
  mapfile -t uniqueNodeIds < <(echo "${nodeIdToPodMap}" | jq -r 'keys[]')

  log "Renaming ZFS datasets for node IDs: ${uniqueNodeIds[*]}"
  for nodeId in "${uniqueNodeIds[@]}"; do
    local pod
    pod=$(echo "${nodeIdToPodMap}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
    local nodeData
    nodeData=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')

    renameZfsPvcVolumes "${pod}" "${nodeData}" "${nodeId}"
    if [[ "${KEEP_SNAPSHOTS}" == "false" ]]; then
      deleteZfsSnapshots "${pod}" "${nodeId}"
    fi

    kubectl_common exec "${pod}" -c openebs-zfs-plugin -- zfs list -t filesystem,snapshot -o creation,name
  done

  log "ZFS datasets renamed"
}

function replaceDisks() {
  prepareCitusDisksForReplacement
  if [[ "${REPLACE_DISKS}" == "true" ]]; then
    log "Will delete disks ${DISK_PREFIX}-(${UNIQUE_NODE_IDS[*]})-zfs in project ${GCP_TARGET_PROJECT}"
    doContinue
    kubectl delete sgshardedbackups.stackgres.io -n "${namespace}" --all
    resizeCitusNodePools 0
    for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
      local nodeInfo diskName diskZone snapshotName snapshotFullName

      nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
      diskName="${DISK_PREFIX}-${nodeId}-zfs"
      diskZone=$(echo "${nodeId}" | cut -d '-' -f 2-4)
      snapshotName=$(echo "${nodeInfo}" | jq -r '.snapshot[0].name')
      replaceDiskFromSnapshot "${diskName}" "${diskZone}" "${snapshotName}"
    done

    replaceMinioDisk
    log "Waiting for disks to be created"
    wait

    resizeCitusNodePools 1
  else
    log "REPLACE_DISKS is set to false. Skipping disk replacement"
  fi
  renameZfsVolumes
  patchCitusClusters
}

function configureShardedClusterResource() {
  local pvcsInNamespace="${1}" shardedClusterName="${2}" namespace="${3}"
  local coordinatorPvcSize workerPvcOverrides shardedCluster shardedClusterPatch
  coordinatorPvcSize=$(echo "${pvcsInNamespace}" |
  jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator))|
                                map(.snapshotPvcSize)|first')
  workerPvcOverrides=$(echo "${pvcsInNamespace}" |
  jq -r 'map(select(.snapshotPrimary and .citusCluster.isCoordinator == false))|
                              sort_by(.citusCluster.citusGroup, .citusCluster.podName)|
                              to_entries|
                              map({index: .key, pods: {persistentVolume: {size: .value.snapshotPvcSize}}})')
  shardedCluster=$(kubectl get sgshardedclusters.stackgres.io -n "${namespace}" "${shardedClusterName}" -o json)
  shardedClusterPatch=$(echo "${shardedCluster} ${workerPvcOverrides}" |
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
  if [[ "${KEEP_SNAPSHOTS}" == "false" ]]; then
    cleanupBackupStorage "${namespace}" "${shardedClusterName}"
  else
    log "Skipping cleanup of backup storage for sharded cluster ${shardedClusterName} in namespace ${namespace}"
  fi
  log "Patching sharded cluster ${shardedClusterName} in namespace ${namespace}"
  kubectl patch sgshardedclusters.stackgres.io -n "${namespace}" \
  "${shardedClusterName}" \
  --type merge \
  -p "${shardedClusterPatch}"
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
  log "Patching Citus clusters in namespaces ${CITUS_NAMESPACES[*]}"
  local pvcsByNamespace
  pvcsByNamespace=$(echo -e "${SNAPSHOTS_TO_RESTORE}\n${ZFS_VOLUMES}" |
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
  for namespace in "${CITUS_NAMESPACES[@]}"; do
    local pvcsInNamespace shardedClusterName

    pvcsInNamespace=$(echo "${pvcsByNamespace}" | jq -r --arg namespace "${namespace}" '.[$namespace]')
    shardedClusterName=$(echo "${pvcsInNamespace}" | jq -r '.[0].citusCluster.shardedClusterName')

    configureShardedClusterResource "${pvcsInNamespace}" "${shardedClusterName}" "${namespace}"
    unpauseCitus "${namespace}" "true"
    updateStackgresCreds "${shardedClusterName}" "${namespace}"
    routeTraffic "${namespace}"
  done
}

function configureAndValidateSnapshotRestore() {
  if [[ -z "${GCP_TARGET_PROJECT}" ]]; then
    GCP_TARGET_PROJECT="$(promptGcpProject "target")"
  fi

  if [[ -z "${GCP_SNAPSHOT_PROJECT}" ]]; then
    GCP_SNAPSHOT_PROJECT="$(promptGcpProject "snapshot source")"
  fi

  if [[ -z "${GCP_K8S_TARGET_CLUSTER_REGION}" ]]; then
    GCP_K8S_TARGET_CLUSTER_REGION="$(promptGcpClusterRegion "target" "${GCP_TARGET_PROJECT}")"
  fi

  if [[ -z "${GCP_K8S_TARGET_CLUSTER_NAME}" ]]; then
    GCP_K8S_TARGET_CLUSTER_NAME="$(promptGcpClusterName "${GCP_TARGET_PROJECT}" "${GCP_K8S_TARGET_CLUSTER_REGION}" "target")"
  fi

  if [[ -z "${SNAPSHOT_ID}" ]]; then
    SNAPSHOT_ID="$(promptSnapshotId "${GCP_SNAPSHOT_PROJECT}"))"
  fi

  SNAPSHOTS_TO_RESTORE="$(getSnapshotsById "${SNAPSHOT_ID}")"
  log "Found snapshots to restore: ${SNAPSHOTS_TO_RESTORE}"
  doContinue

  DISK_PREFIX="$(getDiskPrefix)"
  log "Target disk prefix is ${DISK_PREFIX}"

  for nodeId in "${UNIQUE_NODE_IDS[@]}"; do
    local diskName="${DISK_PREFIX}-${nodeId}-zfs"
    local diskZone nodeInfo
    diskZone="$(echo "${nodeId}" | cut -d '-' -f 2-4)"
    nodeInfo=$(echo "${NODE_ID_MAP}" | jq -r --arg NODE_ID "${nodeId}" '.[$NODE_ID]')
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

  ZFS_VOLUMES="$(getZFSVolumes)"
  NODE_ID_MAP="$(getNodeIdToPvcSnapshotsMap)"
  mapfile -t UNIQUE_NODE_IDS < <(echo "${NODE_ID_MAP}" | jq -r 'keys[]')
  mapfile -t CITUS_NAMESPACES < <(echo "${ZFS_VOLUMES}" | jq -r '.[].namespace' | sort -u)
}

STACKGRES_MINIO_ROOT="${STACKGRES_MINIO_ROOT:-sgbackups.stackgres.io}"
MINIO_SNAPSHOT_PREFIX="${MINIO_SNAPSHOT_PREFIX:-minio-sg-snapshot}"
KEEP_SNAPSHOTS="${KEEP_SNAPSHOTS:-false}"
MINIO_RESOURCE_SELECTOR="${MINIO_RESOURCE_SELECTOR:-app.kubernetes.io/component=minio}"
SOURCE_LATEST_BACKUPS=
SOURCE_BACKUP_PATHS=
GCP_SNAPSHOT_PROJECT="${GCP_SNAPSHOT_PROJECT:-}"
GCP_TARGET_PROJECT="${GCP_TARGET_PROJECT:-}"
GCP_K8S_TARGET_CLUSTER_REGION="${GCP_K8S_TARGET_CLUSTER_REGION:-}"
GCP_K8S_TARGET_CLUSTER_NAME="${GCP_K8S_TARGET_CLUSTER_NAME:-}"
REPLACE_DISKS="${REPLACE_DISKS:-true}"
SNAPSHOT_ID="${SNAPSHOT_ID:-}"
