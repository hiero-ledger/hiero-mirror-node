#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils/utils.sh

requireCommand gcloud

ONE_GI_BYTES=1073741824
EPOCH_SECONDS=$(date +%s)
ZFS_VOLUMES=$(getZFSVolumes)
NODE_ID_MAP=$(echo -e "${ZFS_VOLUMES}" |
              jq -r 'group_by(.nodeId) |
                       map((.[0].nodeId) as $nodeId |
                         map(.)|
                         {
                           ($nodeId) :  {
                              pvcs: (.)
                           }
                         }
                      )|add')
COPY_PROVISIONED_IOPS="${COPY_PROVISIONED_IOPS:-12800}"
COPY_PROVISIONED_THROUGHPUT="${COPY_PROVISIONED_THROUGHPUT:-400}"
TEMP_DISK_DEVICE="sdc"
SG_WEBHOOK_FILE="/tmp/sg-webhook-${EPOCH_SECONDS}.yaml"
WORKER_DEFAULT_SIZE="${WORKER_DEFAULT_SIZE:-300Gi}"

# StorageClass parameters get spliced unescaped into privileged node commands later; reject unsafe characters now.
function requireSafeToken() {
  local name="$1" value="$2" pattern="$3"
  if [[ ! "${value}" =~ ${pattern} ]]; then
    log "${name} '${value}' contains characters that are not allowed. Exiting"
    exit 1
  fi
}

function configureAndValidate() {
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

  DISK_PREFIX="$(kubectl get daemonsets \
  -n "${COMMON_NAMESPACE}" \
  -l 'app=zfs-manager' \
  -o json |
  jq -r '.items[0].spec.template.spec.containers[0].env[] | select (.name == "DISK_PREFIX") | .value')"

  if [[ -z "${DISK_PREFIX}" ]]; then
    log "DISK_PREFIX can not be empty. Exiting"
    exit 1
  fi

  DISK_DEVICE_NAME="$(getZfsManagerEnv DISK_DEVICE_NAME)"
  DISK_DEVICE_NAME="${DISK_DEVICE_NAME:-sdb}"
  requireSafeToken "DISK_DEVICE_NAME" "${DISK_DEVICE_NAME}" '^[a-z0-9-]+$'

  POOL_NAME="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.poolname}')"
  if [[ -z "${POOL_NAME}" ]]; then
    log "POOL_NAME can not be empty. Exiting"
    exit 1
  fi
  requireSafeToken "POOL_NAME" "${POOL_NAME}" '^[A-Za-z0-9_.:-]+$'
  TEMP_POOL_NAME="${POOL_NAME}-new"

  RECORD_SIZE="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.recordsize}')"
  if [[ -z "${RECORD_SIZE}" ]]; then
    log "RECORD_SIZE can not be empty. Exiting"
    exit 1
  fi
  requireSafeToken "RECORD_SIZE" "${RECORD_SIZE}" '^[0-9]+[KkMm]?$'

  COMPRESSION="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.compression}')"
  if [[ -z "${COMPRESSION}" ]]; then
    log "COMPRESSION can not be empty. Exiting"
    exit 1
  fi
  requireSafeToken "COMPRESSION" "${COMPRESSION}" '^[A-Za-z0-9-]+$'

  AVAILABLE_DISKS="$(gcloud compute disks list \
  --project "${GCP_TARGET_PROJECT}" \
  --filter="name~${DISK_PREFIX}.*-zfs" \
  --format="json(name, sizeGb, users, zone)")"
  if [[ "${AVAILABLE_DISKS}" == "[]" ]]; then
    log "No disks found for prefix ${DISK_PREFIX}. Exiting"
    exit 1
  fi

  DISKS_TO_REDUCE_INPUT="$(readUserInput "Enter the disks(${AVAILABLE_DISKS}) to reduce (space-separated): ")"
  if [[ -z "${DISKS_TO_REDUCE_INPUT}" ]]; then
    log "POOLS_TO_UPDATE_INPUT is not set and is required. Exiting"
    exit 1
  else
    NEW_DISK_SIZES=()
    IFS=', ' read -r -a DISKS_TO_REDUCE <<<"${DISKS_TO_REDUCE_INPUT}"
    for disk in "${DISKS_TO_REDUCE[@]}"; do
      DISK_NODE_ID=${disk#"$DISK_PREFIX"-}
      DISK_NODE_ID=${DISK_NODE_ID%"-zfs"}
      DISK_ZONE=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-4)
      gcloud compute disks describe "${disk}" \
      --project="${GCP_TARGET_PROJECT}" \
      --zone="${DISK_ZONE}" >/dev/null

      DISK_SIZE="$(readUserInput "Enter new disk size for ${disk} in GB: ")"
      if [[ "$DISK_SIZE" =~ ^[0-9]*$ ]]; then
        NEW_DISK_SIZES+=("${DISK_SIZE}")
      else
        log "DISK_SIZE is not an integer is required. Exiting"
        exit 1
      fi
    done
  fi
}

