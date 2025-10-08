#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail
shopt -s expand_aliases

function backgroundErrorHandler() {
  log "Background command failure signalled. Exiting..."
  trap - INT
  wait
  exit 1
}

mask() {
  set +x
  if [[ "${GITHUB_ACTIONS:-}" == "true" && -n "${1:-}" ]]; then
    printf '::add-mask::%s\n' "$1"
  fi
}

maskJsonValues() {
  set +x
  local json input
  json="${1:-}"

  [[ -z "$json" ]] && return 0

  while IFS= read -r value; do
    mask "$value"

    if decoded="$(printf '%s' "$value" | base64 -d 2>/dev/null)"; then
      if [[ "$decoded" != *$'\x00'* && "$decoded" == *[[:print:]]* ]]; then
        mask "$decoded"
      fi
    fi
  done < <(jq -r '.. | strings' <<< "$json")
}

trap backgroundErrorHandler INT

function watchInBackground() {
  local -ir pid="$1"
  shift
  "$@" 1>&2 || kill -INT -- -"$pid"
}

function waitUntilOutOfRecovery() {
  local namespace="$1" pod="$2"

  log "Waiting for ${pod} to exit recovery"

  while true; do
    local res
    res="$(kubectl exec -n "${namespace}" "${pod}" -c postgres-util -- \
      psql -U mirror_node -d mirror_node -Atc "select pg_is_in_recovery();" 2>/dev/null || true)"

    case "${res}" in
      f)
        log "${pod} is writable (pg_is_in_recovery = f)"
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

