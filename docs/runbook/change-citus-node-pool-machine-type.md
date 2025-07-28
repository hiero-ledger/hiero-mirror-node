# Change Machine Type for Citus Node Pool(s)

## Problem

Need to Change Machine Type for Citus Node Pool(s)

## Prerequisites

- Have `jq` and `yq` installed
- kubectl is pointing to the cluster you want to change the machine type for
- All bash commands assume your working directory is `docs/runbook/scripts`

## Solution

1. Follow the steps to [create a disk snapshot for Citus cluster](./create-disk-snapshot-for-citus-cluster.md)
   to backup the current cluster data
2. Run
   ```bash
   ./change-machine-type.sh
   ```
