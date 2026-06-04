# Upgrade Major Postgres Version For Sharded Cluster

## Problem

Need to upgrade the major PostgreSQL version for a StackGres Citus cluster while preserving all shard data and Citus metadata integrity.

## Prerequisites

- Have `kubectl` and `jq` installed
- The kubectl context is set to the cluster you want to upgrade
- Configure the default namespace to be namespace of targeted cluster i.e `kubectl config set-context --current --namespace=mainnet-citus`
- Common chart with StackGres containing the target PostgreSQL and Citus versions is already deployed
- A full disk snapshot backup has been completed before starting

## Important Notes

- Only run `citus_prepare_pg_upgrade()` and `citus_finish_pg_upgrade()` on primary nodes
- Suspend flux and unroute traffic before starting
- You should complete any Stackgres upgrades or citus upgrades before upgrading postgres

## Solution

### 1. Create Backup

Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md) before beginning the upgrade.

### 2. Increase Pod Termination Grace Period (if not already set) And Verify Values On Citus Cluster Pods

```bash
kubectl patch sgshardedcluster.stackgres.io mirror-citus \
  --type='merge' \
  -p '{
    "spec": {
      "coordinator": {
        "pods": {
          "terminationGracePeriodSeconds": 600
        }
      },
      "shards": {
        "pods": {
          "terminationGracePeriodSeconds": 600
        }
      }
    }
  }'

cat <<EOF | kubectl apply -f -
apiVersion: stackgres.io/v1
kind: SGShardedDbOps
metadata:
  name: restart-mirror-citus
spec:
  sgShardedCluster: mirror-citus
  op: restart
EOF

sleep 5
kubectl wait --for=jsonpath='{.status.phase}'=Succeeded pod -l job-name=restart-mirror-citus --timeout=-1s
```

Cleanup restart job

```bash
kubectl delete sgshardeddbops.stackgres.io restart-mirror-citus
```

Verify the change

```bash
kubectl get pods -l app=StackGresCluster -o yaml | grep terminationGracePeriodSeconds
```

### 3. Scale Down Database Clients

Scale all database clients to zero replicas:

```bash
kubectl scale deployment mirror-restjava --replicas=0
kubectl scale deployment mirror-rest --replicas=0
kubectl scale deployment mirror-web3 --replicas=0
kubectl scale deployment mirror-grpc --replicas=0
kubectl scale deployment mirror-rest-monitor --replicas=0
kubectl scale deployment mirror-monitor --replicas=0
kubectl scale deployment mirror-importer --replicas=0
```

### 4. Capture Baseline Validation Queries

```bash
kubectl exec -it mirror-citus-coord-0 -c postgres-util -- \
psql -U postgres -d mirror_node -P pager=off -c '
select consensus_end,load_start,load_end from record_file order by consensus_end asc limit 2;
select consensus_end,load_start,load_end from record_file order by consensus_end desc limit 2;
select consensus_timestamp from transaction order by consensus_timestamp asc limit 20;
select consensus_timestamp from transaction order by consensus_timestamp desc limit 20;
'
```

# Worker Upgrade Phase

## 5. Pause Coordinator

```bash
kubectl annotate sgclusters.stackgres.io \
  mirror-citus-coord \
  stackgres.io/reconciliation-pause="true" \
  --overwrite
```

## 6. Scale Down Coordinator

```bash
kubectl scale sts \
  mirror-citus-coord \
  --replicas=0
```

