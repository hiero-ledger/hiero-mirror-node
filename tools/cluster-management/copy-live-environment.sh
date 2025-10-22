#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

source ./utils/utils.sh
source ./utils/input-utils.sh
source ./utils/snapshot-utils.sh

CREATE_NEW_BACKUPS="${CREATE_NEW_BACKUPS:-true}"
DEFAULT_POOL_MAX_PER_ZONE="${DEFAULT_POOL_MAX_PER_ZONE:-5}"
DEFAULT_POOL_NAME="${DEFAULT_POOL_NAME:-default-pool}"
K8S_SOURCE_CLUSTER_CONTEXT=${K8S_SOURCE_CLUSTER_CONTEXT:-}
K8S_TARGET_CLUSTER_CONTEXT=${K8S_TARGET_CLUSTER_CONTEXT:-}
TEST_KUBE_NAMESPACE="${TEST_KUBE_NAMESPACE:-testkube}"
TEST_KUBE_TARGET_NAMESPACE=("mainnet-citus")
WAIT_FOR_K6="${WAIT_FOR_K6:-false}"

function deleteBackupsFromSource() {
  local lines
  lines="$(
    jq -r '
      to_entries[]
      | .key as $namespace
      | .value
      | to_entries[]
      | select(.value != null and .value != "")
      | "\($namespace)\t\(.value)"
    ' <<<"${BACKUPS_MAP}"
  )" || {
    log "Failed to parse BACKUPS_MAP"
    return 1
  }

  if [[ -z "${lines}" ]]; then
    log "No backups to delete."
    return 0
  fi

  local namespace
  local name
  while IFS=$'\t' read -r namespace name; do
    [[ -z "${namespace}" || -z "$name" ]] && continue
    log "Deleting SGShardedBackup/${name} in namespace=${namespace}"
    if ! kubectl delete sgshardedbackups.stackgres.io "$name" -n "${namespace}" --ignore-not-found 1>&2; then
      log "WARN: delete command failed for ${name} in ${namespace}"
      continue
    fi
    while kubectl get sgshardedbackups.stackgres.io "$name" -n "${namespace}" >/dev/null 2>&1; do
      sleep 1
    done
    log "Deleted ${name} in ${namespace}"
  done <<< "$lines"
}

function createSgBackup() {
  local namespace="${1}"
  local sgCluster="${2}"
  local attempt=1

  while true; do
    local backupName
    local status
    local msg
    backupName="${HELM_RELEASE_NAME}-citus-$(date -u +"%Y-%m-%d-%H-%M-%S")"

    cat <<EOF | kubectl apply -n "${namespace}" -f - 1>&2
apiVersion: stackgres.io/v1
kind: SGShardedBackup
metadata:
  name: ${backupName}
  namespace: ${namespace}
spec:
  sgShardedCluster: ${sgCluster}
  managedLifecycle: true
  maxRetries: 0
EOF
    log "Attempt ${attempt}: waiting for SGShardedBackup/${backupName} (namespace=${namespace}, cluster=${sgCluster})."
    while true; do
      status="$(
        kubectl get sgshardedbackups.stackgres.io "${backupName}" -n "${namespace}" -o json 2>/dev/null \
          | jq -r '.status.process.status // empty'
      )"
      case "${status}" in
        Completed)
          echo "${backupName}"
          return 0
          ;;
        Failed)
          msg="$(
            kubectl get sgshardedbackups.stackgres.io "${backupName}" -n "${namespace}" -o json 2>/dev/null \
              | jq -r '.status.process.message // "No error message available"'
          )"
          log "Backup ${backupName} FAILED (namespace=${namespace}, cluster=${sgCluster}): ${msg}"
          kubectl delete sgshardedbackups.stackgres.io "${backupName}" -n "${namespace}" --ignore-not-found 1>&2
          while kubectl get sgshardedbackups.stackgres.io "${backupName}" -n "${namespace}" >/dev/null 2>&1; do
            sleep 2
          done
          sleep $(( attempt < 5 ? attempt*3 : 15 ))
          attempt=$((attempt+1))
          break
          ;;
        ""|Pending|Running)
          sleep 3
          ;;
        *)
          log "Status=${status} (waiting…)"
          sleep 3
          ;;
      esac
    done
  done
}

