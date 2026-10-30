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

For `hyperdisk-balanced` disks, the copy runs with raised performance, set by `COPY_PROVISIONED_IOPS` (default `12800`)
and `COPY_PROVISIONED_THROUGHPUT` (default `400` MiB/s), within the disk limits and never below the current values. The
temporary disk is created with them and the source disk is raised to them. The recreated disk keeps the original values.
Compute Engine allows one performance change per disk every 4 hours, so a source disk changed more recently is copied at
its current performance. The copy is also capped by the VM's total Hyperdisk throughput, which the source and temporary
disks share.