[//]: # "TODO fix this command so it doesnt produce citus, if only shared lib"

## 7. Create Worker SGPostgresConfig

```bash
kubectl get sgpgconfigs.stackgres.io mirror-citus-worker \
  -o json \
| jq '
  del(
    .metadata.creationTimestamp,
    .metadata.generation,
    .metadata.resourceVersion,
    .metadata.uid,
    .status
  )
  | .metadata.name = .metadata.name + "-18"
  | .spec.postgresVersion = "18"
  | .spec["postgresql.conf"].shared_preload_libraries =
      (
        .spec["postgresql.conf"].shared_preload_libraries // "" as $libs
        | if ($libs | test("(^|,\\s*)citus(\\s*,|$)")) then
            $libs
          elif ($libs | gsub("\\s"; "") | length) == 0 then
            "citus"
          else
            "citus, " + $libs
          end
      )
' \
| kubectl apply -f -
```

## 8. Run citus_prepare_pg_upgrade()

```bash
kubectl exec -it mirror-citus-shard0-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_prepare_pg_upgrade();'

kubectl exec -it mirror-citus-shard1-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_prepare_pg_upgrade();'

kubectl exec -it mirror-citus-shard2-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_prepare_pg_upgrade();'
```

## 9. Disable Controller Ownership

```bash
kubectl patch sgclusters.stackgres.io mirror-citus-shard0 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":false}
  ]'

kubectl patch sgclusters.stackgres.io mirror-citus-shard1 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":false}
  ]'

kubectl patch sgclusters.stackgres.io mirror-citus-shard2 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":false}
  ]'
```

## 10. Run Major Version Upgrade For All Workers (can run in parallel)

```bash
cat <<EOF | kubectl apply -f -
apiVersion: stackgres.io/v1
kind: SGDbOps
metadata:
  name: major-version-upgrade-shard0
spec:
  maxRetries: 1
  op: majorVersionUpgrade
  majorVersionUpgrade:
    link: true
    postgresVersion: "18.3"
    sgPostgresConfig: "mirror-citus-worker-18"
    postgresExtensions:
      - name: citus
        version: "14.0.0"
      - name: citus_columnar
        version: "14.0.0"
      - name: btree_gist
        version: stable
      - name: pg_trgm
        version: "1.6"
  sgCluster: mirror-citus-shard0
---
apiVersion: stackgres.io/v1
kind: SGDbOps
metadata:
  name: major-version-upgrade-shard1
spec:
  maxRetries: 1
  op: majorVersionUpgrade
  majorVersionUpgrade:
    link: true
    postgresVersion: "18.3"
    sgPostgresConfig: "mirror-citus-worker-18"
    postgresExtensions:
      - name: citus
        version: "14.0.0"
      - name: citus_columnar
        version: "14.0.0"
      - name: btree_gist
        version: stable
      - name: pg_trgm
        version: "1.6"
  sgCluster: mirror-citus-shard1
---
apiVersion: stackgres.io/v1
kind: SGDbOps
metadata:
  name: major-version-upgrade-shard2
spec:
  maxRetries: 1
  op: majorVersionUpgrade
  majorVersionUpgrade:
    link: true
    postgresVersion: "18.3"
    sgPostgresConfig: "mirror-citus-worker-18"
    postgresExtensions:
      - name: citus
        version: "14.0.0"
      - name: citus_columnar
        version: "14.0.0"
      - name: btree_gist
        version: stable
      - name: pg_trgm
        version: "1.6"
  sgCluster: mirror-citus-shard2
EOF
```

## 11. Monitor Upgrade Pods

Watch upgrade pods for failures or stale PostgreSQL version metadata.

```
++ kubectl get sts mirror-citus-shard0 -o json
++ jq '(.spec.template.spec.initContainers | any(.name == "major-version-upgrade"))
        and (.spec.template.metadata.annotations["stackgres.io/postgresql-version"] == "18.3")'
+ IS_STATEFULSET_UPDATED=false
```

If this error is observed, you will need to patch the `SGCluster` status manually using

```bash
kubectl patch sgclusters.stackgres.io mirror-citus-shard0 \
--type='merge' \
-p '{"status":{"postgresVersion":"18.3"}}'
```

## 12. Run citus_finish_pg_upgrade() After All Upgrade Pods Complete

```bash
kubectl exec -it mirror-citus-shard0-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_finish_pg_upgrade();'

kubectl exec -it mirror-citus-shard1-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_finish_pg_upgrade();'

kubectl exec -it mirror-citus-shard2-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_finish_pg_upgrade();'
```

## 13. Pause Workers

```bash
kubectl annotate sgclusters.stackgres.io \
  mirror-citus-shard0 mirror-citus-shard1 mirror-citus-shard2 \
  stackgres.io/reconciliation-pause="true" \
  --overwrite
```

## 14. Scale Down Workers

```bash
kubectl scale sts \
  mirror-citus-shard0 mirror-citus-shard1 mirror-citus-shard2 \
  --replicas=0
```

# Coordinator Upgrade Phase

## 15. Unpause Coordinator

```bash
kubectl annotate sgclusters.stackgres.io \
  mirror-citus-coord \
  stackgres.io/reconciliation-pause- \
  --overwrite
```

## 16. Create Coordinator SGPostgresConfig

```bash
kubectl get sgpgconfigs.stackgres.io mirror-citus-coordinator \
  -o json \
| jq '
  del(
    .metadata.creationTimestamp,
    .metadata.generation,
    .metadata.resourceVersion,
    .metadata.uid,
    .status
  )
  | .metadata.name = .metadata.name + "-18"
  | .spec.postgresVersion = "18"
  | .spec["postgresql.conf"].shared_preload_libraries =
      (
        .spec["postgresql.conf"].shared_preload_libraries // "" as $libs
        | if ($libs | test("(^|,\\s*)citus(\\s*,|$)")) then
            $libs
          elif ($libs | gsub("\\s"; "") | length) == 0 then
            "citus"
          else
            "citus, " + $libs
          end
      )
' \
| kubectl apply -f -
```

## 17. Run citus_prepare_pg_upgrade() on Coordinator

```bash
kubectl exec -it mirror-citus-coord-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_prepare_pg_upgrade();'
```

## 18. Disable Coordinator Controller Ownership

```bash
kubectl patch sgclusters.stackgres.io mirror-citus-coord \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":false}
  ]'
```

## 19. Run Coordinator Major Version Upgrade

Create and apply an SGDbOps resource for the coordinator.

```bash
cat <<EOF | kubectl apply -f -
apiVersion: stackgres.io/v1
kind: SGDbOps
metadata:
  name: major-version-upgrade-coord
spec:
  maxRetries: 1
  op: majorVersionUpgrade
  scheduling:
    priorityClassName: critical
  majorVersionUpgrade:
    link: true
    postgresVersion: "18.3"
    sgPostgresConfig: "mirror-citus-coordinator-18"
    postgresExtensions:
      - name: citus
        version: "14.0.0"
      - name: citus_columnar
        version: "14.0.0"
      - name: btree_gist
        version: stable
      - name: pg_trgm
        version: "1.6"
  sgCluster: mirror-citus-coord
EOF
```

## 20. Monitor Coordinator Upgrade Pod

Watch upgrade pods for failures or stale PostgreSQL version metadata.

```
++ kubectl get sts mirror-citus-coord -o json
++ jq '(.spec.template.spec.initContainers | any(.name == "major-version-upgrade"))
        and (.spec.template.metadata.annotations["stackgres.io/postgresql-version"] == "18.3")'
+ IS_STATEFULSET_UPDATED=false
```

If this error is observed, you will need to patch the `SGCluster` status manually using

```bash
kubectl patch sgclusters.stackgres.io mirror-citus-coord \
--type='merge' \
-p '{"status":{"postgresVersion":"18.3"}}'
```

## 21. Run citus_finish_pg_upgrade() on Coordinator

Wait for the coordinator replica to finish being created and run:

```bash
kubectl exec -it mirror-citus-coord-0 -c postgres-util -- \
psql -U postgres -d mirror_node -c 'SELECT citus_finish_pg_upgrade();'
```

# Finalization

## 22. Restore Controller Ownership

Set `controller=true` on all SGCluster resources.

```bash
kubectl patch sgclusters.stackgres.io mirror-citus-coord \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":true}
  ]'

kubectl patch sgclusters.stackgres.io mirror-citus-shard0 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":true}
  ]'

kubectl patch sgclusters.stackgres.io mirror-citus-shard1 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":true}
  ]'

kubectl patch sgclusters.stackgres.io mirror-citus-shard2 \
  --type='json' \
  -p='[
    {"op":"replace","path":"/metadata/ownerReferences/0/controller","value":true}
  ]'
```

## 23. Unpause Workers

```bash
kubectl annotate sgclusters.stackgres.io \
  mirror-citus-shard0 mirror-citus-shard1 mirror-citus-shard2 \
  stackgres.io/reconciliation-pause- \
  --overwrite
```

## 24. Delete Temporary SGPostgresConfigs

```bash
kubectl delete sgpgconfigs.stackgres.io --all
```

## 25. Validate Data Integrity

Re-run the validation queries and confirm data integrity.

## 26. Edit the `SGShardedCluster` resource

Update all postgres versions to match the upgraded version. This includes updating all extensions in the status section

```bash
kubectl get sgshardedcluster mirror-citus -o json \
| jq '
  .status.postgresVersion = "18.3"
  | .status.extensions |= map(
      if .name == "citus" or .name == "citus_columnar" then
        .postgresVersion = "18"
      else
        .postgresVersion = "18.3"
      end
    )
' \
| kubectl replace -f -
```

## 27. Scale resources back up

```bash
kubectl scale deployment mirror-restjava --replicas=1
kubectl scale deployment mirror-rest --replicas=1
kubectl scale deployment mirror-web3 --replicas=1
kubectl scale deployment mirror-grpc --replicas=1
kubectl scale deployment mirror-rest-monitor --replicas=1
kubectl scale deployment mirror-importer --replicas=1
```

## 28. Deploy Updated Helm Chart

Deploy the updated chart with the new PostgreSQL and Citus versions. Should disable acceptance and monitor

## 29. Verify Operator Logs

Ensure StackGres operator pod is not logging any errors. There will be some errors in the logs at various steps of the upgrade but there should be no new errors after previous step

## 30. Restart Coordinator

Restart the coordinator

```bash
cat <<EOF | kubectl apply -f -
apiVersion: stackgres.io/v1
kind: SGDbOps
metadata:
  name: restart-mirror-citus-coord
spec:
  op: restart
  sgCluster: mirror-citus-coord
EOF
```

## 31. Deploy Final Helm Release

Enable acceptance tests and monitor and upgrade helm release
