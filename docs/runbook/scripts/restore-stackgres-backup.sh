#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euox pipefail

source ./utils.sh

BACKUP_TO_RESTORE=
CLUSTER=
CLUSTER_CONFIG=

# This is a bug in Stackgres start script that prevents backups from restoring if they have been restored previously
# Need to remove this marker file so backup is restored correctly
function cleanSnapshotMarker() {
  for pvc in $(kubectl get pvc -l 'app=StackGresCluster' --output=custom-columns=':.metadata.name' --no-headers); do
    log "Removing snapshot marker file for ${pvc}"
    cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: snap-marker-wiper
spec:
  backoffLimit: 0
  template:
    spec:
      containers:
      - name: debian
        image: debian:bookworm-slim
        command:
        - "/bin/bash"
        - "-ecx"
        - rm -fr /pgdata/data/.already_restored_from_volume_snapshot_*
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
          claimName: ${pvc}
EOF
      kubectl wait --for=condition=complete job/snap-marker-wiper --timeout=-1s
      kubectl delete job/snap-marker-wiper
    done
}

# Adjust coordinator pods storage
# - Swap coord-0's PV with the backup's source PV if different. It's needed since the coordinator backup is always
#   restored to coord-0's PV
# - Wipe out all other coordinator pods (they will be replicas when restored) storage so they'll get re-created; can't
#   delete them since any such volume might belong to the primary in the past and thus be the source of an older backup
function adjustCoordStorage() {
  coordPod0="${CLUSTER}-coord-0"
  coordPod0Pvc="${CLUSTER}-coord-data-${coordPod0}"
  coordPod0Pv=$(kubectl get pvc/"${coordPod0Pvc}" -o json | jq -r '.spec.volumeName')
  sgBackupCoord=$(kubectl get sgbackups -o json | \
    jq -r --arg backup "${BACKUP_TO_RESTORE}" --arg sgCluster "${CLUSTER}-coord" \
    '.items[] | select(.metadata.ownerReferences[0].name == $backup and .spec.sgCluster == $sgCluster) | .metadata.name')
  backupSourcePv=$(getSnapshotHandle "${sgBackupCoord}" | cut -d'@' -f1)
  if [[ "${backupSourcePv}" != "${coordPod0Pv}" ]]; then
    log "Coordinator backup's source PV is different from the current ${coordPod0}'s PV, swap them"
    backupSourcePvc=$(kubectl get pvc -l 'app=StackGresCluster,citus-group=0' -o json | \
      jq --arg volumeName "${backupSourcePv}" -r '.items[] | select(.spec.volumeName == $volumeName) | .metadata.name')
    swapPv "${coordPod0Pvc}" "${backupSourcePvc}"
  fi

  for pvc in $(kubectl get pvc -l 'app=StackGresCluster,citus-group=0' --output=custom-columns=':.metadata.name' --no-headers); do
    if [[ "${pvc}" =~ ^.*-coord-0$ ]]; then
      continue
    fi

    log "Wiping out ${pvc} data"
    cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: coord-data-wiper
spec:
  backoffLimit: 0
  template:
    spec:
      containers:
      - name: debian
        image: debian:bookworm-slim
        command:
        - "/bin/bash"
        - "-ecx"
        - rm -fr /pgdata/*
        volumeMounts:
        - mountPath: /pgdata
          name: pgdata
      nodeSelector:
        citus-role: coordinator
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
          claimName: ${pvc}
EOF
    kubectl wait --for=condition=complete job/coord-data-wiper --timeout=-1s
    kubectl delete job/coord-data-wiper
  done
}

function checkCoordinatorReplica() {
  EXPECTED_STATE="running,streaming"
  TIMEOUT_SECONDS=300

  log "Waiting for coordinator replica to become streaming"
  primary=$(kubectl get pods -l 'app=StackGresCluster,citus-group=0,role=master' -o name)

  deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [[ "$SECONDS" -lt "${deadline}" ]]; do
    state=$(kubectl exec "${primary}" -c patroni -- patronictl list --group 0 --format json | \
      jq -r '[.[].State] | sort | join(",")')
    if [[ "${state}" == "${EXPECTED_STATE}" ]]; then
      log "Coordinator replica has entered streaming state"
      return
    fi

    log "Coordinator group state is ${state}. Waiting for it to become ${EXPECTED_STATE}"
    sleep 5
  done

  log "Coordinator replica does not enter streaming state in ${TIMEOUT_SECONDS} seconds. Exiting"
  exit 1
}

function createSGShardedClusterConfigWithRestore() {
  sourceConfig=$(kubectl get sgshardedclusters "${CLUSTER}" -o json | \
    jq 'del(.metadata.creationTimestamp,.metadata.generation,.metadata.resourceVersion,.metadata.uid,.spec.configurations.backups,.status)')
  initialDataConfig=$(cat <<EOF
{
  "restore": {
    "fromBackup": {
      "name": "${BACKUP_TO_RESTORE}"
    },
    "downloadDiskConcurrency": 1
  }
}
EOF
)
  CLUSTER_CONFIG=$(echo "${sourceConfig}" | \
    jq --argjson initialDataConfig "${initialDataConfig}" '.spec.initialData=$initialDataConfig')
  log "Created SGShardedCluster configuration to restore backup ${BACKUP_TO_RESTORE}"
}

function deleteCluster() {
  kubectl delete sgshardedclusters "${CLUSTER}"
  log "Deleted SGShardedCluster \"${CLUSTER}\", waiting for all pods to terminate"
  kubectl wait --for=delete pods -l 'app=StackGresCluster' --timeout=-1s
  log "All pods terminated"
  kubectl annotate pvc -l 'app=StackGresCluster' stackgres.io/reconciliation-pause- --overwrite
}

function ensureNoBackupRunning() {
  count=$(kubectl get sgshardedbackups -o yaml | yq '[.items[] | select(.status.process.status == "Running")] | length')
  if [[ "$count" -gt 0 ]]; then
    log "There is a backup running. Exiting"
    exit 1
  fi
}

function findShardedCluster() {
  clusters=($(kubectl get sgshardedclusters --output=custom-columns=':.metadata.name' --no-headers))
  if [[ ${#clusters[@]} -eq 0 ]]; then
    log "No SGShardedClusters found. Exiting"
    exit 1
  elif [[ ${#clusters[@]} -gt 1 ]]; then
    log "More than one SGShardedClusters found. Exiting"
    exit 1
  fi

  CLUSTER="${clusters[0]}"
  log "Will restore backup to SGShardedCluster \"${CLUSTER}\""
  doContinue
}

function getSnapshotHandle() {
  local sgBackup=$1

  volumeSnapshot=$(kubectl get sgbackups "${sgBackup}" -o json | jq -r '.status.volumeSnapshot.name')
  volumeSnapshotContent=$(kubectl get volumesnapshots "${volumeSnapshot}" -o json | \
    jq -r '.status.boundVolumeSnapshotContentName')
  kubectl get volumesnapshotcontents "${volumeSnapshotContent}" -o json | jq -r '.status.snapshotHandle'
}

function pickShardedBackup() {
  allBackups=($(kubectl get sgshardedbackups -o yaml | \
    yq '.items[] | select(.status.process.status == "Completed") | .status.process.timing.start + "/" + .metadata.name' | sort -r))
  if [[ ${#allBackups[@]} -eq 0 ]]; then
    log "No completed backups found. Exiting"
    exit 0
  elif [[ ${#allBackups[@]} -eq 1 ]]; then
    log "Only one SGShardedBackup available"
    choice="${allBackups[0]}"
  else
    # More than one backups
    index=1
    echo "Available backups ordered from most recent to oldest:"
    for backup in "${allBackups[@]}"; do
      IFS='/' read -r startDate name <<< "${backup}"
      echo "  ${index}. ${name} - created at ${startDate}"
      index=$((index + 1))
    done

    read -p "Enter the number of the backup to restore: " backupIndex
    [[ -z "${backupIndex}" || "${backupIndex}" -lt 1 || "${backupIndex}" -gt ${#allBackups[@]} ]] && \
      echo "Invalid choice" && exit 1 || true

    if [[ "${backupIndex}" -gt 1 ]]; then
      echo "WARNING!!! You are about to restore an older backup, all later backups have to be removed before proceeding"
      doContinue
      count=$((backupIndex-1))
      kubectl delete sgshardedbackups $(echo "${allBackups[@]:0:${count}}" | sed -E 's/[:TZ0-9\-]+\///g')
      log "Deleted ${count} most recent SGShardedBackups"
    fi

    choice="${allBackups[$((backupIndex-1))]}"
  fi

  BACKUP_TO_RESTORE=$(echo "${choice}" | cut -d'/' -f2)
  status=$(kubectl get sgshardedbackups "${BACKUP_TO_RESTORE}" -o yaml | yq '.status')
  log "Will restore SGShardedBackup ${BACKUP_TO_RESTORE} with status ${status}"
  doContinue
}

function prepare() {
  findShardedCluster
  ensureNoBackupRunning
  pickShardedBackup
  createSGShardedClusterConfigWithRestore
  preserveResources
  unrouteTraffic "${CURRENT_NAMESPACE}"
  deleteCluster
}

function preserveResources() {
  kubectl annotate pvc -l 'app=StackGresCluster' stackgres.io/reconciliation-pause="true" --overwrite

  for pvc in $(kubectl get pvc -l 'app=StackGresCluster' -o name); do
    kubectl patch "${pvc}" --type=json -p='[{"op": "remove", "path": "/metadata/ownerReferences"}]' || true
  done

  log "Removed ownerReferences from all stackgres PVCs to avoid being garbage collected"
}

function recreateManagedCluster() {
  if kubectl get helmrelease -n "${HELM_RELEASE_NAME}" > /dev/null 2>&1; then
    log "Resuming helmrelease to recreate managed SGShardedCluster"
    flux resume helmrelease -n "${CURRENT_NAMESPACE}" "${HELM_RELEASE_NAME}" --timeout 30m
  fi

  unpauseCitus "${CURRENT_NAMESPACE}"
  updateStackgresCreds "${CLUSTER}" "${CURRENT_NAMESPACE}"
  routeTraffic "${CURRENT_NAMESPACE}"
  log "SGShardedCluster ${CLUSTER} is ready"
}

function restoreBackup() {
  log "Restoring..."

  adjustCoordStorage
  rollbackZfsVolumes
  cleanSnapshotMarker

  log "Creating SGShardedCluster with the restore configuration"
  echo "${CLUSTER_CONFIG}" | kubectl apply -f -

  unpauseCitus "${CURRENT_NAMESPACE}"
  updateStackgresCreds "${CLUSTER}" "${CURRENT_NAMESPACE}"
  checkCitusMetadataSyncStatus "${CURRENT_NAMESPACE}"
  checkCoordinatorReplica

  # Once again remove ownerReferences since in restore they will get updated with new owners
  preserveResources
  deleteCluster
  log "Backup ${BACKUP_TO_RESTORE} restored successfully"
}

function rollbackZfsVolumes() {
  # ZFS node id to GKE node map
  nodeIdMap=$(kubectl_common get zfsnodes -o json | \
    jq '[.items[].metadata] | map({(.name): .ownerReferences[0].name}) | add')
  # GKE node to ZFS daemonset pod map
  nodeZfsPodMap=$(kubectl_common get pods -l 'name=openebs-zfs-node' -o json | \
    jq '.items | map({(.spec.nodeName): .metadata.name}) | add')
  nodeIdZfsPodMap=$(echo "${nodeIdMap}" | \
    jq --argjson nodeZfsPodMap "${nodeZfsPodMap}" 'to_entries | map({(.key): $nodeZfsPodMap[.value]}) | add')

  for sgBackup in $(kubectl get sgshardedbackups "${BACKUP_TO_RESTORE}" -o json | jq -r '.status.sgBackups[]'); do
    snapshotHandle=$(getSnapshotHandle "${sgBackup}")
    zfsSnapshot=$(echo "${snapshotHandle}" | cut -d'@' -f2)
    IFS=' ' read -r nodeId poolName <<< \
      "$(kubectl_common get zfssnapshots "${zfsSnapshot}" -o json | jq -r '.spec | .ownerNodeID + " " + .poolName')"
    zfsPod=$(echo "${nodeIdZfsPodMap}" | jq -r --arg nodeId "${nodeId}" '.[$nodeId]')
    fullSnapshotHandle="${poolName}/${snapshotHandle}"

    log "Rolling back ZFS volume to snapshot ${fullSnapshotHandle}"
    kubectl_common exec -c openebs-zfs-plugin "${zfsPod}" -- zfs rollback -r "${fullSnapshotHandle}"
  done

  log "Rolled back all ZFS volumes to the snapshots created by the backup ${BACKUP_TO_RESTORE}"
}

function swapPv() {
  local pvcs=($1 $2)
  volumeNames=($(kubectl get pvc "${pvcs[@]}" -o json | jq -r '.items[].spec.volumeName'))
  firstPvcConfig=$(kubectl get pvc "${pvcs[0]}" -o json | \
    jq --arg volumeName "${volumeNames[1]}" \
      'del(.metadata.annotations["volume.kubernetes.io/selected-node"],.metadata.creationTimestamp,.metadata.resourceVersion,.metadata.uid,.status)
      | .spec.volumeName = $volumeName')
  secondPvcConfig=$(kubectl get pvc "${pvcs[1]}" -o json | \
      jq --arg volumeName "${volumeNames[0]}" \
        'del(.metadata.annotations["volume.kubernetes.io/selected-node"],.metadata.creationTimestamp,.metadata.resourceVersion,.metadata.uid,.status)
        | .spec.volumeName = $volumeName')

  log "Removing coordinator PVCs"
  kubectl delete pvc "${pvcs[@]}"

  log "Removing claimRef from coordinator PVs"
  for volumeName in "${volumeNames[@]}"; do
    kubectl patch pv/"${volumeName}" --type=json -p='[{"op": "remove", "path": "/spec/claimRef"}]' || true
  done

  log "Recreating coordinator PVCs to swap the volumes"
  echo "$firstPvcConfig" | kubectl apply -f -
  echo "$secondPvcConfig" | kubectl apply -f -
  kubectl wait --for=jsonpath='{.status.phase}'=Bound pvc "${pvcs[@]}" --timeout=-1s
}

CURRENT_NAMESPACE=$(kubectl config view --minify --output 'jsonpath={..namespace}')

prepare
restoreBackup
recreateManagedCluster