function runBackupsForAllNamespaces() {
  if [[ "${CREATE_NEW_BACKUPS}"  != "true" ]]; then
    return 0
  fi

  local shardedClusters
  shardedClusters="$(kubectl get sgshardedclusters -A -o json 2>/dev/null | jq -c '.items[]?')"
  if [[ -z "${shardedClusters}" ]]; then
    echo '{}'
    return 1
  fi

  local out='{}'
  while IFS= read -r item; do
    local namespace
    local cluster
    local backup
    namespace="$(jq -r '.metadata.namespace' <<<"$item")"
    cluster="$(jq -r '.metadata.name' <<<"$item")"
    [[ -z "$namespace" || -z "$cluster" ]] && continue

    log "Launching backup: namespace=${namespace}, cluster=${cluster}"
    if backup="$(createSgBackup "${namespace}" "${cluster}")"; then
      log "Completed: namespace=${namespace}, cluster=${cluster}, backup=${backup}"
      out="$(jq --arg namespace "${namespace}" --arg cluster "$cluster" --arg name "$backup" \
                '(.[$namespace] //= {}) | .[$namespace][$cluster] = $name' <<<"$out")"
    else
      log "ERROR: backup failed for namespace=${namespace}, cluster=${cluster}"
      exit 1
    fi
  done <<< "${shardedClusters}"

  echo "${out}"
}

function getBackupPaths() {
  kubectl get sgshardedclusters --all-namespaces -o json \
    | jq '[
        .items[]
        | {
            (.metadata.namespace): {
              (.metadata.name): (
                .spec.configurations.backups[0].paths // []
              )
            }
          }
      ]
      | add'
}

