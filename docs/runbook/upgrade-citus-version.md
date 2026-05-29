# Upgrade Citus Version For Sharded Cluster

## Problem

Need to update citus extension for stackgres sharded cluster

## Prerequisites

- Have `jq` and `yq` installed
- The kubectl context is set to the cluster you want to upgrade
- All bash commands assume your working directory is `tools/cluster-management`
- Common chart with Stackgres containing necessary citus version is already deployed

## Solution

1. Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md)
   to backup the current cluster data
2. Run

```bash
./upgrade-citus-version.sh
```