function getCitusClusters() {
  kubectl get sgclusters.stackgres.io -A -o json |
    jq -r '.items|
             map(
               .metadata as $metadata|
               .spec.postgres.version as $pgVersion|
               ((.metadata.labels["stackgres.io/coordinator"] // "false")| test("true")) as $isCoordinator |
               .spec.configurations.patroni.initialConfig.citus.group as $citusGroup|
               .status.podStatuses[]|
                 {
                   citusGroup: $citusGroup,
                   clusterName: $metadata.name,
                   isCoordinator: $isCoordinator,
                   namespace: $metadata.namespace,
                   pgVersion: $pgVersion,
                   podName: .name,
                   pvcName: "\($metadata.name)-data-\(.name)",
                   primary: .primary,
                   shardedClusterName: $metadata.ownerReferences[0].name
                 }
             )'
}

function waitForReplicaToBeInSync() {
  local namespace="${1}"
  local replicaPod="${2}"
  local primaryPod="${3}"

  log "Waiting for '${expectedPrimaryPod}' to catch up"

  while true; do
    lag=$(kubectl exec -n "${namespace}" -c postgres-util "${primaryPod}" -- \
      psql -tAc "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)
                 FROM pg_stat_replication
                 WHERE application_name = '${replicaPod}'" | xargs)

    if [[ "$lag" == "0" ]]; then
      log "Replica '${replicaPod}' has 0-byte lag"
      break
    fi

    if [[ -z "$lag" ]]; then
      log "WAL lag for '${expectedPrimaryPod}' not available. Waiting..."
    else
      log "WAL lag on '${expectedPrimaryPod}': ${lag} bytes. Waiting..."
    fi
    sleep 5
  done
}

function patroniFailoverToFirstPod() {
  local namespace="${1}"

  local clustersInNamespace groupCount
  clustersInNamespace=$(getCitusClusters | jq -c --arg ns "${namespace}" '
    map(select(.namespace == $ns)) |
    group_by(.citusGroup)')
  groupCount=$(echo "${clustersInNamespace}" | jq length)

  for ((i = 0; i < groupCount; i++)); do
    local group citusGroup clusterName expectedPrimaryPod patroniCluster currentPrimaryPod

    group=$(echo "${clustersInNamespace}" | jq -c ".[$i]")
    citusGroup=$(echo "${group}" | jq -r ".[0].citusGroup")
    clusterName=$(echo "${group}" | jq -r ".[0].clusterName")
    expectedPrimaryPod="${clusterName}-0"

    if ! kubectl get pod "${expectedPrimaryPod}" -n "${namespace}" &>/dev/null; then
      log "Skipping failover for group ${citusGroup} pod '${expectedPrimaryPod}' not found."
      continue
    fi

    patroniCluster=$(kubectl exec -n "${namespace}" -c patroni "${expectedPrimaryPod}" -- \
    patronictl list --group "${citusGroup}" --format json)
    currentPrimaryPod=$(echo "${patroniCluster}" | jq -r 'map(select(.Role == "Leader")) | .[0].Member'| xargs)

    if (kubectl exec -n "${namespace}" "${expectedPrimaryPod}" -c patroni -- patronictl list --format=json |
         jq --arg primary "${expectedPrimaryPod}" -e '
         .[] | select(.State == "stopped" and .Member == $primary)') &>/dev/null; then
      log "Primary pod '${expectedPrimaryPod}' is stopped. Skipping failover."
      continue
    fi

    if (kubectl exec -n "${namespace}" "${expectedPrimaryPod}" -c patroni -- \
                  patronictl show-config | \
                  yq eval '.pause' -e | grep -q true) &>/dev/null; then
      log "Cluster '${clusterName}' is paused. Skipping failover."
      continue
    fi

    if [[ "${currentPrimaryPod}" != "${expectedPrimaryPod}" ]]; then
      log "Failover required: '${expectedPrimaryPod}' is not primary"
      if [[ -z "${currentPrimaryPod}" || "${currentPrimaryPod}" == "null" ]]; then
        log "No current primary found for group ${citusGroup}"
      else
        local expectedGroupPodCount
        expectedGroupPodCount=$(echo "${group}" | jq length)
        if [[ "${expectedGroupPodCount}" -gt 1 ]]; then
          waitForReplicaToBeInSync "${namespace}" "${expectedPrimaryPod}" "${currentPrimaryPod}"
        fi
      fi

      log "Setting primary ${expectedPrimaryPod} with failover"
      kubectl exec -n "${namespace}" -c patroni "${expectedPrimaryPod}" -- \
        patronictl failover --group "${citusGroup}" --candidate "${expectedPrimaryPod}" --force

      while true; do
        log "Waiting for failover of group ${citusGroup} to '${expectedPrimaryPod}'..."
        sleep 5
        patroniCluster=$(kubectl exec -n "${namespace}" -c patroni "${expectedPrimaryPod}" -- \
            patronictl list --group "${citusGroup}" --format json)
        local newPrimary
        newPrimary=$(echo "${patroniCluster}" | jq -r 'map(select(.Role == "Leader")) | .[0].Member'| xargs)

        if [[ "${newPrimary}" == "${expectedPrimaryPod}" ]]; then
          log "Failover successful: '${expectedPrimaryPod}' is now primary."
          break
        fi
      done

      while true; do
        log "Waiting for SGCluster '${clusterName}' to show '${expectedPrimaryPod}' as primary..."
        sleep 5
        local sgPrimary
        sgPrimary=$(kubectl get sgcluster "${clusterName}" -n "${namespace}" -o json |
          jq -r '.status.podStatuses[] | select(.primary == true) | .name')

        if [[ "${sgPrimary}" == "${expectedPrimaryPod}" ]]; then
          log "SGCluster '${clusterName}' reflects new primary: '${expectedPrimaryPod}'"
          break
        fi
      done
    else
      log "No failover needed: '${expectedPrimaryPod}' is already primary for group ${citusGroup}."
    fi
  done
}

function checkCitusMetadataSyncStatus() {
  TIMEOUT_SECONDS=600
  local namespace="${1}"

  deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    synced=$(kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -U postgres -d mirror_node -P format=unaligned -t -c "select bool_and(metadatasynced) from pg_dist_node")
    if [[ "${synced}" == "t" ]]; then
      log "Citus metadata is synced"
      return 0
    else
      log "Citus metadata is not synced. Waiting..."
      sleep 5
    fi
  done

  log "Citus metadata did not sync in ${TIMEOUT_SECONDS} seconds"
  exit 1
}

function doContinue() {
  if [[ "${AUTO_CONFIRM}" == "true" ]]; then
    log "Skipping confirm"
    return 0
  fi
  while true; do
    read -p "Continue? (Y/N): " confirm && [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]] && return ||
      { [[ -n "$confirm" ]] && exit 1 || true; }
  done
}

function log() {
  echo "$(date -u +"%Y-%m-%dT%H:%M:%SZ") ${1}" >& 2
}

function readUserInput() {
  read -p "${1}" input
  echo "${input}"
}

function scaleDeployment() {
  local namespace="${1}"
  local replicas="${2}"
  local deploymentLabel="${3}"

  if [[ "${replicas}" -gt 0 ]]; then # scale up
    kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
    sleep 5
    log "Waiting for pods with label ${deploymentLabel} to be ready"
    kubectl wait --for=condition=Ready pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
  else # scale down
    local deploymentPods
    deploymentPods=$(kubectl get pods -n "${namespace}" -l "${deploymentLabel}" -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${deploymentPods}" ]]; then
      log "No pods found for deployment ${deploymentLabel} in namespace ${namespace}"
      return
    else
      log "Removing pods ${deploymentPods} in ${namespace}"
      doContinue
      kubectl scale deployment -n "${namespace}" -l "${deploymentLabel}" --replicas="${replicas}"
      log "Waiting for pods with label ${deploymentLabel} to be deleted"
      kubectl wait --for=delete pod -n "${namespace}" -l "${deploymentLabel}" --timeout=-1s
    fi
  fi
}

function unrouteTraffic() {
  local namespace="${1}"
  if [[ "${AUTO_UNROUTE}" == "true" ]]; then
    log "Unrouting traffic to cluster in namespace ${namespace}"
    if kubectl get helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" >/dev/null; then
      log "Suspending helm release ${HELM_RELEASE_NAME} in namespace ${namespace}"
      doContinue
      flux suspend helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}"
    else
      log "No helm release found in namespace ${namespace}. Skipping suspend"
    fi

    scaleDeployment "${namespace}" 0 "app.kubernetes.io/component=monitor"
  fi
  scaleDeployment "${namespace}" 0 "app.kubernetes.io/component=importer"
}

function runTestQueries() {
  local namespace="${1}"

  until \
    kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- \
      psql -P pager=off -U mirror_rest -d mirror_node -c "select * from transaction limit 10" \
    && \
    kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- \
      psql -P pager=off -U mirror_node -d mirror_node -c "select * from transaction limit 10"
  do
    log "queries not succeeding. Retrying..."
    sleep 5
  done

  doContinue
}

function waitForRecordStreamSync() {
  local namespace="${1}"

  while true; do
    local status
    status=$(kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -q --csv -t -U mirror_rest -d mirror_node -c "select $(date +%s) - (max(consensus_end) / 1000000000) from record_file" | tail -n 1)
    if [[ "${status}" -lt 10 ]]; then
      log "Importer is caught up with the source"
      break
    else
      log "Waiting for importer to catch up with the source. Current lag: ${status} seconds"
      sleep 10
    fi
  done
}

function routeTraffic() {
  local namespace="${1}"

  checkCitusMetadataSyncStatus "${namespace}"
  runTestQueries "${namespace}"
  scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=importer"
  waitForRecordStreamSync "${namespace}"

  if [[ "${AUTO_UNROUTE}" == "true" ]]; then
    if kubectl get helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" >/dev/null; then
      log "Resuming helm release ${HELM_RELEASE_NAME} in namespace ${namespace}.
          Be sure to configure values.yaml with any changes before continuing"
      doContinue
      flux resume helmrelease -n "${namespace}" "${HELM_RELEASE_NAME}" --timeout 30m
    else
      log "No helm release found in namespace ${namespace}. Skipping suspend"
    fi
    scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=monitor"
  fi
}

function pausePatroni() {
  local namespace="${1}"

  log "Pausing patroni for all clusters in namespace ${namespace}"

  local clusterNames
  clusterNames=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' \
    -o json | jq -r '
      .items
      | map(.metadata.labels["stackgres.io/cluster-name"])
      | unique[]
    ')

  for cluster in ${clusterNames}; do
    local pod
    pod=$(kubectl get pods -n "${namespace}" \
      -l "stackgres.io/cluster-name=${cluster}" \
      -o json | jq -r '
        .items
        | sort_by(.metadata.name)
        | .[0].metadata.name
      ')

    if [[ -z "${pod}" ]]; then
      log "No pod found for cluster ${cluster} in namespace ${namespace}. Skipping"
      continue
    fi

    if (kubectl exec -n "${namespace}" "${pod}" -c patroni -- \
           patronictl show-config | \
           yq eval '.pause' -e | grep -q true) &>/dev/null; then
      log "Cluster '${cluster}' is already paused. Skipping pause."
    else
      log "Pausing Patroni cluster '${cluster}' via pod ${pod}"
      kubectl exec -n "${namespace}" "${pod}" -c patroni -- patronictl pause --wait
    fi
  done
}

function shutdownPostgres() {
  local namespace="${1}"
  local podNames
  podNames=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' \
    -o json | jq -r '
      .items
      | sort_by(.metadata.name)
      | .[].metadata.name
    ')

  log "Stopping PostgreSQL cleanly for all clusters in namespace ${namespace}"

  for pod in ${podNames}; do
    local dataDir
    dataDir=$(kubectl exec -n "${namespace}" "${pod}" -c patroni -- bash -c 'echo $PATRONI_POSTGRESQL_DATA_DIR')
    until ! kubectl exec -n "${namespace}" "${pod}" -c patroni -- test -f "${dataDir}/postmaster.pid" &>/dev/null;
    do
      if kubectl exec -n "${namespace}" "${pod}" -c patroni -- pg_isready -q; then
        log "PostgreSQL is ready in pod ${pod}, attempting shutdown"
        if ! kubectl exec -n "${namespace}" "${pod}" -c patroni -- pg_ctl stop -t 600 -m fast -D "${dataDir}"; then
          log "PostgreSQL shutdown command failed on pod ${pod} will retry"
        fi
      else
        log "PostgreSQL not ready yet in pod ${pod}, waiting before next shutdown attempt"
      fi
      sleep 2
    done
    log "PostgreSQL shutdown complete in pod ${pod}"
  done
}

function cleanShutdown() {
  local namespace="${1}"

  log "Stopping PostgreSQL cleanly for all clusters in namespace ${namespace}"
  pausePatroni "${namespace}"
  shutdownPostgres "${namespace}"
}

function checkPauseAnnotation() {
  local namespace="${1}"

  log "Ensuring all sgclusters in namespace '${namespace}' are annotated with reconciliation-pause=true"

  local clusters
  mapfile -t clusters < <(kubectl get sgcluster.stackgres.io -n "${namespace}" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')

  for clusterName in "${clusters[@]}"; do
    while true; do
      local pauseAnnotation
      pauseAnnotation=$(kubectl get sgcluster.stackgres.io "${clusterName}" -n "${namespace}" -o jsonpath='{.metadata.annotations.stackgres\.io/reconciliation-pause}')
      if [[ "${pauseAnnotation}" == "true" ]]; then
        log "sgcluster ${clusterName} is annotated with reconciliation-pause=true"
        break
      fi

      log "Annotating sgcluster ${clusterName} with reconciliation-pause=true"
      kubectl annotate sgcluster.stackgres.io "${clusterName}" -n "${namespace}" stackgres.io/reconciliation-pause="true" --overwrite
      sleep 2
     done
    done
}

function scaleDownCitusPods() {
  local namespace="${1}"

  checkPauseAnnotation "${namespace}"
  kubectl scale sts -n "${namespace}" -l 'stackgres.io/cluster=true' --replicas=0
  log "Waiting for citus pods to terminate"

  if ! kubectl wait --for=delete pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=180s; then
    log "Timeout waiting for citus pods to delete"

    local remainingPods
    mapfile -t remainingPods < <(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')

    for pod in "${remainingPods[@]}"; do
      log "Force deleting pod ${pod}"
      kubectl delete pod "${pod}" -n "${namespace}" --grace-period=0 --force
    done
  fi
}

function pauseCitus() {
  local namespace="${1}"
  local skipCleanShutdown="${2:-false}"

  local citusPods
  citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    log "Citus is not currently running"
  else
    log "Removing pods (${citusPods}) in ${namespace}"
    doContinue
    checkPauseAnnotation "${namespace}"
    if [[ "${skipCleanShutdown}" != "true" ]]; then
      patroniFailoverToFirstPod "${namespace}"
      cleanShutdown "${namespace}"
    fi
    scaleDownCitusPods "${namespace}"
  fi
}

function waitForPatroniMasters() {
  local namespace="${1}"
  local expectedTotal masterPods
  expectedTotal=$(($(kubectl get sgshardedclusters -n "${namespace}" -o jsonpath='{.items[0].spec.shards.clusters}') + 1))
  while true; do
    mapfile -t masterPods < <(kubectl get pods -n "${namespace}" -l "${STACKGRES_MASTER_LABELS}" -o name)
    if [[ "${#masterPods[@]}" -eq "${expectedTotal}" ]]; then
      break;
    else
      log "Expected ${expectedTotal} master pods and found only ${#masterPods[@]} in namespace ${namespace}. retrying"
    fi
  done

  local patroniPod="${masterPods[0]}"

  while [[ "$(kubectl exec -n "${namespace}" "${patroniPod}" -c patroni -- patronictl list --format=json |
    jq -r --arg PATRONI_MASTER_ROLE "${PATRONI_MASTER_ROLE}" \
      --arg EXPECTED_MASTERS "${expectedTotal}" \
      'map(select(.Role == $PATRONI_MASTER_ROLE)) |
                     length == ($EXPECTED_MASTERS | tonumber) and all(.State == "running")')" != "true" ]]; do
    log "Waiting for Patroni to be ready"
    sleep 5
  done

  log "All Patroni masters are ready"
}

function resumePatroni() {
  local namespace="${1}"
  local cluster="${2}"
  local pod="${cluster}-0"

  log "Resuming Patroni cluster '${cluster}' via pod ${pod}"

  until kubectl exec -n "${namespace}" "${pod}" -c patroni -- pg_isready &>/dev/null; do
    log "PostgreSQL is not yet ready in pod ${pod}"

    if (kubectl exec -n "${namespace}" "${pod}" -c patroni -- \
         patronictl show-config | yq eval '.pause' -e | grep -q true) &>/dev/null; then
      log "Cluster is paused in pod ${pod}. Attempting to resume..."
      if kubectl exec -n "${namespace}" "${pod}" -c patroni -- \
           patronictl resume --wait &>/dev/null; then
        log "Successfully resumed cluster in pod ${pod}"
      else
        log "Resume failed in pod ${pod}. Retrying"
      fi
    fi
    sleep 2
  done

  log "Successfully resumed cluster in pod ${pod}"
}

function waitForStatefulSetPodsStarted() {
  local namespace="${1}"
  local sts="${2}"

  while true; do
    log "Waiting for pods from StatefulSet ${sts} in namespace ${namespace} to be started"
    local startedCount
    startedCount=$(kubectl get pods -n "${namespace}" -l "statefulset.kubernetes.io/pod-name" \
      -o json | jq '[.items[] | select(.metadata.ownerReferences[]?.name == "'"${sts}"'" and .status.containerStatuses[]?.started == true)] | length')

    if [[ "${startedCount}" -ge 1 ]]; then
      echo "Pods for sts ${sts} are started"
      break
    fi
    sleep 5
  done
}

function unpauseCitus() {
  local namespace="${1}"
  local reinitializeCitus="${2:-false}"
  local citusPods
  citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')

  if [[ -z "${citusPods}" ]]; then
    log "Starting citus cluster in namespace ${namespace}"
    if [[ "${reinitializeCitus}" == "true" ]]; then
      kubectl annotate endpoints -n "${namespace}" -l 'stackgres.io/cluster=true' initialize- --overwrite
    fi
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause- --overwrite

    log "Waiting for all SGCluster StatefulSets to be created"
    while ! kubectl get sgshardedclusters -n "${namespace}" -o jsonpath='{.items[0].spec.shards.clusters}' >/dev/null 2>&1; do
      sleep 1
    done

    expectedTotal=$(($(kubectl get sgshardedclusters -n "${namespace}" -o jsonpath='{.items[0].spec.shards.clusters}') + 1))
    while [[ "$(kubectl get sts -n "${namespace}" -l 'app=StackGresCluster' -o name | wc -l)" -ne "${expectedTotal}" ]]; do
      sleep 1
    done

    log "Waiting for all StackGresCluster pods to be ready"
    for sts in $(kubectl get sts -n "${namespace}" -l 'app=StackGresCluster' -o name); do
      waitForStatefulSetPodsStarted "${namespace}" "${sts##*/}"
      resumePatroni "${namespace}" "${sts##*/}"
      expected=$(kubectl get "${sts}" -n "${namespace}" -o jsonpath='{.spec.replicas}')
      log "Waiting for ${expected} replicas in ${sts}"
      kubectl wait --for=jsonpath='{.status.readyReplicas}'=${expected} "${sts}" -n "${namespace}" --timeout=-1s
    done

    patroniFailoverToFirstPod "${namespace}" # Ensure there is a marked primary
    waitForPatroniMasters "${namespace}"
  else
    log "Citus is already running in namespace ${namespace}. Skipping"
  fi
}

function getDiskPrefix() {
  local diskPrefix
  diskPrefix=$(kubectl_common get daemonsets -l 'app=zfs-init' -o json |
    jq -r '.items[0].spec.template.spec.initContainers[0].env[] | select (.name == "DISK_PREFIX") | .value')

  if [[ -z "${diskPrefix}" ]]; then
    log "DISK_PREFIX can not be empty. Exiting"
    exit 1
  fi

  echo "${diskPrefix}"
}

function getZFSVolumes() {
  kubectl get pv -o json |
    jq -r --arg CITUS_CLUSTERS "$(getCitusClusters)" \
      '.items|
         map(select(.metadata.annotations."pv.kubernetes.io/provisioned-by"=="zfs.csi.openebs.io" and
                    .status.phase == "Bound")|
            (.spec.claimRef.name) as $pvcName |
            (.spec.claimRef.namespace) as $pvcNamespace |
            {
              namespace: ($pvcNamespace),
              volumeName: (.metadata.name),
              pvcName: ($pvcName),
              pvcSize: (.spec.capacity.storage),
              nodeId: (.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0]),
              citusCluster: ($CITUS_CLUSTERS | fromjson | map(select(.pvcName == $pvcName and
                                                              .namespace == $pvcNamespace))|first)
            }
        )'
}

function resizeCitusNodePools() {
  local numNodes="${1}"

  log "Discovering node pools with label 'citus-role'"

  local citusPools=()
  mapfile -t citusPools < <(gcloud container node-pools list \
    --project="${GCP_TARGET_PROJECT}" \
    --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
    --cluster="${GCP_K8S_TARGET_CLUSTER_NAME}" \
    --format="value(name)" \
    --filter="config.labels.citus-role:*")

  if [[ ${#citusPools[@]} -eq 0 ]]; then
    log "No citus-role node pools found"
    return 1
  fi

  for pool in "${citusPools[@]}"; do
    log "Scaling pool ${pool} to ${numNodes} nodes"

    gcloud container clusters resize "${GCP_K8S_TARGET_CLUSTER_NAME}" \
      --node-pool="${pool}" \
      --num-nodes="${numNodes}" \
      --location="${GCP_K8S_TARGET_CLUSTER_REGION}" \
      --project="${GCP_TARGET_PROJECT}" --quiet &
  done

  log "Waiting for resize operations to complete"
  wait

  if [[ "${numNodes}" -gt 0 ]]; then
    kubectl wait --for=condition=Ready node -l'citus-role' --timeout=-1s
  else
    kubectl wait --for=delete node -l'citus-role' --timeout=-1s
  fi
}

function updateStackgresCreds() {
  local cluster="${1}"
  local namespace="${2}"
  local sgPasswords=$(kubectl get secret -n "${namespace}" "${cluster}" -o json |
    jq -r '.data')
  maskJsonValues "${sgPasswords}"

  local superuserUsername=$(echo "${sgPasswords}" | jq -r '.["superuser-username"]' | base64 -d)
  local superuserPassword=$(echo "${sgPasswords}" | jq -r '.["superuser-password"]'| base64 -d)
  local replicationUsername=$(echo "${sgPasswords}" | jq -r '.["replication-username"]'| base64 -d)
  local replicationPassword=$(echo "${sgPasswords}" | jq -r '.["replication-password"]'| base64 -d)
  local authenticatorUsername=$(echo "${sgPasswords}" | jq -r '.["authenticator-username"]'| base64 -d)
  local authenticatorPassword=$(echo "${sgPasswords}" | jq -r '.["authenticator-password"]'| base64 -d)

  # Mirror Node Passwords
  local mirrorNodePasswords=$(kubectl get secret -n "${namespace}" "${HELM_RELEASE_NAME}-passwords" -o json |
    jq -r '.data')
  maskJsonValues "${mirrorNodePasswords}"

  local graphqlUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRAPHQL_DB_USERNAME'| base64 -d)
  local graphqlPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRAPHQL_DB_PASSWORD'| base64 -d)
  local grpcUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRPC_DB_USERNAME'| base64 -d)
  local grpcPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRPC_DB_PASSWORD'| base64 -d)
  local importerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_USERNAME'| base64 -d)
  local importerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_PASSWORD'| base64 -d)
  local ownerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_OWNER'| base64 -d)
  local ownerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_OWNERPASSWORD'| base64 -d)
  local restUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_REST_DB_USERNAME'| base64 -d)
  local restPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_REST_DB_PASSWORD'| base64 -d)
  local restJavaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_RESTJAVA_DB_USERNAME'| base64 -d)
  local restJavaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_RESTJAVA_DB_PASSWORD'| base64 -d)
  local rosettaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_ROSETTA_DB_USERNAME'| base64 -d)
  local rosettaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_ROSETTA_DB_PASSWORD'| base64 -d)
  local web3Username=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_WEB3_DB_USERNAME'| base64 -d)
  local web3Password=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_WEB3_DB_PASSWORD'| base64 -d)
  local dbName=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_NAME'| base64 -d)

  local sql=$(
    cat <<EOF
alter user ${superuserUsername} with password '${superuserPassword}';
alter user ${graphqlUsername} with password '${graphqlPassword}';
alter user ${grpcUsername} with password '${grpcPassword}';
alter user ${importerUsername} with password '${importerPassword}';
alter user ${ownerUsername} with password '${ownerPassword}';
alter user ${restUsername} with password '${restPassword}';
alter user ${restJavaUsername} with password '${restJavaPassword}';
alter user ${rosettaUsername} with password '${rosettaPassword}';
alter user ${web3Username} with password '${web3Password}';
alter user ${replicationUsername} with password '${replicationPassword}';
alter user ${authenticatorUsername} with password '${authenticatorPassword}';

\c ${dbName}
insert into pg_dist_authinfo(nodeid, rolename, authinfo)
  values (0, '${superuserUsername}', 'password=${superuserPassword}'),
         (0, '${graphqlUsername}', 'password=${graphqlPassword}'),
         (0, '${grpcUsername}', 'password=${grpcPassword}'),
         (0, '${importerUsername}', 'password=${importerPassword}'),
         (0, '${ownerUsername}', 'password=${ownerPassword}'),
         (0, '${restUsername}', 'password=${restPassword}'),
         (0, '${restJavaUsername}', 'password=${restJavaPassword}'),
         (0, '${rosettaUsername}', 'password=${rosettaPassword}'),
         (0, '${web3Username}', 'password=${web3Password}') on conflict (nodeid, rolename)
  do
      update set authinfo = excluded.authinfo;
EOF
  )

  log "Fixing passwords and pg_dist_authinfo for all pods in the cluster"
  for pod in $(kubectl get pods -n "${namespace}" -l "${STACKGRES_MASTER_LABELS}" -o name); do
    waitUntilOutOfRecovery "${namespace}" "${pod}"
    log "Updating passwords and pg_dist_authinfo for ${pod}"
    if ! kubectl exec -n "${namespace}" -i "${pod}" -c postgres-util -- \
       psql -v ON_ERROR_STOP=1 -U "${superuserUsername}" -f - <<< "${sql}"; then
       log "Failed to update passwords in pod ${pod}"
       exit 1
     fi
  done
}

function pauseClustersIfNeeded() {
  if [[ "${PAUSE_CLUSTER}" == "true" ]]; then
    for namespace in "${CITUS_NAMESPACES[@]}"; do
      unrouteTraffic "${namespace}"
      pauseCitus "${namespace}"
    done
  fi
}

function resumeClustersIfNeeded() {
  if [[ "${PAUSE_CLUSTER}" == "true" ]]; then
    for namespace in "${CITUS_NAMESPACES[@]}"; do
      log "Resuming Citus in namespace ${namespace}"
      unpauseCitus "${namespace}" true
      routeTraffic "${namespace}"
    done
  fi
}

function waitForZfsPodsReady() {
  log "Waiting for zfs pods to be ready"
  kubectl_common wait --for=condition=Ready pod -l 'component=openebs-zfs-node' --timeout=-1s
}

function buildNodeIdToPodMap() {
  local zfsNodePods zfsNodes
  zfsNodePods=$(kubectl get pods -A -o wide -o json -l 'component=openebs-zfs-node' |
    jq -r '.items | map({node: (.spec.nodeName), podName: (.metadata.name)})')
  zfsNodes=$(kubectl get zfsnodes.zfs.openebs.io -A -o json |
    jq -r '.items | map({nodeId: .metadata.name, node: .metadata.ownerReferences[0].name})')

  echo -e "${zfsNodePods}\n${zfsNodes}" | jq -s '
    .[0] as $pods |
    .[1] | map(.node as $nodeName |
      { (.nodeId): ($pods[] | select(.node == $nodeName).podName) }
    ) | add'
}

function setCitusNamespaces() {
  mapfile -t CITUS_NAMESPACES < <(
      kubectl get sgshardedclusters -A \
        -o jsonpath='{range .items[*]}{.metadata.namespace}{"\n"}{end}'
    )
}

AUTO_CONFIRM="${AUTO_CONFIRM:-false}"
AUTO_UNROUTE="${AUTO_UNROUTE:-true}"
CITUS_NAMESPACES=
COMMON_NAMESPACE="${COMMON_NAMESPACE:-common}"
DISK_PREFIX=
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-mirror}"
PATRONI_MASTER_ROLE="${PATRONI_MASTER_ROLE:-Leader}"
PAUSE_CLUSTER="${PAUSE_CLUSTER:-true}"
STACKGRES_MASTER_LABELS="${STACKGRES_MASTER_LABELS:-app=StackGresCluster,role=master}"
ZFS_POOL_NAME="${ZFS_POOL_NAME:-zfspv-pool}"

alias kubectl_common="kubectl -n ${COMMON_NAMESPACE}"