function getLatestBackup() {
  local backups
  local zfsSnapshots
  backups=$(kubectl get sgbackups.stackgres.io -A -o json | jq '
  .items|
  map(select(.status.process.status == "Completed"))|
  map({
    name: .metadata.name,
    ownerReferences: .metadata.ownerReferences,
    namespace: .metadata.namespace,
    pod: .status.backupInformation.sourcePod,
    backupLabel: .status.volumeSnapshot.backupLabel
  })')
  zfsSnapshots=$(kubectl get volumesnapshotcontents.snapshot.storage.k8s.io -o json | jq '
  .items|
  map({
    snapshotRef: .spec.volumeSnapshotRef.name,
    volumeHandle: .spec.source.volumeHandle,
    snapshotHandle: .status.snapshotHandle
  })')

  echo -e "${backups}\n${zfsSnapshots}" |
  jq -s '
      .[0] as $backups
    | .[1] as $snapshots
    | $backups
    | map(select(.ownerReferences and (.ownerReferences | length) > 0 and .ownerReferences[0].name))
    | sort_by(.namespace, .ownerReferences[0].name)
    | group_by(.namespace)
    | map({
        key: .[0].namespace,
        value: (
          . as $nsItems
          | ($nsItems | map(.ownerReferences[0].name) | max) as $latestGroup
          | $nsItems
          | map(select(.ownerReferences[0].name == $latestGroup))
          | map(.name as $refName | {
              name: .name,
              pod: .pod,
              backupLabel: .backupLabel,
              shardedBackup: .ownerReferences[0].name,
              volumeHandle: ($snapshots | map(select(.snapshotRef == $refName)) | .[0].volumeHandle),
              snapshotHandle: ($snapshots | map(select(.snapshotRef == $refName)) | .[0].snapshotHandle)
            })
        )
      })
    | from_entries
  '
}

function contextExists() {
  local context="$1"
  kubectl config get-contexts "${context}" >/dev/null 2>&1
}

function changeContext() {
  local context="${1}"
  if contextExists "${context}"; then
    log "Change context to ${context} source cluster"
    kubectl config use-context "${context}"
  else
    log "Context ${context} doesn't exist"
    exit 1
  fi
  DISK_PREFIX="$(getDiskPrefix)"
}

function snapshotSource() {
  KEEP_SNAPSHOTS="true"
  changeContext "${K8S_SOURCE_CLUSTER_CONTEXT}"
  if [[ -z "${SNAPSHOT_ID}" ]]; then
    PAUSE_CLUSTER="false"
    BACKUPS_MAP=$(runBackupsForAllNamespaces)
    SOURCE_LATEST_BACKUPS="$(getLatestBackup)"
    SOURCE_BACKUP_PATHS="$(getBackupPaths)"
    SNAPSHOT_ID="$(snapshotCitusDisks)"
    printf "%s" "${SOURCE_LATEST_BACKUPS}" > "shardedBackups-${SNAPSHOT_ID}.json"
    deleteBackupsFromSource
  else
    if [[ ! -f "shardedBackups-${SNAPSHOT_ID}.json" ]]; then
      log "SNAPSHOT_ID provided but source sharded backup file not found"
      exit 1
    fi
    SOURCE_LATEST_BACKUPS="$(< "shardedBackups-${SNAPSHOT_ID}.json")"
    SOURCE_BACKUP_PATHS="$(getBackupPaths)"
  fi
}

function patchBackupPaths() {
  local lines
  lines="$(
    jq -r '
      to_entries[] as $namespace
      | $namespace.value
      | to_entries[]
      | "\($namespace.key)\t\(.key)\t\( (.value // []) | tojson )"
    ' <<<"${SOURCE_BACKUP_PATHS}" 2>/dev/null
  )" || { log "Failed to parse SOURCE_BACKUP_PATHS JSON"; return 1; }

  if [[ -z "${lines}" ]]; then
    log "No entries to patch."
    return 0
  fi

  local namespace
  local cluster
  local patch
  local pathsJson
  while IFS=$'\t' read -r namespace cluster pathsJson; do
    [[ -z "${namespace}" || -z "${cluster}" ]] && continue

    log "Patching SGShardedCluster/${cluster} in namespace=${namespace}"

    patch="$(jq -n --argjson arr "${pathsJson}" \
      '[{"op":"replace","path":"/spec/configurations/backups/0/paths","value":$arr}]')"

    if kubectl -n "${namespace}" patch sgshardedclusters.stackgres.io "${cluster}" \
         --type='json' -p "${patch}" 1>&2; then
      log "replaced paths"
    fi
  done <<< "${lines}"
}

function scaleupResources() {
  waitForClusterOperations "${DEFAULT_POOL_NAME}"

  gcloud container clusters resize "${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --node-pool="${DEFAULT_POOL_NAME}" \
    --project="${GCP_TARGET_PROJECT}" \
    --num-nodes=1 \
    --quiet

  gcloud container node-pools update "${DEFAULT_POOL_NAME}" \
    --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --project="${GCP_TARGET_PROJECT}" \
    --enable-autoscaling \
    --min-nodes=1 \
    --max-nodes="${DEFAULT_POOL_MAX_PER_ZONE}" \
    --quiet

  log "Waiting for Kubernetes API to become READY..."
  while true; do
    if kubectl --request-timeout=5s get --raw='/readyz' 2>/dev/null | grep -q '^ok$'; then
      log "K8S API is ready"
      return 0
    fi

    log "K8S API not ready yet"
    sleep 5
  done
}

function restoreTarget() {
  if [[ -z "${SNAPSHOT_ID}" ]]; then
    log "SNAPSHOT_ID is not set"
    exit 1
  fi

  PAUSE_CLUSTER="true"
  changeContext "${K8S_TARGET_CLUSTER_CONTEXT}"
  configureAndValidateSnapshotRestore
  scaleupResources
  resumeCommonChart
  patchBackupPaths
  replaceDisks
}

function deleteSnapshots() {
  local snapshots
  local name
  snapshots="$(getSnapshotsById)"

  log "Deleting snapshots ${snapshots}"
  doContinue
  jq -r '.[].name' <<< "${snapshots}" | while read -r name; do
    [[ -z "${name}" ]] && continue
    log "Deleting snapshot ${name} from project"
    if ! gcloud compute snapshots delete "${name}" \
        --project "${GCP_SNAPSHOT_PROJECT}" \
        --quiet; then
      log "ERROR: failed to delete snapshot ${name}"
    fi
  done

  return 0
}

function scaleDownNodePools() {
  resizeCitusNodePools 0

  waitForClusterOperations "${DEFAULT_POOL_NAME}"
  gcloud container node-pools update "${DEFAULT_POOL_NAME}" \
    --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --project="${GCP_TARGET_PROJECT}" \
    --no-enable-autoscaling \
    --quiet

  sleep 10

  local poolNodes
  log "Cordoning nodes in pool ${DEFAULT_POOL_NAME}"
  mapfile -t poolNodes < <(
    kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" \
      get nodes -l "cloud.google.com/gke-nodepool=${DEFAULT_POOL_NAME}" \
      -o name
  )

  for node in "${poolNodes[@]}"; do
    [[ -z "$node" ]] && continue
    if ! kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" get "$node" >/dev/null 2>&1; then
      log "Node ${node} no longer exists; skipping cordon"
      continue
    fi
    kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" cordon "$node" || true
  done

  log "Draining nodes in pool ${DEFAULT_POOL_NAME}"
  for node in "${poolNodes[@]}"; do
    [[ -z "$node" ]] && continue

    while true; do
      if ! kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" get "$node" >/dev/null 2>&1; then
        log "Node ${node} disappeared before/while draining; skipping"
        break
      fi

      if kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" drain "$node" \
            --ignore-daemonsets \
            --delete-emptydir-data \
            --grace-period=60 \
            --timeout=15m \
            --force; then
        log "Drained ${node}"
        break
      fi

      log "Failed to drain ${node}, retrying"
      kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" get pods -A -o wide --field-selector spec.nodeName="${node#node/}" || true
      kubectl --context "${K8S_TARGET_CLUSTER_CONTEXT}" cordon "$node" || true
      sleep 30
    done
  done

  log "Scaling down default pool to 0 nodes"
  until gcloud container clusters resize "${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --node-pool="${DEFAULT_POOL_NAME}" \
    --project="${GCP_TARGET_PROJECT}" \
    --num-nodes=0 \
    --quiet; do
      log "Failed to scale down default pool, retrying"
      sleep 5
  done
}


function removeDisks() {
  local disksToDelete
  local diskJson
  local diskName
  local diskZone
  local zoneLink
  disksToDelete="$(getCitusDiskNames "${GCP_TARGET_PROJECT}" "${DISK_PREFIX}")"
  log "Will delete disks ${disksToDelete}"
  doContinue
  jq -c '.[]' <<< "${disksToDelete}" | while read -r diskJson; do
    diskName="$(jq -r '.name' <<<"${diskJson}")"
    zoneLink="$(jq -r '.zone' <<<"${diskJson}")"
    diskZone="${zoneLink##*/}"
    log "Deleting ${diskName}"
    deleteDisk "${diskName}" "${diskZone}"
  done

  diskName="$(getMinioDiskName)"
  diskZone="$(getZoneFromPv "${diskName}")"

  log "Deleting minio sg backup disk"
  doContinue
  deleteDisk "${diskName}" "${diskZone}"
}

function resolveHpaName() {
   local component="$1"
   local ns="$2"
   local -a hpas
   mapfile -t hpas < <(kubectl get hpa -n "${ns}" \
     -l "app.kubernetes.io/component=${component}" \
     -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null)

   if [[ ${#hpas[@]} -eq 0 || -z "${hpas[0]}" ]]; then
     log "No HPA found in ${ns} with label app.kubernetes.io/component=${component}"
     return 0
   fi
   if [[ ${#hpas[@]} -gt 1 ]]; then
     log "Expected exactly 1 HPA in ${ns} for component=${component}, found ${#hpas[@]}: ${hpas[*]}"
     return 0
   fi

   printf '%s\n' "${hpas[0]}"
}

function getHpaMaxReplicas() {
  local ns="$1"
  local hpaName="$2"
  if [[ -z "$ns" || -z "$hpaName" ]]; then
    echo ""
    return 0
  fi
  kubectl get hpa "${hpaName}" -n "${ns}" -o jsonpath='{.spec.maxReplicas}' 2>/dev/null || true
}

function scaleHpaMin() {
  local ns="$1"
  local hpaName="$2"
  local min="${3:-1}"
  if [[ -z "$ns" || -z "$hpaName" ]]; then
    echo ""
    return 0
  fi
  log "Patching HPA ${hpaName} in ${ns}: minReplicas=${min}"
  kubectl patch hpa "${hpaName}" -n "${ns}" --type='merge' \
    -p "{\"spec\":{\"minReplicas\":${min}}}" >/dev/null || true
}

function teardownResources() {
  log "Tearing down resources"
  for namespace in "${CITUS_NAMESPACES[@]}"; do
    unrouteTraffic "${namespace}"
    pauseCitus "${namespace}" "true"
    kubectl delete pdb -n "${namespace}" --all --ignore-not-found 1>&2
  done

  suspendCommonChart
  kubectl delete pdb -n "${COMMON_NAMESPACE}" --all --ignore-not-found 1>&2

  scaleDownNodePools
  removeDisks
}

function waitForK6PodExecution() {
  local testName="${1:-}"
  local targetNamespace="${2:-}"
  local job out

  if [[ -z "${testName}" || -z "${targetNamespace}" ]]; then
    log "ERROR: waitForK6PodExecution requires <testName> and <namespace>"
    return 1
  fi

  job=""
  until {
    if out="$(
      kubectl get jobs -n "${TEST_KUBE_NAMESPACE}" -l "executor=k6-custom-executor" -o json 2>/dev/null \
        | jq -r --arg testName "$testName" '
            .items[]
            | select(.metadata.labels["test-name"] != null
                     and (.metadata.labels["test-name"] | test("^(test-" + $testName + ".*)$")))
            | .metadata.name
          ' \
        | head -n1
    )"; then
      [[ -n "$out" ]]
    else
      false
    fi
  }; do
    log "waiting for test ${testName} to start"
    sleep 30
  done

  job="$out"
  log "Found job ${job} for test ${testName}"

  local hpaName
  local maxReplicas
  hpaName="$(resolveHpaName "${testName}" "${targetNamespace}")" || true
  maxReplicas="$(getHpaMaxReplicas "${targetNamespace}" "${hpaName}")" || true
  if [[ -n "${maxReplicas}" ]]; then
    scaleHpaMin "${targetNamespace}" "${hpaName}" "${maxReplicas}"
  fi

  until kubectl wait -n "${TEST_KUBE_NAMESPACE}" --for=condition=complete "job/${job}" --timeout=10m > /dev/null 2>&1; do
    log "Waiting for job ${job} to complete for test ${testName}"
    sleep 1
  done

  until kubectl get job -n "${TEST_KUBE_NAMESPACE}" "${job}-scraper" >/dev/null 2>&1; do
    log "Waiting for scraper"
    sleep 1
  done

  until kubectl wait -n "${TEST_KUBE_NAMESPACE}" --for=condition=complete "job/${job}-scraper" --timeout=10m > /dev/null 2>&1; do
    log "Waiting for scraper job to complete"
    sleep 1
  done

  log "downloading artifacts for job ${job}"
  until {
    rm -f artifacts/report.md 2>/dev/null || true
    kubectl testkube download artifacts "${job}"  >/dev/null 2>&1
    [[ -s artifacts/report.md ]]
  }; do
    log "Waiting for artifacts to be available"
    sleep 5
  done

  cat artifacts/report.md

  scaleHpaMin "${targetNamespace}" "${hpaName}"
}

if [[ -z "${K8S_SOURCE_CLUSTER_CONTEXT}" || -z "${K8S_TARGET_CLUSTER_CONTEXT}" ]]; then
  log "K8S_SOURCE_CLUSTER_CONTEXT and K8S_TARGET_CLUSTER_CONTEXT are required"
  exit 1
elif ! contextExists "${K8S_SOURCE_CLUSTER_CONTEXT}"; then
  log "context ${K8S_SOURCE_CLUSTER_CONTEXT} doesn't exist"
  exit 1
elif ! contextExists "${K8S_TARGET_CLUSTER_CONTEXT}"; then
  log "context ${K8S_TARGET_CLUSTER_CONTEXT} doesn't exist"
  exit 1
fi

snapshotSource
restoreTarget
deleteSnapshots

if [[ "${WAIT_FOR_K6}" == "true" ]]; then
  log "Awaiting k6 results"
  for ns in "${TEST_KUBE_TARGET_NAMESPACE[@]}"; do
    if kubectl get helmrelease -n "${ns}" "${HELM_RELEASE_NAME}" >/dev/null 2>&1; then
      log "Suspending HelmRelease ${HELM_RELEASE_NAME} in namespace ${ns}"
      flux suspend helmrelease -n "${ns}" "${HELM_RELEASE_NAME}"
    fi
    waitForK6PodExecution "rest" "${ns}"
    waitForK6PodExecution "rest-java" "${ns}"
    waitForK6PodExecution "web3" "${ns}"
  done
  log "K6 tests completed"
  teardownResources
fi
