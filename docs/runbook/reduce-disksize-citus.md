## Problem

Citus disks are over provisioned and need to be reduced in size.

## Prerequisites

- All Citus PVCS are defined in GiB
- `jq` and `yq` is installed
- The kubectl context is set to the cluster with the oversized disk
- Need to ensure that `zfs.(coordinator|worker).initialDiskSize` is less than any disk you are reducing
- The script will resize the node's disk along with any database persistent volumes associated with that node.
- Follow the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook to create a snapshot for the cluster before running this runbook

## Solution

Run the script and follow along with all prompts:

```bash
./reduce-citus-disk-size.sh
```
