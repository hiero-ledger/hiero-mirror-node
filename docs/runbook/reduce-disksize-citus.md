## Problem

Citus disks are over provisioned and need to be reduced in size.

## Prerequisites

- All Citus PVCS are defined in GiB
- `jq` and `yq` is installed
- The kubectl context is set to the cluster with the over-sized disk
- Need to ensure that `zfs.(coordinator|worker).diskSize` is less than any disk you are reducing
- Follow the [create snapshot](create-disk-snapshot-for-citus-cluster.md) runbook to create a snapshot for cluster before running this runbook

## Solution

Run script and follow along with all prompts

```bash
./reduce-citus-disk-size.sh
```
