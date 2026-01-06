## Problem

The pvc for a shard is running out of space and needs to be increased beyond current capacity of the disk.

## Prerequisites

- Have `jq` installed
- The kubectl context is set to the cluster containing the disks you want to resize

## Solution

1. Identify the worker (and/or coordinator) pvc(s) that needs to be resized
   ```bash
   kubectl get pv -o \
   custom-columns='PVC_NAME:.spec.claimRef.name,PV_NAME:.metadata.name,CAPACITY:..spec.capacity.storage,NODE_ID:.spec.nodeAffinity.required.nodeSelectorTerms[0].matchExpressions[0].values[0]' \
   --sort-by=.spec.capacity.storage
   ```
   Example output
   ```text
      PVC_NAME                                                                               PV_NAME                                    CAPACITY   NODE_ID
      sentinel-data-mirror-redis-node-1                                                      pvc-9d9da6c6-f6e4-45a3-91cf-61d47e50dcd9   1Gi        us-central1-f
      sentinel-data-mirror-redis-node-2                                                      pvc-4b25a1b7-c34a-4d1f-8af3-bfcfe908bd99   1Gi        us-central1-c
      sentinel-data-mirror-redis-node-0                                                      pvc-531e97b6-d4d6-4023-a2dc-847a2fac75dd   1Gi        us-central1-b
      redis-data-mirror-redis-node-0                                                         pvc-7638c7ba-2ffe-4bb7-a09d-995e4d09b3a4   8Gi        us-central1-b
      redis-data-mirror-redis-node-1                                                         pvc-edc9ed5a-03b4-48eb-86b0-49def5c1af1f   8Gi        us-central1-f
      redis-data-mirror-redis-node-2                                                         pvc-638cab0b-ed6c-49b0-a61b-6893a5f3415f   8Gi        us-central1-c
      prometheus-mirror-prometheus-prometheus-db-prometheus-mirror-prometheus-prometheus-0   pvc-4745d425-fb1d-4af3-85c6-272cff98dcb8   100Gi      us-central1-b
      storage-mirror-loki-0                                                                  pvc-768215f5-30e3-4253-95ea-a82fb733207e   250Gi      us-central1-b
      mirror-citus-coord-data-mirror-citus-coord-0                                           pvc-6501aa41-f238-447b-b21b-7d91a36b8f02   256Gi      coordinator-us-central1-c-0
      mirror-citus-coord-data-mirror-citus-coord-1                                           pvc-78ef76d9-ea31-49b3-a9b6-559a3ec5cd9f   256Gi      coordinator-us-central1-b-0
      mirror-citus-shard2-data-mirror-citus-shard2-0                                         pvc-49d46894-51a0-4a97-b2da-e9c003e382f2   3200Gi     worker-us-central1-b-0
      mirror-citus-shard0-data-mirror-citus-shard0-0                                         pvc-5dd58b07-db59-4c3a-882f-dcd7467dfd49   10000Gi    worker-us-central1-c-0
      mirror-citus-shard1-data-mirror-citus-shard1-0                                         pvc-f9b980a9-0771-4222-9034-bd44279ddde8   12000Gi    worker-us-central1-f-0
   ```
2. Using the `nodeId` from the previous step, increase the disk size for all disks needed
   ```text
   diskPrefix - value of zfs.init.diskPrefix in values.yaml
   diskName - {diskPrefix}-{nodeId}-zfs
   zone - extracted from the `nodeId`
   diskSize - the new size of the disk in Gb.
   ```
   ```bash
   gcloud compute disks resize "{diskName}" --size="{diskSize}" --zone="{zone}"
   ```
3. Restart the zfs manager pods
   ```bash
   kubectl rollout restart daemonset -n common mirror-zfs-manager
   ```
4. Verify the pool size has been increased
   ```bash
   kubectl get pods -n common -l component=openebs-zfs-node  -o json |
   jq -r '.items[].metadata.name' |
   xargs -I % kubectl exec -c openebs-zfs-plugin -n common % -- zfs list
   ```
5. Update the `hedera-mirror` chart's `values.yaml` to reflect the new disk size
   ```yaml
   stackgres:
     coordinator:
       persistentVolume:
         size: 256Gi
     worker:
       overrides:
         - index: 0
           pods:
             persistentVolume:
               size: 12000Gi
         - index: 1
           pods:
             persistentVolume:
               size: 14000Gi
         - index: 2
           pods:
             persistentVolume:
               size: 3200Gi
   ```
6. Deploy the changes. Be sure to leave wiggle room for zfs rounding
   see [here](https://github.com/openebs/zfs-localpv/blob/develop/docs/faq.md#7-why-the-zfs-volume-size-is-different-than-the-reqeusted-size-in-pvc)
   and use [this site](https://www.gbmb.org/gib-to-gb) for conversion