function execOnNode() {
  local pod="$1"
  local cmd="$2"
  kubectl exec -n "${COMMON_NAMESPACE}" "${pod}" -- chroot /node-fs /bin/bash -c "${cmd}"
}

function resolveNodeDiskDevice() {
  local pod="$1" deviceName="$2"
  local attempts=0 devicePath

  until devicePath="$(execOnNode "${pod}" \
    "for byId in /dev/disk/by-id/google-${deviceName} /dev/disk/by-id/scsi-0Google_PersistentDisk_${deviceName}; do \
       if [ -b \"\${byId}\" ]; then \
         if [ \"\$(readlink -f \"\${byId}\")\" = /dev/${deviceName} ]; then echo /dev/${deviceName}; else echo \"\${byId}\"; fi; \
         exit 0; \
       fi; \
     done; \
     udevadm settle; \
     exit 1" 2>/dev/null)"; do
    attempts=$((attempts + 1))
    if [[ ${attempts} -ge 30 ]]; then
      log "Unable to find block device ${deviceName} via pod ${pod} after ${attempts} attempts. Exiting"
      exit 1
    fi
    log "Block device ${deviceName} not found yet via pod ${pod}, attempt ${attempts}. Waiting..."
    sleep 2
  done

  echo "${devicePath}"
}

