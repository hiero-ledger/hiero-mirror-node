# Copy Live Environment

## Problem

Need to copy live environment with zero downtime on source

## Prerequisites

- Have `jq`, `yq`, and `base64` installed
- Have `testkube` kubectl plugin installed
- The source and target have compatible versions of postgres
- The `target cluster` has a running Citus cluster deployed with `hedera-mirror` chart
- The `target cluster` you are restoring to doesn't have any pvcs with a size larger than the size of the pvc in the
  snapshot. You can't decrease the size of a pvc. If needed, you can delete the existing cluster in the `target cluster`
  and redeploy the `hedera-mirror` chart with the default disk sizes.
- If you have multiple Citus clusters in the `target cluster`, you will need to restore all of them
- All bash commands assume your working directory is `tools/cluster-management`
- Only a single citus cluster is installed per namespace

## Steps

1. Configure kubernetes context for source and target by setting `K8S_SOURCE_CLUSTER_CONTEXT` and `K8S_TARGET_CLUSTER_CONTEXT`
2. If you want to copy the environment, run k6 tests, and automatically tear down resources, set `WAIT_FOR_K6=true` and
   `DEFAULT_POOL_NAME`
3. Run script and follow along with all prompts. To auto confirm destructive operations, set `AUTO_CONFIRM=true`
4. You can skip any prompts or inputs by setting the following variables. If they are not set you will be prompted for
   their values

- `GCP_SNAPSHOT_PROJECT`
- `GCP_TARGET_PROJECT`
- `GCP_K8S_TARGET_CLUSTER_REGION`
- `GCP_K8S_TARGET_CLUSTER_NAME`

```bash
./copy-live-environment.sh
```
