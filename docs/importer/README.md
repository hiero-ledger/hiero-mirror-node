# Importer

The importer component is responsible for downloading stream files uploaded by consensus nodes, verifying them, and
ingesting the normalized data into the database.

## HAPI Compatibility

The mirror node strives to be both backwards and forwards compatible with consensus nodes. Most of the time, older
versions of the mirror node should work against the latest version of consensus nodes. However, while it won't fail if
new fields or transactions are added to [HAPI](https://github.com/hashgraph/hedera-protobufs/tree/main/services), the
mirror node may fail to capture that extra information until it is updated. The exception to this rule is when the
stream format itself changes like when Record File v6 was introduced or the balance file was deprecated. The below
table provides a compatibility matrix of the most recent changes:

| HAPI Version | Mirror Node Version | Breaking Change | HIP                                                                                                                                                          |
| ------------ | ------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| v0.37.0      | v0.75.0+            | False           | [HIP-583](https://hips.hedera.com/hip/hip-583): Expand alias support                                                                                         |
| v0.40.0      | v0.84.0+            | False           | [HIP-729](https://hips.hedera.com/hip/hip-729): Contract account nonce externalization                                                                       |
| v0.42.0      | v0.88.0+            | True            | [HIP-794](https://hips.hedera.com/hip/hip-794): Sunsetting balance file                                                                                      |
| v0.43.0      | v0.89.0+            | False           | [HIP-786](https://hips.hedera.com/hip/hip-786): Enriched staking metadata exports                                                                            |
| v0.47.0+     | v0.98.0+            | False           | [HIP-844](https://hips.hedera.com/hip/hip-844): Handling and externalisation improvements for account nonce updates                                          |
| v0.48.0+     | v0.101.0+           | False           | [HIP-646](https://hips.hedera.com/hip/hip-646)/[657](https://hips.hedera.com/hip/hip-657)/[765](https://hips.hedera.com/hip/hip-765): Mutable token metadata |

## Synthetic Contract Logs

Users writing dApps want to monitor for token approval and transfer events. HAPI transactions like `CryptoTransfer`,
`CryptoApproveAllowance`, `CryptoDeleteAllowance`, `TokenMint`, `TokenWipe`, and `TokenBurn` do not emit events that
could be captured by monitoring tools since they're executed outside the EVM. In order to not bloat the size of the
blocks, the decision was made for the mirror node to generate synthetic events for these native HAPI transactions. It
is assumed that any transfers that occur via the EVM on consensus nodes will emit events that will show up in the
blocks.

Whether or not to generate these synthetic contract logs is controlled by the property
`hiero.mirror.importer.parser.record.entity.persist.syntheticContractLogs`, which has a default of `true`. When
enabled, the mirror node will persist synthetic contract logs from HAPI transactions to the database. This will
generate synthetic logs for the following scenarios:

- `CryptoApproveAllowanceTransaction`: Fungible and non-fungible allowances
- `CryptoDeleteAllowanceTransaction`: Non-fungible allowances
- `CryptoTransferTransaction`: Fungible and non-fungible only. For fungible, only if a single transfer pair.
- `TokenAirdropTransaction`: Fungible and non-fungible. For fungible, only if a single transfer pair.
- `TokenBurnTransaction`: Fungible and non-fungible
- `TokenCreateTransaction`: Fungible
- `TokenMintTransaction`: Fungible and non-fungible
- `TokenRejectTransaction`: Fungible and non-fungible. For fungible, only if a single transfer pair.
- `TokenWipeAccountTransaction`: Fungible and non-fungible
- Any other current or future transaction that transfers fungible or non-fungible tokens.

For now, the decision was made to omit Hbar transfers because they can be executed at 10,000 transactions per second
and this could quickly generate a lot of logs and slow down the system. Also, we only support token transfers that
have a single transfer pair due to the difficulty in splitting aggregated transfers in the `TransactionRecord` into
separate transfer pairs for event emission. This could change in the future.

## Initialize Entity Balance

The importer tracks up-to-date entity balance by applying balance changes from crypto transfers. This relies on the
[InitializeEntityBalanceMigration](/importer/src/main/java/org/hiero/mirror/importer/migration/InitializeEntityBalanceMigration.java)
to set the correct initial entity balance from the latest account balance snapshot relative to the last record stream
file the importer has ingested and balance changes from crypto transfers not accounted in the snapshot. If the importer
is started with a database which doesn't meet the prerequisite (e.g., an empty database) or the entity balance is
inaccurate due to bugs, follow the steps below to re-run the migration to fix it.

1. Stop importer

2. Get the latest checksum of `InitializeEntityBalanceMigration`

   ```shell
   $ psql -h db -U mirror_node -c "select checksum from flyway_schema_history \
     where script like '%InitializeEntityBalanceMigration' order by installed_rank desc limit 1"
   ```

3. Set a different checksum (e.g., 2) for the migration and start importer

   ```yaml
   hiero:
     mirror:
       importer:
         migration:
           initializeEntityBalanceMigration:
             checksum: 2
   ```

## Historical Data Ingestion

The following resource allocation and configuration is recommended to speed up historical data ingestion. The importer
should be able to ingest one month's worth of mainnet data in less than 1.5 days.

### Importer

Run the importer with 4 vCPUs and 10 GB of heap. Configure the application.yml:

```yaml
hiero:
  mirror:
    importer:
      downloader:
        batchSize: 600
        record:
          frequency: 1ms
      parser:
        record:
          entity:
            redis:
              enabled: false
          frequency: 10ms
          queueCapacity: 40
```

Note once the importer has caught up all data, it's recommend to change the configuration back to the default.

### Database

Run a PostgreSQL 16 instance with at least 4 vCPUs and 16 GB memory. Set the following parameters (note the unit is
kilobytes):

```
max_wal_size = 8388608
work_mem = 262144
```

## Performance Tests

The `RecordFileParserPerformanceTest` can be used to declaratively generate a `RecordFile` with different performance
characteristics and test how fast the importer can ingest them. To configure the performance test, populate the remote
database information and the test scenarios in an `application.yml`. Use the standard `hiero.mirror.importer.db`
properties to target the remote database. The below config is generating a mix of crypto transfer and contract calls
transactions at a combined 300 transactions per second (TPS) sustained for 60 seconds:

```yaml
hiero.mirror.importer.parser.record:
  performance:
    duration: 60s
    transactions:
      - entities: 10
        tps: 100
        type: CRYPTOTRANSFER
      - entities: 5
        tps: 200
        type: CONTRACTCALL
```

To run performance tests, use Gradle to run the `performanceTest` task. To run all performance tests omit the `tests`
parameter.

```console
./gradlew :importer:performanceTest --tests 'RecordFileParserPerformanceTest' --info
```

## Reconciliation Job

The reconciliation job verifies that the data within the stream files are in sync with each other and with the mirror
node database. This process runs once a day at midnight and produces logs, metrics, and alerts if reconciliation fails.

For each balance file, the job verifies it sums to 50 billion hbars. For every pair of balance files, it verifies the
aggregated hbar transfers in that period match what's expected in the next balance file. It also verifies the aggregated
token transfers match the token balance and that the NFT transfers match the expected NFT count in the balance file.

## Running importer for v2

For local testing the importer can be run using the following command:

```console
./gradlew :importer:bootRun --args='--spring.profiles.active=v2'
```

## Building the Citus docker image

The citus image is built and then pushed to our [Google Cloud Registry](https://gcr.io/mirrornode).
A multi-platform alpine linux image is built in order to be used for local testing (arm64 on M-series Macs).
This image will need to be maintained until the [upstream builder](https://github.com/citusdata/docker/tree/master)
provides a multi-platform build supporting arm64.

In production and ci environments, citus is enabled through stackgres SGShardedCluster and doesn't use the custom image.

`DockerFile` to build a custom image to be used in v2 testing is located
in [Citus's upstream repository](https://github.com/citusdata/docker). To build this image:

### GCR details

You authenticate with the registry via gcloud:

```console
gcloud auth configure-docker gcr.io
```

You should see output similar to:

```console
Adding credentials for: gcr.io
```

### Build

The instructions here pertain to building the multi-platform alpine image on an M-series Mac using Docker Desktop.
Please refer to
[Multi-platform images](https://docs.docker.com/build/building/multi-platform/) for an overview on how to use Docker
Desktop to do this.

If you've not done so already since installing Docker Desktop, create a new builder:

```console
$ docker buildx create --name mybuilder --driver docker-container --bootstrap                                                                            1 ↵
mybuilder
[+] Building 12.6s (1/1) FINISHED
 => [internal] booting buildkit                                                                                                                          12.6s
 => => pulling image moby/buildkit:buildx-stable-1                                                                                                       11.9s
 => => creating container buildx_buildkit_mybuilder0
```

Switch to the new builder:

```console
docker buildx use mybuilder
```

Checkout the upstream project containing the docker file

```
git clone git@github.com:citusdata/docker.git
cd docker
```

Build the image for both amd64 (Github CI) and arm64 (local testing). Don't forget the '.' at the end of the line. This
can take some time depending on your internet speed. You will see activity around both arm64 and amd64.

```console
$ docker buildx build --platform linux/arm64,linux/amd64 -t gcr.io/mirrornode/citus:12.1.1 --file alpine/Dockerfile  .
[+] Building 351.1s (28/28) FINISHED
 => [internal] load .dockerignore                                                                                                                         0.0s
 => => transferring context: 2B                                                                                                                           0.0s
 => [internal] load build definition from Dockerfile                                                                                                      0.0s
 => => transferring dockerfile: 3.58kB
 ...
 => [linux/amd64  1/11] FROM docker.io/library/postgres:15.1-alpine@sha256:f19eede5a214c0933dce30c2e734b787b4c09193e874cce3b26c5d54b8b77ec7              18.2s
 ...
 => [linux/arm64  1/11] FROM docker.io/library/postgres:15.1-alpine@sha256:f19eede5a214c0933dce30c2e734b787b4c09193e874cce3b26c5d54b8b77ec7              28.0s
 WARNING: No output specified with docker-container driver. Build result will only remain in the build cache. To push result image into registry use --push or to load image into docker use --load
```

Again, this does not push the image. If you prefer, you can add `--push` to the command above and push the image at this
time
rather than as a separate step below.

## Publishing the Citus docker images

If you did not utilize `--push` with `docker buildx` when building the alpine image, push it now to Docker Hub.

```console
docker push gcr.io/mirrornode/citus:12.1.1
```