# Matches zfs_append_partition so the imported vdev path is the one zfs-manager passes to zpool online -e.
function getDiskPartitionPath() {
  local devicePath="$1"
  if [[ "${devicePath}" == /dev/disk/* ]]; then
    echo "${devicePath}-part1"
  elif [[ "${devicePath}" == *[0-9] ]]; then
    echo "${devicePath}p1"
  else
    echo "${devicePath}1"
  fi
}

# Raises hyperdisk-balanced performance for the copy within the disk limits, never below the current values
function getCopyDiskSpec() {
  local diskSpec="${1}" sizeGb="${2}"
  local diskType currentIops currentThroughput iops throughput maxIops minThroughput maxThroughput
  IFS=',' read -r diskType currentIops currentThroughput <<<"${diskSpec}"
  if [[ "${diskType}" != "hyperdisk-balanced" ]]; then
    echo "${diskSpec}"
    return 0
  fi

  maxIops=$((sizeGb * 500 < 160000 ? sizeGb * 500 : 160000))
  iops=$((COPY_PROVISIONED_IOPS < maxIops ? COPY_PROVISIONED_IOPS : maxIops))
  iops=$((${currentIops:-0} > iops ? currentIops : iops))
  maxThroughput=$((iops / 4 < 2400 ? iops / 4 : 2400))
  minThroughput=$(((iops + 255) / 256 > 140 ? (iops + 255) / 256 : 140))
  throughput=$((COPY_PROVISIONED_THROUGHPUT < maxThroughput ? COPY_PROVISIONED_THROUGHPUT : maxThroughput))
  throughput=$((${currentThroughput:-0} > throughput ? currentThroughput : throughput))
  throughput=$((throughput > minThroughput ? throughput : minThroughput))
  echo "${diskType},${iops},${throughput}"
}

function raiseDiskPerformance() {
  local diskName="${1}" diskZone="${2}" diskSpec="${3}" copySpec="${4}"
  local iops throughput
  if [[ "${diskSpec}" == "${copySpec}" ]]; then
    return 0
  fi

  IFS=',' read -r _ iops throughput <<<"${copySpec}"
  log "Raising ${diskName} to ${iops} IOPS and ${throughput} MiB/s for the copy"
  if ! gcloud compute disks update "${diskName}" \
         --project="${GCP_TARGET_PROJECT}" \
         --zone="${diskZone}" \
         --provisioned-iops="${iops}" \
         --provisioned-throughput="${throughput}"; then
    log "Unable to raise ${diskName} performance; copying at its current performance"
  fi
}

# Runs the zfs snapshot/send/receive chain detached on the node so a local disconnect can't kill it, then polls for completion.
function copyZfsVolume() {
  local pod="$1" sourcePool="$2" destPool="$3" pvcVolume="$4" compression="$5" recordsize="$6" reservation="$7"
  # Keyed by EPOCH_SECONDS since destPool is recreated fresh every run; a marker left over from an earlier run must never match.
  local logFile="/tmp/zfs-migrate-${pvcVolume}-${EPOCH_SECONDS}.log"
  local exitFile="/tmp/zfs-migrate-${pvcVolume}-${EPOCH_SECONDS}.exit"
  local resumeToken remoteCmd alreadyRunning datasetExists exitCode

  exitCode=$(execOnNode "${pod}" "cat ${exitFile} 2>/dev/null" 2>/dev/null || true)
  if [[ "${exitCode}" == "0" ]]; then
    log "Copy for ${pvcVolume} already finished successfully in a previous run; skipping"
    return 0
  elif [[ -n "${exitCode}" ]]; then
    log "A previous background copy for ${pvcVolume} failed (exit ${exitCode}); see ${logFile} in pod ${pod}. Checking for a resumable token..."
  fi

  alreadyRunning=$(execOnNode "${pod}" "pgrep -f 'zfs receive.*${destPool}/${pvcVolume}'" 2>/dev/null || true)
  if [[ -n "${alreadyRunning}" ]]; then
    log "Copy for ${pvcVolume} is already running in the background from an earlier invocation of this script; re-attaching"
  else
    resumeToken=$(execOnNode "${pod}" "zfs get -Ho value receive_resume_token ${destPool}/${pvcVolume} 2>/dev/null" 2>/dev/null || true)
    [[ "${resumeToken}" == "-" ]] && resumeToken=""

    datasetExists=false
    execOnNode "${pod}" "zfs list ${destPool}/${pvcVolume}" &>/dev/null && datasetExists=true

    if [[ -n "${resumeToken}" ]]; then
      log "Found an interrupted receive for ${pvcVolume}; resuming from the saved token instead of re-copying from scratch"
      remoteCmd="set -o pipefail; zfs send -t ${resumeToken} | pv -f | zfs receive -s ${destPool}/${pvcVolume}"
    elif [[ "${datasetExists}" == "true" ]]; then
      log "ERROR: ${destPool}/${pvcVolume} exists but is neither resumable nor actively copying, and has no recorded exit status. Inspect it manually (zfs list / zfs get receive_resume_token) before re-running."
      exit 1
    else
      log "Starting copy for ${pvcVolume} to ${destPool} in the background so a local disconnect can't interrupt it (check progress with: kubectl exec -n ${COMMON_NAMESPACE} ${pod} -- chroot /node-fs tail -f ${logFile})"
      remoteCmd="set -o pipefail; \
zfs snapshot ${sourcePool}/${pvcVolume}@initial && \
zfs send -c ${sourcePool}/${pvcVolume}@initial | pv -f | \
zfs receive -s -o compression=${compression} -o recordsize=${recordsize} -o reservation=${reservation} -o mountpoint=legacy ${destPool}/${pvcVolume} && \
zfs destroy ${destPool}/${pvcVolume}@initial"
    fi

    execOnNode "${pod}" "rm -f ${exitFile}; setsid bash -c '${remoteCmd}; echo \$? >${exitFile}' </dev/null >${logFile} 2>&1 &"
  fi

  log "Waiting for copy of ${pvcVolume} to finish"
  local waitedSeconds=0
  while true; do
    local probe
    # Remote probe always exits 0, so a failed execOnNode means we couldn't reach the pod, not that the copy is done.
    if probe=$(execOnNode "${pod}" "if [ -f ${exitFile} ]; then cat ${exitFile}; else echo __PENDING__; fi" 2>/dev/null); then
      if [[ "${probe}" == "__PENDING__" ]]; then
        if (( waitedSeconds % 300 == 0 )); then
          log "Still copying ${pvcVolume} (${waitedSeconds}s elapsed); tail ${logFile} in pod ${pod} for live progress"
        fi
      elif [[ "${probe}" == "0" ]]; then
        log "Copy for ${pvcVolume} finished successfully"
        return 0
      else
        log "Copy for ${pvcVolume} failed (exit ${probe}); see ${logFile} in pod ${pod}. Re-run the script to resume from the saved receive token."
        exit 1
      fi
    else
      log "Could not reach pod ${pod} to check copy progress (transient connection issue?); retrying"
    fi
    sleep 30
    waitedSeconds=$((waitedSeconds + 30))
  done
}

# Async + indefinite poll since gcloud's own sync wait times out after 1800s on multi-TB snapshots.
function createSnapshotAndWait() {
  local snapshotName="$1" sourceDiskName="$2" sourceDiskZone="$3" storageLocation="$4"
  local opName

  opName="$(gcloud compute snapshots create "${snapshotName}" \
    --project="${GCP_TARGET_PROJECT}" \
    --source-disk="${sourceDiskName}" \
    --source-disk-zone="${sourceDiskZone}" \
    --storage-location="${storageLocation}" \
    --description="Resize for ${sourceDiskName}" \
    --async --quiet --format="value(name)")"
  opName="$(echo "${opName}" | tail -n1 | tr -d '[:space:]')"

  if [[ -z "${opName}" ]]; then
    log "Failed to start snapshot creation for ${snapshotName}"
    return 1
  fi

  log "Snapshot ${snapshotName} creation started (operation ${opName}); waiting for it to complete"

  while true; do
    local status
    status="$(gcloud compute operations describe "${opName}" --project="${GCP_TARGET_PROJECT}" --global \
      --format="value(status)" 2>/dev/null || true)"

    if [[ "${status}" == "DONE" ]]; then
      local errorInfo
      errorInfo="$(gcloud compute operations describe "${opName}" --project="${GCP_TARGET_PROJECT}" --global \
        --format="value(error)" 2>/dev/null || true)"
      if [[ -n "${errorInfo}" ]]; then
        log "Snapshot ${snapshotName} operation ${opName} failed: ${errorInfo}"
        return 1
      fi
      log "Snapshot ${snapshotName} completed successfully"
      return 0
    fi

    log "Snapshot ${snapshotName} still in progress (operation ${opName}, status=${status:-unknown}); checking again in 60s"
    sleep 60
  done
}

function reduceDiskSizes() {
  for diskIndex in "${!DISKS_TO_REDUCE[@]}"; do
    DISK_NAME=${DISKS_TO_REDUCE[$diskIndex]}
    DISK_SIZE=${NEW_DISK_SIZES[$diskIndex]}

    DISK_NODE_ID=${DISK_NAME#"$DISK_PREFIX"-}
    DISK_NODE_ID=${DISK_NODE_ID%"-zfs"}
    DISK_ZONE=$(echo "${DISK_NODE_ID}" | cut -d '-' -f 2-4)

    INSTANCE_NAME="$(kubectl get zfsnodes.zfs.openebs.io \
    -n "${COMMON_NAMESPACE}" \
    -o jsonpath='{.metadata.ownerReferences[0].name}' \
    "${DISK_NODE_ID}" )"
    NEW_DISK_NAME="${DISK_NAME}-resize-${EPOCH_SECONDS}"
    DISK_SPEC="$(describeDiskSpec "${DISK_NAME}" "${DISK_ZONE}")"
    COPY_DISK_SPEC="$(getCopyDiskSpec "${DISK_SPEC}" "${DISK_SIZE}")"
    mapfile -t DISK_SPEC_ARGS < <(getDiskSpecArgs "${COPY_DISK_SPEC}")
    mapfile -t SNAPSHOT_DISK_SPEC_ARGS < <(getDiskSpecArgs "${DISK_SPEC}" "${DISK_SIZE}")
    log "Creating ${NEW_DISK_NAME} with ${DISK_SPEC_ARGS[*]}"
    gcloud compute disks create "${NEW_DISK_NAME}" \
                    --project="${GCP_TARGET_PROJECT}" \
                    "${DISK_SPEC_ARGS[@]}" \
                    --size="${DISK_SIZE}GB" \
                    --zone="${DISK_ZONE}"
    raiseDiskPerformance "${DISK_NAME}" "${DISK_ZONE}" "${DISK_SPEC}" "${COPY_DISK_SPEC}"
    gcloud compute instances attach-disk "${INSTANCE_NAME}" \
    --disk "${NEW_DISK_NAME}" \
    --zone "${DISK_ZONE}" \
    --project "${GCP_TARGET_PROJECT}" \
    --device-name="${TEMP_DISK_DEVICE}"
    cat >"/tmp/${INSTANCE_NAME}.yaml" <<EOF
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: ${INSTANCE_NAME}
  namespace: common
spec:
  selector:
    matchLabels:
      instance: ${INSTANCE_NAME}
  template:
    metadata:
      labels:
        instance: ${INSTANCE_NAME}
    spec:
      hostNetwork: true
      hostPID: true
      hostIPC: true
      containers:
        - name: busybox
          image: ubuntu
          securityContext:
            privileged: true
          command:
            - tail
            - "-f"
            - /dev/null
          volumeMounts:
            - name: nodefs
              mountPath: /node-fs
            - name: devfs
              mountPath: /dev
      volumes:
        - name: nodefs
          hostPath:
            path: /
        - name: devfs
          hostPath:
            path: /dev
      nodeSelector:
        kubernetes.io/hostname: ${INSTANCE_NAME}
      tolerations:
        - effect: NoSchedule
          key: zfs
          operator: Equal
          value: "true"
EOF
    kubectl apply -f "/tmp/${INSTANCE_NAME}.yaml"
    sleep 5
    log "Waiting for helper pod to be ready"
    kubectl wait --for=condition=Ready pod -n "${COMMON_NAMESPACE}" -l "instance=${INSTANCE_NAME}" --timeout=-1s
    DAEMONSET_POD=$(kubectl get pods \
    -n "${COMMON_NAMESPACE}" \
    -l instance="${INSTANCE_NAME}" \
    -o jsonpath='{.items[*].metadata.name}')
    TEMP_DISK_PATH="$(resolveNodeDiskDevice "${DAEMONSET_POD}" "${TEMP_DISK_DEVICE}")"
    log "Creating pool ${TEMP_POOL_NAME} on ${TEMP_DISK_PATH}"
    kubectl exec -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash \
    -c "chroot /node-fs /bin/bash -c \
       'zpool create -o autoexpand=on ${TEMP_POOL_NAME} ${TEMP_DISK_PATH}'"

    PVCS=$(echo "${NODE_ID_MAP}" | jq -r --arg DISK_NODE_ID "${DISK_NODE_ID}" '.[$DISK_NODE_ID].pvcs')
    NODE_PVC_COUNT=$(echo "${PVCS}" | jq -r 'length - 1')
    for pvcIndex in $(seq 0 "${NODE_PVC_COUNT}"); do
      PVC=$(echo "${PVCS}" | jq -r --arg INDEX "$pvcIndex" '.[$INDEX | tonumber]')
      PVC_VOLUME=$(echo "${PVC}" | jq -r '.volumeName')
      CURRENT_PVC_SIZE="$(echo "${PVC}" | jq -r '.pvcSize')"
      CURRENT_PVC_SIZE="${CURRENT_PVC_SIZE%Gi}"
      NEW_PVC_SIZE="$(readUserInput "Enter new size for ${PVC_VOLUME} in Gi(current size: ${CURRENT_PVC_SIZE}): ")"
      while ! [[ "${NEW_PVC_SIZE}" =~ ^[0-9]*$ || "${NEW_PVC_SIZE}" -lt "${CURRENT_PVC_SIZE}" ]]; do
        log "NEW_PVC_SIZE is not an integer or is greater than the current size"
        NEW_PVC_SIZE="$(readUserInput "Enter new size for ${PVC_VOLUME} in Gi(current size: ${CURRENT_PVC_SIZE}): ")"
      done
      RESERVATION=$((ONE_GI_BYTES * NEW_PVC_SIZE))
      if [[ "${NEW_PVC_SIZE}" -ne "${CURRENT_PVC_SIZE}" ]]; then
        NEW_VOLUMES+=("$(echo "${PVC}" |
          jq -r --arg RESERVATION "${RESERVATION}" \
            --arg NEW_PVC_SIZE "${NEW_PVC_SIZE}Gi" \
            '. += {newPvcSize: $NEW_PVC_SIZE} |
             . += {newPvcSizeBytes: $RESERVATION}')")
      fi
      copyZfsVolume "${DAEMONSET_POD}" "${POOL_NAME}" "${TEMP_POOL_NAME}" "${PVC_VOLUME}" \
        "${COMPRESSION}" "${RECORD_SIZE}" "${RESERVATION}"

      log "Finished copy for pvc ${PVC_VOLUME} to ${TEMP_POOL_NAME}/${PVC_VOLUME}"
    done

    ZFS_MANAGER_POD="$(kubectl get pods -n "${COMMON_NAMESPACE}" -l 'app=zfs-manager' \
      --field-selector spec.nodeName="${INSTANCE_NAME}" -o jsonpath='{.items[*].metadata.name}')"
    if [[ -n "${ZFS_MANAGER_POD}" ]]; then
      log "Unmounting ${POOL_NAME} root dataset in pod ${ZFS_MANAGER_POD}"
      kubectl exec -n "${COMMON_NAMESPACE}" "${ZFS_MANAGER_POD}" -- umount "/node/${POOL_NAME}" 2>/dev/null || true
    fi

    kubectl exec -it -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash -c \
      "chroot /node-fs /bin/bash -c \
      'zpool destroy ${POOL_NAME} && \
       zpool export ${TEMP_POOL_NAME} && \
       zpool import ${TEMP_POOL_NAME} ${POOL_NAME} && \
       zpool export ${POOL_NAME}'"

    DISK_REGION=$(echo "${DISK_ZONE}" | cut -d '-' -f 1-2)
    createSnapshotAndWait "${NEW_DISK_NAME}" "${NEW_DISK_NAME}" "${DISK_ZONE}" "${DISK_REGION}"
    gcloud compute instances detach-disk "${INSTANCE_NAME}" --disk="${DISK_NAME}" --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" --quiet
    gcloud compute disks delete "${DISK_NAME}" \
    --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" \
    --quiet
    gcloud compute disks create "${DISK_NAME}" \
    --project="${GCP_TARGET_PROJECT}" \
    --zone="${DISK_ZONE}" \
    --source-snapshot=projects/"${GCP_TARGET_PROJECT}"/global/snapshots/"${NEW_DISK_NAME}" "${SNAPSHOT_DISK_SPEC_ARGS[@]}"
    gcloud compute instances detach-disk "${INSTANCE_NAME}" \
    --disk="${NEW_DISK_NAME}" --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" --quiet
    gcloud compute disks delete "${NEW_DISK_NAME}" \
    --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" \
    --quiet
    gcloud compute instances attach-disk "${INSTANCE_NAME}" \
    --disk="${DISK_NAME}" \
    --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" \
    --quiet \
    --device-name="${DISK_DEVICE_NAME}"

    DISK_PATH="$(resolveNodeDiskDevice "${DAEMONSET_POD}" "${DISK_DEVICE_NAME}")"
    DISK_PARTITION_PATH="$(getDiskPartitionPath "${DISK_PATH}")"
    log "Importing pool ${POOL_NAME} from ${DISK_PARTITION_PATH}"
    kubectl exec -it -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash -c \
    "chroot /node-fs /bin/bash -c \
    'udevadm settle || true; \
     zpool import -d ${DISK_PARTITION_PATH} ${POOL_NAME} && \
     zpool upgrade ${POOL_NAME}'"
    kubectl delete -n "${COMMON_NAMESPACE}" daemonset "${INSTANCE_NAME}"
  done
}

function updateK8sResources() {
  kubectl get validatingwebhookconfigurations.admissionregistration.k8s.io \
  "${HELM_RELEASE_NAME}" -o yaml > "${SG_WEBHOOK_FILE}"
  kubectl delete validatingwebhookconfigurations.admissionregistration.k8s.io "${HELM_RELEASE_NAME}"

  for newVolume in "${NEW_VOLUMES[@]}"; do
    PVC_NAME=$(echo "${newVolume}" | jq -r '.pvcName')
    PV_NAME=$(echo "${newVolume}" | jq -r '.volumeName')
    NAMESPACE=$(echo "${newVolume}" | jq -r '.namespace')
    NEW_PVC_SIZE=$(echo "${newVolume}" | jq -r '.newPvcSize')
    NEW_PVC_SIZE_BYTES=$(echo "${newVolume}" | jq -r '.newPvcSizeBytes')
    NEW_PVC=$(kubectl get pvc "${PVC_NAME}" -n "${NAMESPACE}" -o json |
    jq --arg NEW_PVC_SIZE "${NEW_PVC_SIZE}" '.spec.resources.requests.storage = $NEW_PVC_SIZE|
                                             del(.status)|
                                             del(.metadata.annotations["pv.kubernetes.io/bind-completed"])')
    kubectl get pvc -n "${NAMESPACE}" "${PVC_NAME}" -o yaml >"/tmp/${PVC_NAME}.yaml.bak"
    kubectl delete pvc -n "${NAMESPACE}" "${PVC_NAME}"

    kubectl patch pv "${PV_NAME}" --type='json' -p \
    '[{ "op": "replace", "path": "/spec/capacity/storage", "value": "'"${NEW_PVC_SIZE}"'" },
      { "op": "remove", "path": "/spec/claimRef" },
      { "op": "remove", "path": "/status" }]'

    kubectl patch zfsvolumes.zfs.openebs.io -n "${COMMON_NAMESPACE}" "${PV_NAME}"\
    --type='json' -p '[{ "op": "replace", "path": "/spec/capacity", "value": "'"${NEW_PVC_SIZE_BYTES}"'" }]'

    echo "${NEW_PVC}" | kubectl apply -f -

    SHARDED_CLUSTER="$(echo "${newVolume}" | jq -r '.citusCluster.shardedClusterName')"
    CLUSTER="$(echo "${newVolume}" | jq -r '.citusCluster.clusterName')"

    kubectl patch sgclusters -n "${NAMESPACE}" "${CLUSTER}" \
    --type='json' -p '[{ "op": "replace", "path": "/spec/pods/persistentVolume/size", "value": "'"${NEW_PVC_SIZE}"'" }]'

    IS_WORKER="$(echo "${newVolume}" | jq -r '.citusCluster.isCoordinator | not')"
    WORKER_OVERRIDES=$(kubectl get sgshardedclusters.stackgres.io -n "${NAMESPACE}" \
                      "${SHARDED_CLUSTER}" -o jsonpath='{.spec.shards.overrides}' |
                       jq -r 'sort_by(.index)')
    COORDINATOR_SIZE="$(kubectl get sgshardedclusters.stackgres.io -n "${NAMESPACE}" \
                       "${SHARDED_CLUSTER}" -o jsonpath='{.spec.coordinator.pods.persistentVolume.size}')"
    if [[ "${IS_WORKER}" == "true" ]]; then
      if [[ -z "${WORKER_OVERRIDES}" ]]; then
        WORKER_OVERRIDES="[]"
      fi
      INDEX=$(echo "${CLUSTER}" | grep -oE '[0-9]+$')
      PVC_OVERRIDE=$(echo "${WORKER_OVERRIDES}" | jq -r --arg INDEX "${INDEX}" \
      '.[] | select(.index == ($INDEX|tonumber))')

      if [[ -z "${PVC_OVERRIDE}" ]]; then
        PVC_OVERRIDE="{\"index\": ${INDEX}, \"pods\": {\"persistentVolume\": {\"size\": \"${NEW_PVC_SIZE}\"}}}"
        WORKER_OVERRIDES=$(echo "${WORKER_OVERRIDES}" | jq ". += [${PVC_OVERRIDE}]")
      else
        WORKER_OVERRIDES=$(echo "${WORKER_OVERRIDES}" |
        jq --arg NEW_PVC_SIZE "${NEW_PVC_SIZE}" --arg INDEX "${INDEX}" \
        '(.[] | select(.index == ($INDEX|tonumber)).pods.persistentVolume).size |= $NEW_PVC_SIZE')
      fi
    else
      COORDINATOR_SIZE="${NEW_PVC_SIZE}"
    fi

    SHARDED_CLUSTER_PATCH="{
                               \"spec\": {
                                 \"coordinator\": {
                                   \"pods\": {
                                     \"persistentVolume\": {
                                       \"size\": \"${COORDINATOR_SIZE}\"
                                     }
                                   }
                                 },
                                 \"shards\": {
                                   \"overrides\": ${WORKER_OVERRIDES},
                                   \"pods\": {
                                      \"persistentVolume\": {
                                        \"size\": \"${WORKER_DEFAULT_SIZE}\"
                                      }
                                    }
                                 }
                              }
                          }"
    log "Patching sharded cluster ${SHARDED_CLUSTER} in namespace ${NAMESPACE} with ${SHARDED_CLUSTER_PATCH}"
    kubectl patch sgshardedclusters.stackgres.io "${SHARDED_CLUSTER}" \
     -n "${NAMESPACE}" \
     --type merge \
     -p "${SHARDED_CLUSTER_PATCH}"
  done
  kubectl apply -f "${SG_WEBHOOK_FILE}"
}

configureAndValidate

NAMESPACES=($(echo $ZFS_VOLUMES | jq -r '.[].namespace' | tr ' ' '\n' | sort -u | tr '\n' ' '))
for namespace in "${NAMESPACES[@]}"; do
  unrouteTraffic "${namespace}"
  pauseCitus "${namespace}"
done

NEW_VOLUMES=()
reduceDiskSizes
updateK8sResources

NEW_VOLUMES_JSON=$(echo "${NEW_VOLUMES[@]}" | jq -s)
MODIFIED_NAMESPACES=($(echo "${NEW_VOLUMES_JSON}" | jq -r '.[].namespace' | tr ' ' '\n' | sort -u | tr '\n' ' '))
for namespace in "${MODIFIED_NAMESPACES[@]}"; do
  PVC_INFO=$(echo "${NEW_VOLUMES_JSON}" | jq -r --arg NAMESPACE "${namespace}" 'map(select(.namespace == $NAMESPACE))[0]')
  SHARDED_CLUSTER="$(echo "${PVC_INFO}" | jq -r '.citusCluster.shardedClusterName')"
  WORKER_OVERRIDES=$(kubectl get sgshardedclusters.stackgres.io -n "${namespace}" \
                    "${SHARDED_CLUSTER}" -o jsonpath='{.spec.shards.overrides}' |
                      jq -r 'sort_by(.index)')
  COORDINATOR_SIZE="$(kubectl get sgshardedclusters.stackgres.io -n "${NAMESPACE}" \
                         "${SHARDED_CLUSTER}" -o jsonpath='{.spec.coordinator.pods.persistentVolume.size}')"
  log "
        **** IMPORTANT ****
        Please configure your helm values.yaml for namespace ${namespace} to have the following values:

        stackgres.coordinator.pods.persistentVolume.size=${COORDINATOR_SIZE}

        stackgres.worker.overrides=${WORKER_OVERRIDES}

        stackgres.worker.pods.persistentVolume.size=${WORKER_DEFAULT_SIZE}
        "
  log "Continue to acknowledge these values have been applied to the helm values.yaml."
  doContinue
done

log "Restarting Stackgres pod"
kubectl delete pods -n "${COMMON_NAMESPACE}" -l 'app=StackGresConfig'
sleep 5
kubectl wait --for=condition=Ready pod -n "${COMMON_NAMESPACE}" -l 'app=StackGresConfig' --timeout=-1s

log "Restarting ZFS init daemonset"
kubectl delete pods -n "${COMMON_NAMESPACE}" -l 'app=zfs-manager'
sleep 5
kubectl wait --for=condition=Ready pod -n "${COMMON_NAMESPACE}" -l 'app=zfs-manager' --timeout=-1s

log "Restarting ZFS node pods"
kubectl delete pods -n "${COMMON_NAMESPACE}" -l 'component=openebs-zfs-node'
sleep 5
kubectl wait --for=condition=Ready pod -n "${COMMON_NAMESPACE}" -l 'component=openebs-zfs-node' --timeout=-1s

for namespace in "${NAMESPACES[@]}"; do
  unpauseCitus "${namespace}"
  routeTraffic "${namespace}"
done
