# ZFS on Google Kubernetes Engine (GKE)

## Purpose

This document outlines the steps needed to run [Citus](https://www.citusdata.com/)
in [GKE](https://cloud.google.com/kubernetes-engine) using the [ZFS](https://en.wikipedia.org/wiki/ZFS) filesystem
with [Zstandard](https://github.com/facebook/zstd) compression enabled. This is made possible by installing a custom
CSI (Container Storage Interface) driver called [OpenEBS ZFS LocalPV](https://openebs.github.io/zfs-localpv/).

## Pre-Requisites

All listed commands with relative paths are assumed to be run from the repository root.

1. [kubectl](https://kubernetes.io/docs/reference/kubectl)
2. [Helm](https://helm.sh/)
3. Access to create clusters in GKE

## Setup

### Infrastructure

1. Create a Kubernetes cluster with the standard application node pool and auto-upgrade disabled.
2. Create two node pools specific for the Citus coordinator and worker nodes. For each:
   1. Use Ubuntu with containerd.
   2. Set node security access scopes to `Allow full access to all Cloud APIs`.
   3. Add a label and taint to node to match (change `citus-role` appropriately):
   ```yaml
   nodeSelector:
     citus-role: worker
     csi-type: zfs
   tolerations:
     - key: zfs
       operator: Equal
       value: "true"
       effect: NoSchedule
   ```

On GKE, the following gcloud commands can be used to easily create the cluster and node pools:

If you plan to use an nvme attached ssd for an L2 ARC, be sure to add the `--local-nvme-ssd-block count=1` option when
creating the coordinator and worker node pools. Note that having an L2 ARC requires some memory overhead in the primary
ARC and you may need to increase its size in the helm chart (`zfs.init.arcSizeGb`). You should also
set `zfs.init.l2ArcNvmeDeviceId` in the helm chart. If only attaching a single local ssd, this value should be `0`.
See [here](https://cloud.google.com/kubernetes-engine/docs/how-to/persistent-volumes/local-ssd-raw#node-pool) for more
information on the device id.

```shell
NETWORK="default"
PROJECT="myproject"
VERSION="1.32.2-gke.1182001"
gcloud beta container --project "${PROJECT}" clusters create "citus" --region "us-central1" --no-enable-basic-auth --cluster-version "${VERSION}" --release-channel "None" --machine-type "n2-custom-6-16384" --image-type "COS_CONTAINERD" --disk-type "pd-balanced" --disk-size "100" --metadata disable-legacy-endpoints=true --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --num-nodes "1" --logging=SYSTEM,WORKLOAD --monitoring=SYSTEM --enable-ip-alias --network "${NETWORK}" --no-enable-intra-node-visibility --default-max-pods-per-node "110" --security-posture=standard --workload-vulnerability-scanning=disabled --enable-network-policy --no-enable-master-authorized-networks --addons HorizontalPodAutoscaling,HttpLoadBalancing,NodeLocalDNS,GcePersistentDiskCsiDriver --enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0 --no-enable-managed-prometheus --enable-shielded-nodes --node-locations "us-central1-a","us-central1-b" --workload-pool "${PROJECT}.svc.id.goog" --workload-metadata=GKE_METADATA
gcloud beta container --project "${PROJECT}" node-pools create "coordinator" --cluster "citus" --region "us-central1" --node-version "${VERSION}" --machine-type "n2-custom-10-32768" --image-type "UBUNTU_CONTAINERD" --disk-type "pd-balanced" --disk-size "100" --node-labels csi-type=zfs,citus-role=coordinator --metadata disable-legacy-endpoints=true --node-taints zfs=true:NoSchedule --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --num-nodes "1" --no-enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0 --node-locations "us-central1-a","us-central1-b" --workload-metadata=GKE_METADATA
gcloud beta container --project "${PROJECT}" node-pools create "worker" --cluster "citus" --region "us-central1" --node-version "${VERSION}" --machine-type "n2-custom-10-32768" --image-type "UBUNTU_CONTAINERD" --disk-type "pd-balanced" --disk-size "100" --node-labels csi-type=zfs,citus-role=worker --metadata disable-legacy-endpoints=true --node-taints zfs=true:NoSchedule --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --num-nodes "1" --no-enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0 --node-locations "us-central1-a","us-central1-b","us-central1-c" --workload-metadata=GKE_METADATA
```

## Install

### Install the ZFS Driver

Before performing these steps, ensure that your kubectl is pointing to the correct cluster by examining the output of
`kubectl config get-contexts`.

1. `helm dependency build charts/hedera-mirror-common`
2. `helm upgrade --install mirror charts/hedera-mirror-common --create-namespace -n common --set zfs.enabled=true,stackgres.enabled=true`

### Install Citus

Install the `hedera-mirror` chart using the prod and v2 values files:

1. `helm dependency build charts/hedera-mirror`
2. `helm upgrade --install  mirror charts/hedera-mirror -f charts/hedera-mirror/values-prod.yaml -f charts/hedera-mirror/values-v2.yml --create-namespace --namespace citus`

## Maintenance

### Expanding a disk

1. Increase the size of the `zfs.(coordinator|worker).diskSize` properties and re-deploy the common chart.
2. Increase the size of the `stackgres.(coordinator|worker).persistentVolume.size` properties and re-deploy the mirror
   chart. Ensure this size is slightly smaller than the corresponding size set in #1 to account for the ZFS overhead.

### Recompressing

1. Changing ZFS compression settings only effects writes going forward. In order to recompress, you must copy the data
   to a new folder.

### Updating ZFS parameters

For list of parameters see [here](https://github.com/openebs/zfs-localpv/blob/develop/docs/storageclasses.md)
For a guide on modifying the parameters, please follow the
guide [here](https://github.com/openebs/zfs-localpv#4-zfs-property-change)

1. Identify your pvc `kubectl get pvc -n <namespace>`
2. Identify your zv by matching the pvc `kubectl get zv --all-namespaces`
3. Edit the parameters on the zv `kubectl edit zv -n <namespace> <zvName>`

### Deleting a pool on a node

To delete a zpool on the node for a PVC, you must identify the PVC in question `kubectl get pvc --all-namespaces` and
find the corresponding zvolume `kubectl get zv --all-namespaces` then you may delete the volume
using `kubectl delete zv -n <namespace> <zVolumeName>`. This will do the cleanup on the node itself and make the space
available in the zpool again.
