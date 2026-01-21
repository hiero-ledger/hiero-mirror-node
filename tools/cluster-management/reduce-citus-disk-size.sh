#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils/utils.sh

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
TEMP_DISK_DEVICE="sdc"
SG_WEBHOOK_FILE="/tmp/sg-webhook-${EPOCH_SECONDS}.yaml"
WORKER_DEFAULT_SIZE="${WORKER_DEFAULT_SIZE:-300Gi}"

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

  POOL_NAME="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.poolname}')"
  if [[ -z "${POOL_NAME}" ]]; then
    log "POOL_NAME can not be empty. Exiting"
    exit 1
  fi
  TEMP_POOL_NAME="${POOL_NAME}-new"

  RECORD_SIZE="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.recordsize}')"
  if [[ -z "${RECORD_SIZE}" ]]; then
    log "RECORD_SIZE can not be empty. Exiting"
    exit 1
  fi

  COMPRESSION="$(kubectl get storageclasses.storage.k8s.io zfs -o jsonpath='{.parameters.compression}')"
  if [[ -z "${COMPRESSION}" ]]; then
    log "COMPRESSION can not be empty. Exiting"
    exit 1
  fi

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
    gcloud compute disks create "${NEW_DISK_NAME}" \
                    --project="${GCP_TARGET_PROJECT}" \
                    --type=pd-balanced \
                    --size="${DISK_SIZE}GB" \
                    --zone="${DISK_ZONE}"
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
    kubectl exec -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash \
    -c "chroot /node-fs /bin/bash -c \
       'zpool create -o autoexpand=on ${TEMP_POOL_NAME} /dev/${TEMP_DISK_DEVICE}'"

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
      kubectl exec -it -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash -c \
      "chroot /node-fs /bin/bash -c  \
      'zfs snapshot ${POOL_NAME}/${PVC_VOLUME}@initial && \
      zfs send ${POOL_NAME}/${PVC_VOLUME}@initial | pv | \
      zfs receive \
      -o compression=${COMPRESSION} \
      -o recordsize=${RECORD_SIZE} \
      -o reservation=${RESERVATION} \
      -o mountpoint=legacy \
      ${TEMP_POOL_NAME}/${PVC_VOLUME} && \
      zfs destroy ${TEMP_POOL_NAME}/${PVC_VOLUME}@initial'"

      log "Finished copy for pvc ${PVC_VOLUME} to ${TEMP_POOL_NAME}/${PVC_VOLUME}"
    done

    kubectl exec -it -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash -c \
      "chroot /node-fs /bin/bash -c \
      'zpool destroy ${POOL_NAME} && \
       zpool export ${TEMP_POOL_NAME} && \
       zpool import ${TEMP_POOL_NAME} ${POOL_NAME} && \
       zpool export ${POOL_NAME}'"

    DISK_REGION=$(echo "${DISK_ZONE}" | cut -d '-' -f 1-2)
    gcloud compute snapshots create "${NEW_DISK_NAME}" --project="${GCP_TARGET_PROJECT}" --source-disk="${NEW_DISK_NAME}" \
    --source-disk-zone="${DISK_ZONE}" --storage-location="${DISK_REGION}" --description="Resize for ${DISK_NAME}" \
    --quiet
    gcloud compute instances detach-disk "${INSTANCE_NAME}" --disk="${DISK_NAME}" --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" --quiet
    gcloud compute disks delete "${DISK_NAME}" \
    --zone="${DISK_ZONE}" \
    --project="${GCP_TARGET_PROJECT}" \
    --quiet
    gcloud compute disks create "${DISK_NAME}" \
    --project="${GCP_TARGET_PROJECT}" \
    --zone="${DISK_ZONE}" \
    --source-snapshot=projects/"${GCP_TARGET_PROJECT}"/global/snapshots/"${NEW_DISK_NAME}" --type=pd-balanced
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
    --device-name=sdb

    kubectl exec -it -n "${COMMON_NAMESPACE}" "${DAEMONSET_POD}" -- bash -c \
    "chroot /node-fs /bin/bash -c \
    'zpool import ${POOL_NAME} && \
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
