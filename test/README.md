# Acceptance Tests

This module covers the end-to-end (E2E) testing strategy employed by the mirror node for key scenarios.

## Overview

In an effort to quickly confirm product capability during deployment windows, we desire to have E2E tests that will
allow us to confirm functionality for core scenarios that span the main and mirror networks. In general, transactions
are submitted to the main network, the mirror node importer ingests these to the database, and the client subscribes to
either the mirror node gRPC or REST API to receive results.

To achieve this, the tests use the Hiero Java SDK under the hood. This E2E suite allows us to execute scenarios as
regular clients would and gain the required confidence during deployment.

## Cucumber

Our E2E tests use the [Cucumber](https://cucumber.io) framework, which allows them to be written in
a [BDD](https://cucumber.io/docs/bdd/) approach that ensures we closely track valid customer scenarios. The framework
allows tests to be written in a human-readable text format by way of the Gherkin plain language parser, which gives
developers, project managers, and quality assurance the ability to write easy-to-read scenarios that connect to more
complex code underneath.

### Requirements

- Java

### Test Execution

Tests can be compiled and run by running the following command from the root folder:

`./gradlew :test:acceptance --info -Dcucumber.filter.tags=@acceptance`

### Test Configuration

Configuration properties are set in the `application.yml` file located under `src/test/resources`. This component
uses [Spring Boot](https://spring.io/projects/spring-boot) properties to configure the application. Available properties
include:

| Name                                                                    | Default                                      | Description                                                                                                       |
| ----------------------------------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `hiero.mirror.test.acceptance.backOffPeriod`                            | 5s                                           | The amount of time the client will wait before retrying a retryable failure.                                      |
| `hiero.mirror.test.acceptance.childAccountBalance`                      | 0.1                                          | The amount of dollars to fund a child account.                                                                    |
| `hiero.mirror.test.acceptance.createOperatorAccount`                    | true                                         | Whether to create a separate operator account to run the acceptance tests.                                        |
| `hiero.mirror.test.acceptance.emitBackgroundMessages`                   | false                                        | Whether background topic messages should be emitted.                                                              |
| `hiero.mirror.test.acceptance.feature.contractCallLocalEstimate`        | false                                        | Whether to execute additional ContractCallLocal queries to network node for additional gas estimate validation    |
| `hiero.mirror.test.acceptance.feature.hapiMinorVersionWithHooks`        | 70                                           | The HAPI minor version at which hooks is enabled.                                                                 |
| `hiero.mirror.test.acceptance.feature.hapiMinorVersionWithoutGasRefund` | 69                                           | The HAPI minor version at which no gas refund is enabled.                                                         |
| `hiero.mirror.test.acceptance.feature.maxContractFunctionGas`           | 5250000                                      | The maximum amount of gas an account is willing to pay for a contract call.                                       |
| `hiero.mirror.test.acceptance.feature.sidecars`                         | false                                        | Whether information in sidecars should be used to verify test scenarios.                                          |
| `hiero.mirror.test.acceptance.maxNodes`                                 | 10                                           | The maximum number of nodes to validate from the address book.                                                    |
| `hiero.mirror.test.acceptance.maxRetries`                               | 2                                            | The number of times client should retry mirror node on supported failures.                                        |
| `hiero.mirror.test.acceptance.maxTinyBarTransactionFee`                 | 5000000000                                   | The maximum transaction fee the payer is willing to pay in tinybars.                                              |
| `hiero.mirror.test.acceptance.messageTimeout`                           | 20s                                          | The maximum amount of time to wait to receive topic messages from the mirror node.                                |
| `hiero.mirror.test.acceptance.mirrorNodeAddress`                        | testnet.mirrornode.hedera.com:443            | The mirror node gRPC server endpoint including IP address and port.                                               |
| `hiero.mirror.test.acceptance.network`                                  | TESTNET                                      | Which Hedera network to use. Can be either `MAINNET`, `PREVIEWNET`, `TESTNET` or `OTHER`.                         |
| `hiero.mirror.test.acceptance.nodes[].accountId`                        | ""                                           | The consensus node's account ID                                                                                   |
| `hiero.mirror.test.acceptance.nodes[].certHash`                         | ""                                           | The consensus node's certificate hash used for TLS certificate verification                                       |
| `hiero.mirror.test.acceptance.nodes[].host`                             | ""                                           | The consensus node's hostname                                                                                     |
| `hiero.mirror.test.acceptance.nodes[].nodeId`                           | ""                                           | The consensus node's node ID                                                                                      |
| `hiero.mirror.test.acceptance.nodes[].port`                             | 50211                                        | The consensus node's port                                                                                         |
| `hiero.mirror.test.acceptance.operatorBalance`                          | 72                                           | The amount of dollars to fund the operator. Applicable only when `createOperatorAccount` is `true`.               |
| `hiero.mirror.test.acceptance.operatorId`                               | 0.0.2                                        | Operator account ID used to pay for transactions.                                                                 |
| `hiero.mirror.test.acceptance.operatorKey`                              | Genesis key                                  | Operator ED25519 or ECDSA private key used to sign transactions in hex encoded DER format.                        |
| `hiero.mirror.test.acceptance.rest.baseUrl`                             | https://testnet.mirrornode.hedera.com/api/v1 | The URL to the mirror node REST API.                                                                              |
| `hiero.mirror.test.acceptance.rest.maxAttempts`                         | 20                                           | The maximum number of attempts when calling a REST API endpoint and receiving a 404.                              |
| `hiero.mirror.test.acceptance.rest.maxBackoff`                          | 4s                                           | The maximum amount of time to wait between REST API attempts.                                                     |
| `hiero.mirror.test.acceptance.rest.minBackoff`                          | 0.5s                                         | The minimum amount of time to wait between REST API attempts.                                                     |
| `hiero.mirror.test.acceptance.restJava.baseUrl`                         | Inherits `rest.baseUrl`                      | The endpoint associated with the REST Java API.                                                                   |
| `hiero.mirror.test.acceptance.restJava.enabled`                         | false                                        | Whether to invoke the REST Java API.                                                                              |
| `hiero.mirror.test.acceptance.retrieveAddressBook`                      | true                                         | Whether to download the address book from the mirror node and use those nodes to publish transactions.            |
| `hiero.mirror.test.acceptance.sdk.grpcDeadline`                         | 10s                                          | The maximum amount of time to wait for a grpc call to complete.                                                   |
| `hiero.mirror.test.acceptance.sdk.maxAttempts`                          | 1000                                         | The maximum number of times the sdk should try to submit a transaction to the network.                            |
| `hiero.mirror.test.acceptance.sdk.maxNodesPerTransaction`               | 2147483647                                   | The maximum number of nodes that a transaction can be submitted to.                                               |
| `hiero.mirror.test.acceptance.skipEntitiesCleanup`                      | false                                        | Whether to skip cleaning up the created in tests accounts,tokens, contracts at the end of execution.              |
| `hiero.mirror.test.acceptance.startupTimeout`                           | 60m                                          | How long the startup probe should wait for the network as a whole to be healthy before failing the tests.         |
| `hiero.mirror.test.acceptance.web3.baseUrl`                             | Inherits `rest.baseUrl`                      | The endpoint associated with the web3 API.                                                                        |
| `hiero.mirror.test.acceptance.web3.enabled`                             | false                                        | Whether to invoke the web3 API.                                                                                   |
| `hiero.mirror.test.acceptance.web3.opcode.tracer.enabled`               | false                                        | Whether to invoke the web3 opcode API.                                                                            |
| `hiero.mirror.test.acceptance.webclient.connectionTimeout`              | 10s                                          | The timeout duration to wait to establish a connection with the server.                                           |
| `hiero.mirror.test.acceptance.webclient.readTimeout`                    | 10s                                          | The timeout duration to wait for data to be read.                                                                 |
| `hiero.mirror.test.acceptance.webclient.wiretap`                        | false                                        | Whether a wire logger configuration should be applied to connection calls.                                        |
| `hiero.mirror.test.acceptance.webclient.writeTimeout`                   | 10s                                          | The timeout duration to wait for data to be written.                                                              |
| `hiero.mirror.test.acceptance.nodeType`                                 | MIRROR                                       | Determines the node type for test execution facilitated by NetworkAdapter. Can be either `MIRROR` or `CONSENSUS`. |

Options can be set by creating your own configuration file with the above properties. This allows for
multiple files per environment. The `spring.config.additional-location` property can be set to the folder containing
the environment-specific `application.yml`:

`./gradlew :test:acceptance --info -Dcucumber.filter.tags="@acceptance" -Dspring.config.additional-location=/etc/hiero/`

Options can also be set through the command line as follows

`./gradlew :test:acceptance --info -Dhiero.mirror.test.acceptance.nodeId=0.0.4 -Dhiero.mirror.test.acceptance.nodeAddress=1.testnet.hedera.com:50211`

#### Custom nodes

In some scenarios you may want to point to nodes not yet captured by the SDK, a subset of published nodes, or custom
nodes for a test environment. To achieve this you can specify a list of accountId and host key-value pairs in
the `hiero.mirror.test.acceptance.nodes` value of the config. These values will always take precedence over the default
network map used by the SDK for an environment. Refer
to [Mainnet Nodes](https://docs.hedera.com/guides/mainnet/mainnet-nodes)
and [Testnet Nodes](https://docs.hedera.com/guides/testnet/testnet-nodes) for the published list of nodes.

The following example shows how you might specify a set of hosts to point to. Modify the accountId and host values as
needed

```yaml
hiero:
  mirror:
    test:
      acceptance:
        network: OTHER
        nodes:
          - accountId: 0.0.3
            host: 127.0.0.1
          - accountId: 0.0.4
            host: 127.0.0.2
          - accountId: 0.0.5
            host: 127.0.0.3
          - accountId: 0.0.6
            host: 127.0.0.4
```

#### Feature Tags

Tags: Tags allow you to filter which Cucumber scenarios and files are run. By default, tests marked with
the `@acceptance` tag are run. To run a different set of files different tags can be specified.

Test Suite Tags

- `@critical` - Test cases to ensure the network is up and running and satisfies base scenarios. Total cost to run 6.5
  ℏ.
- `@release` - Test cases to verify a new deployed version satisfies core scenarios and is release worthy. Total cost to
  run 19.2 ℏ.
- `@acceptance` - Test cases to verify most feature scenarios meet customer acceptance criteria. Total cost to run 31.6
  ℏ.
- `@fullsuite` - All cases - this will require some configuration of feature files and may include some disabled tests
  that will fail on execution. Total cost to run 33.9 ℏ.

> **_NOTE:_** Any noted total costs are estimates.
> They will fluctuate with test coverage expansion, improvements and network fee schedule changes.

Feature based Tags

- `@accounts` - Crypto account focused tests.
- `@topic` - Simple HCS focused tests.
- `@topicmessagesfilter` - HCS focused tests wth varied subscription filters.
- `@token` - HTS focused tests.
- `@schedulebase` - Scheduled transactions focused tests.

To execute run

    ./gradlew :test:acceptance --info -Dcucumber.filter.tags="<tag name>"

> **_NOTE:_** Feature tags can be combined - See [Tag expressions](https://cucumber.io/docs/cucumber/api/). To run a
> subset of tags
>
> - `@acceptance and @topic` - all topic acceptance scenarios
> - `@acceptance and not @token` - all acceptance except token scenarios

### Test Layout

The project layout encompasses the Cucumber Feature files, the Runner file(s) and the Step files

- Feature Files : These are located under `src/test/resources/features` folder and are files of the `.feature` format.
  These files contain the Gherkin based language that describes the test scenarios.
- Step Files : These are java classes located under `src/test/java/org/hiero/mirror/test/e2e/acceptance/steps`. Every
  `Given`, `When`, `And`, and `Then` keyword line in the `.feature` file has a matching step method that implements its
  logic. Feature files scenarios and step file method descriptions must be kept in sync to avoid mismatch errors.
- Runner Files : Currently a single runner file is used at
  `src/test/java/org/hiero/mirror/test/e2e/acceptance/AcceptanceTest.java`. This file also specifies
  the `CucumberOptions`
  such as `features`, `glue` and `plugin` that are used to connect all the files together.

### Test Creation

To create a new test/scenario follow these steps

1. Update an existing .feature file or create a new .feature file under `src/test/resources/features` with your desired
   scenario. Describe your scenario with a `Given` setup, a `When` execution and a `Then` validation step. The `When`
   and `Then` steps would be the expected minimum for a meaningful scenario.
2. Update an existing step file or create a new step file with the corresponding java method under
   `src/test/java/org/hiero/mirror/test/e2e/acceptance/steps` that will be run. Note method Cucumber attribute text
   must
   match the feature file.
