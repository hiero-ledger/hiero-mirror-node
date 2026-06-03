# Upgrade Minor Postgres Version For Sharded Cluster

## Problem

Need to update minor postgres version for stackgres citus cluster

## Prerequisites

- Have `jq` and `yq` installed
- The kubectl context is set to the cluster you want to upgrade
- All bash commands assume your working directory is `tools/cluster-management`
- Common chart with Stackgres containing necessary postgres version is already deployed

## Solution

1. Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md)
   to backup the current cluster data
2. Run

```bash
./minor-postgres-version-upgrade.sh
```
