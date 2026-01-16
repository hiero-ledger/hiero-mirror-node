# Stream Verification

## Problem

Stream files need to be verified for correctness or to identify a particular error observed when processing in an
environment.

## Setup

- All commands are assumed to be run from the git repository root.

### DB

1. rm -r db
2. docker-compose up db redis

### Application Configuration

Create application.yml in `importer/config`

1. Configure Network
2. Configure start date
3. Configure end date (optional)
4. configure bucketName (blank will use the default bucket for the network)

#### For S3 download application.yml

```yaml
hiero:
  mirror:
    importer:
      startDate: 2023-04-19T16:12:00.000000000Z # exclusive
      endDate: 2023-04-19T16:12:00.000000001Z # inclusive
      network: testnet
      downloader:
        cloudProvider: S3
        accessKey: <S3_ACCESS_KEY>
        secretKey: <S3_SECRET_KEY>
        bucketName: # Remove or use a known bucket name
      parser:
        record:
          sidecar: true
```

#### For Local Files application.yml

When starting the application, the following subdirectories will be created under `${hiero.mirror.importer.dataPath}`
`/streams/accountBalances/balance{NODE1...NODEn}` `/streams/recordstreams/record{NODE1...NODEn}` where n is the number
of nodes in the address book.

You will need the record file(s) and signature file(s) from at least 1/3 of the nodes and placed under the corresponding
node folder in `${hiero.mirror.importer.dataPath}/streams/{accountBalances|recordstreams}/{NODE_FOLDER}`.

These folders are watched for new files, so you may place the necessary files after application has started.

```yaml
hiero:
  mirror:
    importer:
      dataPath: { PATH_TO_RECORD_STREAMS }
      startDate: 2023-04-19T22:47:16.030078003Z # exclusive
      endDate: 2023-04-19T22:47:16.030078003Z # inclusive
      network: testnet
      downloader:
        cloudProvider: LOCAL
      parser:
        record:
          sidecar: true
```

## Run the application

1. `./gradlew importer:bootRun`
2. Optionally, run in an IDE and run the bootrun Gradle task on the importer module

## Verification

### DB

1. `psql -h localhost -U mirror_node`
2. password is located in docker-compose.yml in this repo and defaulted to `mirror_node_pass`
3. Verify the record_file and/or account_balance_file table
4. Verify any expected transactions

#### Helpful Queries to verify data

```sql
SELECT * from record_file;

SELECT * from account_balance_file;

-- See TransactionType.java enum for readable type to int.
select * from transaction where type = ?;
```

### Other

1. Verify no errors in the application logs
