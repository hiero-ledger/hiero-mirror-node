# Upgrade K8s Version for Citus Node Pool(s)

## Problem

Need to update k8s version for clusters with citus installed

## Prerequisites

- Have `jq` and `yq` installed
- The kubectl context is set to the cluster you want to upgrade
- All bash commands assume your working directory is `docs/runbook/scripts`

## Solution

1. Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md)
   to backup the current cluster data
2. Run

```bash
./upgrade-k8s-version-citus.sh
```
