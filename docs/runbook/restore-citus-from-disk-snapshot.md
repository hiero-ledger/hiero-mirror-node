# Restore Citus Data From Disk Snapshots

## Problem

Need to restore Citus cluster from disk snapshots

## Prerequisites

- Snapshots of disks were created by following the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook
- Have `jq`, `yq`, and `ksd`(kubernetes secret decrypter) installed
- The snapshots are from a compatible version of `postgres`
- The `target cluster` has a running Citus cluster deployed with `hedera-mirror` chart
- The `target cluster` you are restoring to doesn't have any pvcs with a size larger than the size of the pvc in the
  snapshot. You can't decrease the size of a pvc. If needed, you can delete the existing cluster in the `target cluster`
  and redeploy the `hedera-mirror` chart with the default disk sizes.
- If you have multiple Citus clusters in the `target cluster`, you will need to restore all of them
- All bash commands assume your working directory is `tools/cluster-management`
- Only a single citus cluster is installed per namespace
- The kubectl context is set to the cluster you want to restore snapshots to

## Steps

Run script and follow along with all prompts

```bash
./restore-volume-snapshot.sh
```
