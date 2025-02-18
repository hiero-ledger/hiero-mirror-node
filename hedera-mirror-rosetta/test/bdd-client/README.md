# Rosetta BDD Test Client

This package provides a BDD test client for rosetta server.

## Overview

The BDD test client aims to provide better end-to-end test coverage of the rosetta Construction API, in addition to the
`rosetta-cli` [Constructor DSL](https://github.com/coinbase/rosetta-sdk-go/tree/master/constructor/dsl) based
[crypto transfer transaction Construction API tests](/hedera-mirror-rosetta/scripts/validation/testnet/testnet.ros).

The client currently supports basic crypto create, alias account creation, and crypto transfer scenarios.

## Requirements

- golang 1.17+
- two testnet accounts with private key
- aws / gcp credentials with requester pay enabled to access the Hedera network cloud storage

## Test Client Configuration

Configuration properties can be customized in an `application.yml` file in the test client source directory. Most
properties have default values and should work out of the box.

The only required property is two operator accounts and the corresponding private keys, for example:

```yaml
hedera:
  mirror:
    rosetta:
      test:
        operators:
          - privateKey: 90e42b7c...
            id: 0.0.65342
          - privateKey: 91e33b87...
            id: 0.0.65345
```

Note for crypto create and crypto transfer scenarios, only one operator account is required.

Please refer to the [appendix](#test-configuration-properties) for the complete list of properties.

## Run the Test

1. Build the Rosetta All-in-One Docker Image

   ```shell
   $ cd ../../container
   $ docker build --build-arg GIT_REF=${RELEASE_TAG} -t hedera-mirror-rosetta:${RELEASE_TAG} .
   ```

   > Note you can check the release tags on the releases page.

2. Configure and run the online mode and offline mode rosetta containers

   `hedera-mirror-importer` is a built-in component in the rosetta all-in-one docker image. Please refer to the
   [guide](https://docs.hedera.com/guides/mirrornet/run-your-own-beta-mirror-node) to properly configure the importer in
   the online mode rosetta container.

   Run the online mode rosetta container:

   ```shell
   docker run -d -e NETWORK=testnet \
     -v /tmp/application_importer.yml:/app/importer/application.yml \
     -p :5700:5700 hedera-mirror-rosetta:${RELEASE_TAG}
   ```

   Run the offline mode rosetta container:

   ```shell
   docker run -d -e MODE=offline -e NETWORK=testnet -p 5701:5700 hedera-mirror-rosetta:${RELEASE_TAG}
   ```

3. Configure and run the test client

   ```shell
   $ go test -v
   ```

   Note you can run tests with the `--godog.tags` flag to filter features, for example, to run `crypto` scenarios:

   ```shell
   $ go test -v --godog.tags=@crypto
   ```

   `--godog.tags` also supports complex expressions, please refer to
   the [official documentation](https://github.com/cucumber/godog#tags).

## Appendix

### Test Configuration Properties

The following table lists the available properties along with their default values.

| Name                                                    | Default               | Description                                                                                   |
| ------------------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------- |
| `hedera.mirror.rosetta.test.log.level`                  | debug                 | The log level                                                                                 |
| `hedera.mirror.rosetta.test.operators`                  | []                    | A list of operators with the account ids and corresponding private keys                       |
| `hedera.mirror.rosetta.test.operators[].id`             |                       | The operator account id, in the format of shard.realm.num                                     |
| `hedera.mirror.rosetta.test.operators[].privateKey`     |                       | The operator's private key in hex                                                             |
| `hedera.mirror.rosetta.test.server.dataRetry.backOff`   | 1s                    | The amount of time to wait between data request retries, if the request can be retried.       |
| `hedera.mirror.rosetta.test.server.dataRetry.max`       | 60                    | The max retries of a data request                                                             |
| `hedera.mirror.rosetta.test.server.httpTimeout`         | 25s                   | The timeout of an http request sent to the rosetta server                                     |
| `hedera.mirror.rosetta.test.server.network`             | {}                    | A map of main nodes with its service endpoint as the key and the node account id as its value |
| `hedera.mirror.rosetta.test.server.offlineUrl`          | http://localhost:5701 | The url of the offline rosetta server                                                         |
| `hedera.mirror.rosetta.test.server.onlineUrl`           | http://localhost:5700 | The url of the online rosetta server                                                          |
| `hedera.mirror.rosetta.test.server.submitRetry.backOff` | 200ms                 | The amount of time to wait between submit request retries                                     |
| `hedera.mirror.rosetta.test.server.submitRetry.max`     | 100                   | The max retries of a submit request                                                           |
