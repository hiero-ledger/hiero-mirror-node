# Monitor

The monitor verifies end-to-end functionality of the network and generates metrics from the results. It supports
both publishing transactions to HAPI and subscribing to the mirror node API. Configuration is flexible and declarative,
allowing one to express a mixture of transactions and their expected publish rates, and the tool will do its best to
make it so. By default, the monitor is already set up with a basic scenario that creates a topic, submits a message
every 10 seconds to it, and verifies the messages are received via the mirror node's gRPC and REST APIs.

## Configuration

This section covers the higher level properties to consider before diving into the specific publish and subscribe
properties in the next sections. For a full list of configuration options see
the [config](/docs/configuration.md#monitor)
documentation.

First, make sure the monitor is configured to talk to the correct network by setting `hiero.mirror.monitor.network` to
`MAINNET`, `PREVIEWNET` or `TESTNET`. If you are not using one of these public environments, the network can be set
to `OTHER`, and `hiero.mirror.monitor.nodes` and `hiero.mirror.monitor.mirrorNode` properties should be filled in.

Additionally, the operator information in `hiero.mirror.monitor.operator` is required and needs to be populated with a
valid payer account ID and its private key. Ensure this account has the necessary funds to publish transactions at the
rate you desire. The following is an example with a custom network configured:

```yaml
hiero:
  mirror:
    monitor:
      mirrorNode:
        grpc:
          host: 34.69.90.179
          port: 5600
        rest:
          host: 35.239.255.55
          port: 80
      network: OTHER
      nodes:
        - accountId: 0.0.3
          host: 34.94.106.61
        - accountId: 0.0.4
          host: 35.237.119.55
        - accountId: 0.0.5
          host: 35.245.27.193
        - accountId: 0.0.6
          host: 34.83.112.116
      operator:
        accountId: 0.0.1234
        privateKey: 104...
```

### Publish

The monitor can be configured to publish multiple transaction types concurrently. It does this by specifying a list of
scenarios that should actively publish. A unique scenario name should be used as a label for logs, metrics, and the REST
API so that each scenario can be monitored separately. Each scenario has a target transactions per second (TPS) and uses
a client-side rate limiter to achieve the desired rate. Additionally, a percentage of receipts or records can be
requested for each transaction to verify transactions are reaching consensus.

The monitor can be used to publish at a very high TPS, with a single monitor able to achieve 10K TPS on the
network. To publish at higher rates, the `hiero.mirror.monitor.publish.clients` and
`hiero.mirror.monitor.publish.responseThreads` properties can be adjusted. With the default values of four clients and
40 response threads, the monitor can already achieve 10K TPS out of the box. The transaction publisher will round-robin
the list of clients to send transactions to the network, and every transaction is sent to a randomly chosen node,
ensuring the load is distributed evenly across all nodes.

The `type` property specifies which transaction type to publish. It also affects which `properties` need to be
specified, with different transaction types requiring different properties to be set. See the
[TransactionType](/monitor/src/main/java/org/hiero/mirror/monitor/publish/transaction/TransactionType.java)
enum for a list of possible values for `type`. The properties can be seen as fields on the various
[TransactionSupplier](/monitor/src/main/java/org/hiero/mirror/monitor/publish/transaction) classes that
the `TransactionType` enum references. Most of these properties have a default and don't need to be explicitly
specified, but some are empty and may need to be populated.

For example, if you want to publish a topic message, you would open the `TransactionType` class,
find `CONSENSUS_SUBMIT_MESSAGE`, then open the ConsensusSubmitMessageTransactionSupplier
class that it references. From there, you can see that fields `maxTransactionFee`, `message`, `retry`, and `topicId` are
available as properties. Only `topicId` doesn't have a default and will be required. Here's a YAML excerpt that
specifies some of those properties:

```yaml
hiero:
  mirror:
    monitor:
      publish:
        scenarios:
          pinger: # Scenario name
            properties:
              maxTransactionFee: 1000000
              message: Hello world!
              retry: true
              topicId: 0.0.1000
            recordPercent: 1.0
            tps: 0.1
            type: CONSENSUS_SUBMIT_MESSAGE
```

Some properties, such as `transferTypes` in the CryptoTransferTransactionSupplier, are Collections, which requires the
YAML list syntax.

#### Scheduled Transactions

Scheduled transactions require unique configuration as they encompass two transactions during the entity creation. One (
outer) transaction for the `Schedule` entity create and one (inner/scheduled) transaction to eventually be executed. The
inner transaction must be signed by all required signatories as part of the `ScheduleCreate` or `ScheduleSign` scenario
transactions. Due to this the monitor by default will initially support only `ScheduleCreate` scenarios in which the
inner transaction is a `CryptoCreate` with `receiverSignatureRequired` set to true.

By default, all required signatures will be provided. However, this can be modified by
setting `hiero.mirror.monitor.publish.scenarios.properties.signatoryCount` to be a number greater than 0 but smaller
than `hiero.mirror.monitor.publish.scenarios.properties.totalSignatoryCount`
To execute a scheduled scenario set the `hiero.mirror.monitor.publish.scenarios` properties similar to the following
example.

```yaml
hiero:
  mirror:
    monitor:
      publish:
        scenarios:
          scheduledCryptoCreate:
            properties:
              nodeAccountId: 0.0.3
              operatorAccountId: 0.0.1018
              receiverSignatureRequired: true
              signatoryCount: 4
              totalSignatoryCount: 5
            type: SCHEDULE_CREATE
```

#### NFTs

NFT (Non-Fungible Token) transactions use the same `TransactionSuppliers` as Fungible Token transactions, but they
require that the serial number(s) to be operated upon be specified, as opposed to Fungible tokens where one simply sets
an amount to be operated on. Because most operations can only occur once for a given serial number (a serial number can
only be burned/wiped once, and it can only be transferred from Alice to Bob once without transferring it back), the
monitor requires a sufficient amount of serial numbers be minted prior to running any `CRYPTO_TRANSFER`
, `TOKEN_BURN`, or `TOKEN_WIPE` scenario involving the NFT. A `TOKEN_MINT` scenario can be set up to accomplish this. If
you are reusing an existing NFT, be sure to set the `serialNumber` property where applicable, as by default
the `TransactionSupplier` will start with serial number 1, which may have already been deleted or transferred elsewhere.

```yaml
hiero:
  mirror:
    monitor:
      publish:
        scenarios:
          nftMint:
            properties:
              type: NON_FUNGIBLE_UNIQUE
              tokenId: ${nft.1}
              amount: 1
            type: TOKEN_MINT
            tps: 105
          nftTransfer:
            properties:
              recipientAccountId: ${account.1}
              senderAccountId: ${account.2}
              transferTypes:
                - NFT
              amount: 1
              nftTokenId: ${nft.1}
            tps: 100
            type: CRYPTO_TRANSFER
```

### Expression Syntax

The monitor can automatically create account, token, and topic entities on application startup using a special
expression syntax. This is useful to avoid boilerplate configuration and manual entity creation steps that vary per
environment. The syntax can currently only be used in `hiero.mirror.monitor.publish.scenarios.properties`
and `hiero.mirror.monitor.subscribe.grpc.topicId`.

The syntax takes the form of `${type.name}` where `type` is one of `account`, `schedule`, `token`, or `topic`,
and `name` is a descriptive label. Based upon the entity type, it will create the appropriate entity on the network with
default values. The name label allows the same entity to be referenced in multiple places but only created once.

The following example uses the expression syntax to create the sender and recipient accounts as well as a token. These
same entities are created once and reused in both the token associate and the token transfer transaction.

```yaml
hiero:
  mirror:
    monitor:
      publish:
        scenarios:
          htsAssociate:
            limit: 1
            properties:
              accountId: ${account.them}
              tokenId: ${token.foobar}
            tps: 1
            type: TOKEN_ASSOCIATE
          htsTransfer:
            properties:
              recipientAccountId: ${account.them}
              senderAccountId: ${account.me}
              tokenId: ${token.foobar}
              transferType: BOTH
            tps: 1
            type: CRYPTO_TRANSFER
```

### Subscribe

The monitor can optionally subscribe to the mirror node gRPC and REST APIs simultaneously. Each subscription type can
have one or more scenarios. For the REST API, it can verify that a percentage of individual transactions have made it to
the mirror node by querying the `/api/v1/transactions/{transactionId}` REST endpoint. The exact percentage to verify is
controlled via the `samplePercent` property.

For gRPC, `topicId` is required and controls which topic should be registered for asynchronous notifications of topic
messages. Below is an example of both types:

```yaml
hiero:
  mirror:
    monitor:
      subscribe:
        clients: 4
        grpc:
          hcs:
            enabled: true
            subscribers: 10
            topicId: ${topic.ping}
        rest:
          transactionId:
            enabled: false
            samplePercent: 1.0
```

For performance testing subscribers, the `hiero.mirror.monitor.subscribe.clients` property should be adjusted higher to
control the pool of client connections to the server. Since the communication is asynchronous, a number between 1-10
should suffice. Additionally, the
`hiero.mirror.monitor.subscribe.grpc.subscribers` property can be adjusted to increase the number of concurrent
subscribers for that scenario.

## REST API

The monitor REST API provides a way to query the status of the scenarios currently publishing and subscribing to various
Hiero APIs. The monitor [OpenAPI](https://www.openapis.org) specification is available at `/api/v1/docs/openapi`
and `/api/v1/docs/openapi.yaml`. The [Swagger UI](https://swagger.io/tools/swagger-ui) is also available
at `/api/v1/docs`. This UI provides a form of documentation, and an interactive way to explore the monitor's REST API.

### Get Subscribers

Lists all subscriber scenarios. If no scenarios can be found with the given input a 404 status code will be returned.
A `protocol` and `status` query parameter can be optionally supplied. The `protocol` query parameter can be
either `GRPC` or `REST`. The `status` flag can be one of the below:

- `COMPLETED`: The scenario has completed normally due to reaching the configured duration or limit
- `IDLE`: The scenario has not completed but is not currently receiving any responses
- `RUNNING`: The scenario is still actively receiving responses

`GET /api/v1/subscriber`

Example response:

```json
[
  {
    "count": 74,
    "status": "RUNNING",
    "errors": {},
    "elapsed": "PT12M14.906846353S",
    "rate": 0.2,
    "name": "HCS Subscribe",
    "id": 1,
    "protocol": "GRPC"
  },
  {
    "count": 72,
    "status": "RUNNING",
    "errors": {
      "TimeoutException": 1
    },
    "elapsed": "2d3h3s",
    "rate": 0.1,
    "name": "HCS REST",
    "id": 1,
    "protocol": "REST"
  }
]
```

### Get Subscribers by Name

Lists all subscriber scenarios with a given name. Also supports the aforementioned `status` query parameter. If no
scenarios can be found with the given input a 404 status code will be returned.

`GET /api/v1/subscriber/{name}`

Example response:

```json
[
  {
    "count": 72,
    "status": "RUNNING",
    "errors": {
      "TimeoutException": 1
    },
    "elapsed": "1h2m57s",
    "rate": 0.1,
    "name": "HCS REST",
    "id": 1,
    "protocol": "REST"
  }
]
```

### Get Subscriber

Gets a subscriber scenario by its unique name and index. If no scenario can be found with the given input a 404 status
code will be returned.

`GET /api/v1/subscriber/{name}/{index}`

Example response:

```json
{
  "count": 72,
  "status": "RUNNING",
  "errors": {
    "TimeoutException": 1
  },
  "elapsed": "12m13s",
  "rate": 0.1,
  "name": "HCS REST",
  "id": 1,
  "protocol": "REST"
}
```

## Dashboard & Metrics

The monitor includes a Grafana [dashboard](/charts/hedera-mirror-common/dashboards/hedera-mirror-monitor.json) that
displays the various metrics collected by the application visually. The monitor uses [Micrometer](http://micrometer.io)
to export the metrics to [Prometheus](https://prometheus.io). The Prometheus metrics can be scraped
at `http://localhost:8082/actuator/prometheus`.

![Grafana Dashboard](https://user-images.githubusercontent.com/17552371/101863000-e4eee180-3b38-11eb-88b8-7841718db2f7.png)
