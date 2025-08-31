#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils.sh
source ./input-utils.sh
source ./snapshot-utils.sh

KEEP_SNAPSHOTS="true"
K8S_SOURCE_CLUSTER_CONTEXT=${K8S_SOURCE_CLUSTER_CONTEXT:-}
K8S_TARGET_CLUSTER_CONTEXT=${K8S_TARGET_CLUSTER_CONTEXT:-}
DEFAULT_POOL_NAME="${DEFAULT_POOL_NAME:-default-pool}"
DEFAULT_POOL_MAX_PER_ZONE="${DEFAULT_POOL_MAX_PER_ZONE:-5}"

function deleteBackupsFromSource() {
  local lines
  lines="$(
    jq -r '
      to_entries[]
      | .key as $ns
      | .value
      | to_entries[]
      | select(.value != null and .value != "")
      | "\($ns)\t\(.value)"
    ' <<<"${BACKUPS_MAP}"
  )" || {
    log "Failed to parse BACKUPS_MAP"
    return 1
  }

  if [[ -z "${lines}" ]]; then
    log "No backups to delete."
    return 0
  fi

  local namespace name
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
    local backupName status msg
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
  local shardedClusters
  shardedClusters="$(kubectl get sgshardedclusters -A -o json 2>/dev/null | jq -c '.items[]?')"
  if [[ -z "${shardedClusters}" ]]; then
    echo '{}'
    return 1
  fi

  local out='{}'
  while IFS= read -r item; do
    local namespace cluster backup
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

# TODO probably do not need to group by namespace
#TODO update to use the created backup
function getLatestBackup() {
  local backups zfsSnapshots
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

  mapfile -t CITUS_NAMESPACES < <(
        kubectl get sgshardedclusters -A \
          -o jsonpath='{range .items[*]}{.metadata.namespace}{"\n"}{end}'
      )
  DISK_PREFIX="$(getDiskPrefix)"
}

function snapshotSource() {
  changeContext "${K8S_SOURCE_CLUSTER_CONTEXT}"
  if [[ -z "${SNAPSHOT_ID}" ]]; then
    PAUSE_CLUSTER="false"
    BACKUPS_MAP=$(runBackupsForAllNamespaces)
    SOURCE_LATEST_BACKUPS="$(getLatestBackup)"
    SOURCE_BACKUP_PATHS="$(getBackupPaths)"
    SNAPSHOT_ID="$(snapshotCitusDisks "false" "${GCP_SNAPSHOT_PROJECT}")"
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
      to_entries[] as $ns
      | $ns.value
      | to_entries[]
      | "\($ns.key)\t\(.key)\t\( (.value // []) | tojson )"
    ' <<<"${SOURCE_BACKUP_PATHS}" 2>/dev/null
  )" || { log "Failed to parse SOURCE_BACKUP_PATHS JSON"; return 1; }

  if [[ -z "${lines}" ]]; then
    log "No entries to patch."
    return 0
  fi

  local namespace cluster patch pathsJson
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
  patchBackupPaths
  replaceDisks
}

function teardownResources() {
  log "Tearing down resources"
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
