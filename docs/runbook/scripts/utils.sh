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

trap backgroundErrorHandler INT

function watchInBackground() {
  local -ir pid="$1"
  shift
  "$@" || kill -INT -- -"$pid"
}

function waitForReplicaToBeInSync() {
  local namespace="${1}"
  local replicaPod="${2}"
  local primaryPod="${3}"

  log "Waiting for '${expectedPrimaryPod}' to catch up"

  local lag_timeout=300
  local lag_start_time=$SECONDS

  while true; do
    lag=$(kubectl exec -n "${namespace}" -c postgres-util "${primaryPod}" -- \
      psql -tAc "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)
                 FROM pg_stat_replication
                 WHERE application_name = '${replicaPod}'")

    lag=$(echo "$lag" | xargs)

    if [[ "$lag" == "0" ]]; then
      log "Replica '${replicaPod}' has 0-byte lag"
      break
    fi

    if [[ -z "$lag" ]]; then
      log "WAL lag for '${expectedPrimaryPod}' not available. Waiting..."
    else
      log "WAL lag on '${expectedPrimaryPod}': ${lag} bytes. Waiting..."
    fi

    if ((SECONDS - lag_start_time > lag_timeout)); then
      log "Timed out waiting for WAL lag = 0 on '${expectedPrimaryPod}' (current lag is ${lag:-unknown})"
      exit 1
    fi

    sleep 5
  done
}

function patroniFailoverToFirstPod() {
  local namespace="${1}"

  local clustersInNamespace
  clustersInNamespace=$(echo "${CITUS_CLUSTERS}" | jq -c --arg ns "${namespace}" '
    map(select(.namespace == $ns)) |
    group_by(.citusGroup)')

  local groupCount
  groupCount=$(echo "${clustersInNamespace}" | jq length)

  for ((i = 0; i < groupCount; i++)); do
    local group
    group=$(echo "${clustersInNamespace}" | jq -c ".[$i]")

    local citusGroup
    citusGroup=$(echo "${group}" | jq -r ".[0].citusGroup")

    local currentPrimaryPod
    currentPrimaryPod=$(echo "${group}" | jq -r 'map(select(.primary == true)) | .[0].podName')

    if [[ -z "${currentPrimaryPod}" || "${currentPrimaryPod}" == "null" ]]; then
      log "No primary pod found for Citus group '${citusGroup}' in namespace ${namespace}. Exiting."
      exit 1
    fi

    local podPrefix
    podPrefix=$(echo "${currentPrimaryPod}" | sed -E 's/-[0-9]+$//')
    local expectedPrimaryPod="${podPrefix}-0"

    if [[ "${currentPrimaryPod}" != "${expectedPrimaryPod}" ]]; then
      log "Failover required: '${expectedPrimaryPod}' is not primary"
      waitForReplicaToBeInSync "${namespace}" "${expectedPrimaryPod}" "${currentPrimaryPod}"

      kubectl exec -n "${namespace}" -c patroni "${currentPrimaryPod}" -- \
        patronictl failover --group "${citusGroup}" --candidate "${expectedPrimaryPod}" --force

      # Wait for failover to complete
      local timeout=600
      local start_time=$SECONDS
      while true; do
        log "Waiting for failover of group ${citusGroup} to '${expectedPrimaryPod}'..."
        sleep 5

        CITUS_CLUSTERS=$(getCitusClusters)

        local newPrimary
        newPrimary=$(echo "${CITUS_CLUSTERS}" | jq -r --arg ns "${namespace}" --argjson group "${citusGroup}" '
          map(select(.namespace == $ns and .citusGroup == $group and .primary == true)) | .[0].podName')

        if [[ "${newPrimary}" == "${expectedPrimaryPod}" ]]; then
          log "Failover successful: '${expectedPrimaryPod}' is now primary."
          break
        fi

        if ((SECONDS - start_time > timeout)); then
          log "Timeout during failover to '${expectedPrimaryPod}' in group '${citusGroup}' (namespace ${namespace})"
          exit 1
        fi
      done

      # Verify SGCluster reflects new primary pod
      local sgCheckTimeout=60
      local sgCheckStart=$SECONDS
      local sgClusterName
      sgClusterName=$(echo "${group}" | jq -r ".[0].clusterName")

      while true; do
        log "Waiting for SGCluster '${sgClusterName}' to show '${expectedPrimaryPod}' as primary..."
        sleep 5
        local sgPrimary
        sgPrimary=$(kubectl get sgcluster "${sgClusterName}" -n "${namespace}" -o json |
          jq -r '.status.podStatuses[] | select(.primary == true) | .name')

        if [[ "${sgPrimary}" == "${expectedPrimaryPod}" ]]; then
          log "SGCluster '${sgClusterName}' reflects new primary: '${expectedPrimaryPod}'"
          break
        fi

        if ((SECONDS - sgCheckStart > sgCheckTimeout)); then
          log "Timed out waiting for SGCluster '${sgClusterName}' to reflect new primary '${expectedPrimaryPod}'."
          exit 1
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
  while true; do
    read -p "Continue? (Y/N): " confirm && [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]] && return ||
      { [[ -n "$confirm" ]] && exit 1 || true; }
  done
}

function log() {
  echo "$(date -u +"%Y-%m-%dT%H:%M:%SZ") ${1}"
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
    local deploymentPods=$(kubectl get pods -n "${namespace}" -l "${deploymentLabel}" -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${deploymentPods}" ]]; then
      log "No pods found for deployment ${deploymentLabel} in namespace ${namespace}"
      return
    else
      log "Removing pods ${deploymentPods} in ${namespace} for ${CURRENT_CONTEXT}"
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

function routeTraffic() {
  local namespace="${1}"

  checkCitusMetadataSyncStatus "${namespace}"

  log "Running test queries"
  kubectl exec -it -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -P pager=off -U mirror_rest -d mirror_node -c "select * from transaction limit 10"
  kubectl exec -it -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -P pager=off -U mirror_node -d mirror_node -c "select * from transaction limit 10"
  doContinue
  scaleDeployment "${namespace}" 1 "app.kubernetes.io/component=importer"
  while true; do
    local statusQuery="select $(date +%s) - (max(consensus_end) / 1000000000) from record_file"
    local status=$(kubectl exec -n "${namespace}" "${HELM_RELEASE_NAME}-citus-coord-0" -c postgres-util -- psql -q --csv -t -U mirror_rest -d mirror_node -c "select $(date +%s) - (max(consensus_end) / 1000000000) from record_file" | tail -n 1)
    if [[ "${status}" -lt 10 ]]; then
      log "Importer is caught up with the source"
      break
    else
      log "Waiting for importer to catch up with the source. Current lag: ${status} seconds"
      sleep 10
    fi
  done
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

function pauseCitus() {
  local namespace="${1}"
  local citusPods
  citusPods=$(kubectl get pods -n "${namespace}" -l 'stackgres.io/cluster=true' -o 'jsonpath={.items[*].metadata.name}')
  if [[ -z "${citusPods}" ]]; then
    log "Citus is not currently running"
  else
    patroniFailoverToFirstPod "${namespace}"
    log "Removing pods (${citusPods}) in ${namespace} for ${CURRENT_CONTEXT}"
    doContinue
    kubectl annotate sgclusters.stackgres.io -n "${namespace}" --all stackgres.io/reconciliation-pause="true" --overwrite
    sleep 5
    kubectl scale sts -n "${namespace}" -l 'stackgres.io/cluster=true' --replicas=0
    log "Waiting for citus pods to terminate"
    kubectl wait --for=delete pod -l 'stackgres.io/cluster=true' -n "${namespace}" --timeout=-1s
  fi
}

function waitForPatroniMasters() {
  local namespace="${1}"
  local masterPods
  masterPods=($(kubectl get pods -n "${namespace}" -l "${STACKGRES_MASTER_LABELS}" -o jsonpath='{.items[*].metadata.name}'))
  local expectedTotal=$(($(kubectl get sgshardedclusters -n "${namespace}" -o jsonpath='{.items[0].spec.shards.clusters}') + 1))

  if [[ "${#masterPods[@]}" -ne "${expectedTotal}" ]]; then
    log "Expected ${expectedTotal} master pods and found only ${#masterPods[@]} in namespace ${namespace}"
    exit 1
  fi

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
      expected=$(kubectl get "${sts}" -n "${namespace}" -o jsonpath='{.spec.replicas}')
      log "Waiting for ${expected} replicas in ${sts}"
      sleep 1 # small pause is sometimes needed between these calls
      kubectl wait --for=jsonpath='{.status.readyReplicas}'=${expected} "${sts}" -n "${namespace}" --timeout=-1s
    done

    while [[ "$(kubectl get pods -n "${namespace}" -l "${STACKGRES_MASTER_LABELS}" -o name | wc -l)" -ne "${expectedTotal}" ]]; do
      log "Waiting for a pod in each cluster to be marked with master role label"
      sleep 1
    done
    waitForPatroniMasters "${namespace}"
  else
    log "Citus is already running in namespace ${namespace}. Skipping"
  fi
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

function getDiskPrefix() {
  DISK_PREFIX=$(kubectl_common get daemonsets -l 'app=zfs-init' -o json |
    jq -r '.items[0].spec.template.spec.initContainers[0].env[] | select (.name == "DISK_PREFIX") | .value')
  if [[ -z "${DISK_PREFIX}" ]]; then
    log "DISK_PREFIX can not be empty. Exiting"
    exit 1
  fi
}

function getZFSVolumes() {
  kubectl get pv -o json |
    jq -r --arg CITUS_CLUSTERS "${CITUS_CLUSTERS}" \
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

  log "Scaling nodepool ${GCP_COORDINATOR_POOL_NAME} and ${GCP_WORKER_POOL_NAME} in cluster ${GCP_K8S_CLUSTER_NAME}
  for project ${GCP_PROJECT} to ${numNodes} nodes per zone"

  if [[ "${numNodes}" -gt 0 ]]; then
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes "${numNodes}" --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    log "Waiting for nodes to be ready"
    wait
    kubectl wait --for=condition=Ready node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=condition=Ready node -l'citus-role=worker' --timeout=-1s
  else
    local coordinatorNodes=$(kubectl get nodes -l'citus-role=coordinator' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${coordinatorNodes}" ]]; then
      log "No coordinator nodes found"
    else
      log "Scaling down coordinator nodes ${coordinatorNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_COORDINATOR_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi

    local workerNodes=$(kubectl get nodes -l'citus-role=worker' -o 'jsonpath={.items[*].metadata.name}')
    if [[ -z "${workerNodes}" ]]; then
      log "No worker nodes found"
    else
      log "Scaling down worker nodes ${workerNodes}"
      gcloud container clusters resize "${GCP_K8S_CLUSTER_NAME}" --node-pool "${GCP_WORKER_POOL_NAME}" --num-nodes 0 --location "${GCP_K8S_CLUSTER_REGION}" --project "${GCP_PROJECT}" --quiet &
    fi
    log "Waiting for nodes to be deleted"
    wait
    kubectl wait --for=delete node -l'citus-role=coordinator' --timeout=-1s
    kubectl wait --for=delete node -l'citus-role=worker' --timeout=-1s
  fi
}

function updateStackgresCreds() {
  local cluster="${1}"
  local namespace="${2}"
  local sgPasswords=$(kubectl get secret -n "${namespace}" "${cluster}" -o json |
    ksd |
    jq -r '.stringData')
  local superuserUsername=$(echo "${sgPasswords}" | jq -r '.["superuser-username"]')
  local superuserPassword=$(echo "${sgPasswords}" | jq -r '.["superuser-password"]')
  local replicationUsername=$(echo "${sgPasswords}" | jq -r '.["replication-username"]')
  local replicationPassword=$(echo "${sgPasswords}" | jq -r '.["replication-password"]')
  local authenticatorUsername=$(echo "${sgPasswords}" | jq -r '.["authenticator-username"]')
  local authenticatorPassword=$(echo "${sgPasswords}" | jq -r '.["authenticator-password"]')

  # Mirror Node Passwords
  local mirrorNodePasswords=$(kubectl get secret -n "${namespace}" "${HELM_RELEASE_NAME}-passwords" -o json |
    ksd |
    jq -r '.stringData')
  local graphqlUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRAPHQL_DB_USERNAME')
  local graphqlPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRAPHQL_DB_PASSWORD')
  local grpcUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRPC_DB_USERNAME')
  local grpcPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_GRPC_DB_PASSWORD')
  local importerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_USERNAME')
  local importerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_PASSWORD')
  local ownerUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_OWNER')
  local ownerPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_OWNERPASSWORD')
  local restUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_REST_DB_USERNAME')
  local restPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_REST_DB_PASSWORD')
  local restJavaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_RESTJAVA_DB_USERNAME')
  local restJavaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_RESTJAVA_DB_PASSWORD')
  local rosettaUsername=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_ROSETTA_DB_USERNAME')
  local rosettaPassword=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_ROSETTA_DB_PASSWORD')
  local web3Username=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_WEB3_DB_USERNAME')
  local web3Password=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_WEB3_DB_PASSWORD')
  local dbName=$(echo "${mirrorNodePasswords}" | jq -r '.HIERO_MIRROR_IMPORTER_DB_NAME')
  local sql=$(cat <<EOF
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
    log "Updating passwords and pg_dist_authinfo for ${pod}"
    echo "$sql" | kubectl exec -n "${namespace}" -i "${pod}" -c postgres-util -- psql -U "${superuserUsername}" -f -
  done
}

AUTO_UNROUTE="${AUTO_UNROUTE:-true}"
CITUS_CLUSTERS="$(getCitusClusters)"
COMMON_NAMESPACE="${COMMON_NAMESPACE:-common}"
CURRENT_CONTEXT="$(kubectl config current-context)"
DISK_PREFIX=
GCP_COORDINATOR_POOL_NAME="${GCP_COORDINATOR_POOL_NAME:-citus-coordinator}"
GCP_WORKER_POOL_NAME="${GCP_WORKER_POOL_NAME:-citus-worker}"
HELM_RELEASE_NAME="${HELM_RELEASE_NAME:-mirror}"
STACKGRES_MASTER_LABELS="${STACKGRES_MASTER_LABELS:-app=StackGresCluster,role=master}"
PATRONI_MASTER_ROLE="${PATRONI_MASTER_ROLE:-Leader}"

alias kubectl_common="kubectl -n ${COMMON_NAMESPACE}"
