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
2. By default, the script will copy the environment, run k6 tests, and automatically tear down resources. Set the
   following environment variables to a non-default value if needed
   - `DEFAULT_POOL_NAME`
   - `TEST_KUBE_TARGET_NAMESPACE`
3. Run script and follow along with all prompts. To auto confirm destructive operations, set `AUTO_CONFIRM=true`
4. You can skip any prompts or inputs by setting the following variables. If they are not set you will be prompted for
   their values
   - `GCP_SNAPSHOT_PROJECT`
   - `GCP_TARGET_PROJECT`
   - `GCP_K8S_TARGET_CLUSTER_REGION`
   - `GCP_K8S_TARGET_CLUSTER_NAME`
5. Use different combinations of `NO_RESTORE` (default `false`), `RUN_K6_TEST` (default `true`), and `TEARDOWN_TARGET`
   (default `true`) for different tasks. Examples:
   - `TEARDOWN_TARGET=false`: Copy the environment and run k6 tests. The target cluster is left running
   - `RUN_K6_TEST=false` and `TEARDOWN_TARGET=false`: Just copy the environment and leave it running
   - `NO_RESTORE=true` and `TEARDOWN_TARGET=false`: Re-run k6 tests given the target cluster is already restored
   - `NO_RESTORE=true` and `RUN_K6_TEST=false`: Tear down the target cluster

```bash
./copy-live-environment.sh
```
