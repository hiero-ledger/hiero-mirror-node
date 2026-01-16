# Restore Citus Data From StackGresShardedBackup

## Problem

Need to restore Citus cluster from a StackGres sharded backup

## Prerequisites

- Have `jq`, `yq`, and `base64` installed
- The cluster has a running Citus cluster deployed with `hedera-mirror` chart
- StackGresShardedCluster backup is enabled
- All bash commands assume your working directory is `tools/cluster-management`
- Only a single citus cluster is installed per namespace
- The kubectl context is set to the cluster you want to restore backup to and the namespace is set to the one
  `hedera-mirror` chart is installed in

## Steps

Run script and follow along with all prompts

```bash
./restore-stackgres-backup.sh
```
