# Smart Contracts

## Purpose

Smart contracts have existed since open access, but the mirror node has never stored all the data associated
with smart contract transactions in its database. With the
[announcement](https://hedera.com/blog/hedera-evm-smart-contracts-now-bring-highest-speed-programmability-to-tokenization)
to bring high speed smart contract execution to the network, it has become more important to ensure the mirror
node is storing the appropriate smart contract information and making it retrievable via its APIs.

## Goals

- Enhance the database schema to store all contract-related information from transactions and transaction records
- Enhance the REST API to retrieve smart contracts and their execution results
- Enhance the REST API to search by smart contract log topics
- Explore alternative smart contract APIs including compatibility
  with [Ethereum JSON-RPC](https://ethereum.org/en/developers/docs/apis/json-rpc/) APIs

## Non-Goals

- Ensure 100% compatibility with all Ethereum JSON-RPC APIs
- Execute smart contracts on the mirror node

## Architecture

### Database

#### Contract

Create a contract table that has most of the same fields as the entity table. A database migration should move entries
in `entity` into `contract` if they are of type contract or have contract create or update transactions. The
contract-specific fields will need to be marked as nullable since we didn't store them on any existing tables.

```sql
create table if not exists contract
(
  auto_renew_period    bigint                         null,
  created_timestamp    bigint                         null,
  deleted              boolean                        null,
  evm_address          bytea                          null,
  expiration_timestamp bigint                         null,
  file_id              bigint                         null,
  id                   bigint                         not null,
  key                  bytea                          null,
  memo                 text        default ''         not null,
  num                  bigint                         not null,
  obtainer_id          bigint                         null,
  proxy_account_id     bigint                         null,
  public_key           character varying              null,
  realm                bigint                         not null,
  shard                bigint                         not null,
  timestamp_range      int8range                      not null,
  type                 entity_type default 'CONTRACT' not null,
  primary key (id)
);
```

#### Contract History

Create a contract history table that is populated by application upsert logic. It should insert the old row to the
history table after setting its timestamp range to end (exclusively) at the new row's start consensus timestamp.

```sql
create table if not exists contract_history
(
  like contract including defaults,
  primary key (id, timestamp_range)
);

create index if not exists contract_history__timestamp_range on contract_history using gist (timestamp_range);
```

#### Contract Result

Update the existing `contract_result` to capture all fields present in the protobuf (see below).
Replace `consensus_timestamp` index with `consensus_timestamp` primary key. Migrate data in `call_result` to parse it
using the protobuf and normalize it into the other fields.

```sql
create table if not exists contract_result
(
  account_access       bigint array       null,
  amount               bigint             null,
  bloom                bytea              null,
  call_result          bytea              null,
  consensus_timestamp  bigint primary key not null,
  contract_id          bigint             null,
  created_contract_ids bigint array       null,
  error_message        text               null,
  function_parameters  bytea              not null,
  function_result      bytea              null,
  gas_consumed         bigint             null,
  gas_limit            bigint             not null,
  gas_used             bigint             not null,
  payer_account_id     bigint             not null,
  primary key (consensus_timestamp)
);

create index if not exists contract_result__id_payer_timestamp
  on contract_result (contract_id, payer_account_id, consensus_timestamp);
```

#### Contract Log

Create a new table to store the results of the contract's log output.

```sql
create table if not exists contract_log
(
  bloom               bytea  not null,
  consensus_timestamp bigint not null,
  contract_id         bigint not null,
  data                bytea  not null,
  index               int    not null,
  payer_account_id    bigint not null,
  root_contract_id    bigint null,
  topic0              bytea  null,
  topic1              bytea  null,
  topic2              bytea  null,
  topic3              bytea  null,
  primary key (consensus_timestamp, index)
);

create index if not exists contract_log__id_timestamp
  on contract_log (contract_id, consensus_timestamp);
```

#### Contract Access List

Create a new table to store the access list of a contract execution

```sql
create table if not exists contract_access
(
  consensus_timestamp bigint      not null,
  contract_id         bigint      not null,
  initial_contract_id bigint      not null,
  payer_account_id    bigint      not null,
  storage_keys        bytea array not null,
  primary key (consensus_timestamp, initial_contract_id, contract_id)
);
```

#### Contract State Change

Create a new table to store the state changes of a contract execution

```sql
create table if not exists contract_state_change
(
  consensus_timestamp bigint not null,
  contract_id         bigint not null,
  payer_account_id    bigint not null,
  slot                bytea  not null,
  value_read          bytea  not null,
  value_written       bytea  null,
  primary key (consensus_timestamp, contract_id, slot)
);
```

### Importer

- Add a `Contract` domain object with fields that match the schema.
- Add a `ContractAccess` domain object with fields that match the schema.
- Add a `ContractLog` domain object with fields that match the schema.
- Update the `ContractResult` domain object with fields that match the schema.
- Add a `ContractStateChange` domain object with fields that match the schema.
- Add a `ContractRepository` and `ContractLogRepository`.
- Add `EntityListener.onContract(Contract)` and `EntityListener.onContractLog(ContractLog)`.
- Add `EntityListener.onContractAccess(ContractAccess)`
  and `EntityListener.onContractStateChange(ContractStateChange)`.
- Add logic to create a `Contract` domain object in create, update, and delete contract transaction handlers and notify
  via `EntityListener`.
- Add logic to create a `ContractAccess`, `ContractLog`, `ContractResult` and `ContractStateChange` domain objects in
  the contract create and contract call transaction handlers and notify via `EntityListener`.
- Add logic to `SqlEntityListener` to batch insert `Contract` and `ContractLog`.
- Implement a generic custom `UpsertQueryGenerator` that generates the insert query entirely from annotations on
  the `Contract` domain object.
- Remove logic specific to contracts in `EntityRecordItemListener`.

### REST API

#### List Contracts

`GET /api/v1/contracts`

```json
{
  "contracts": [
    {
      "admin_key": {
        "_type": "ProtobufEncoded",
        "key": "7b2233222c2233222c2233227d"
      },
      "auto_renew_period": 7776000,
      "contract_id": "0.0.10001",
      "created_timestamp": "1633466568.31556926",
      "deleted": false,
      "evm_address": "0x0000000000000000000000000000000000001001",
      "expiration_timestamp": null,
      "file_id": 1000,
      "memo": "First contract",
      "obtainer_id": null,
      "proxy_account_id": "0.0.100",
      "timestamp": {
        "from": "1633466568.31556926",
        "to": null
      }
    }
  ],
  "links": {
    "next": null
  }
}
```

Optional filters

- `contract.id` Supports all comparison operators and repeated equality parameters to generate an `IN` clause
- `limit`
- `order`

#### Get Contract

`GET /api/v1/contracts/{id}`

```json
{
  "admin_key": {
    "_type": "ProtobufEncoded",
    "key": "7b2233222c2233222c2233227d"
  },
  "auto_renew_period": 7776000,
  "bytecode": "0xc896c66db6d98784cc03807640f3dfd41ac3a48c",
  "contract_id": "0.0.10001",
  "created_timestamp": "1633466229.96874612",
  "deleted": false,
  "evm_address": "0x0000000000000000000000000000000000001001",
  "expiration_timestamp": null,
  "file_id": "0.0.1000",
  "memo": "First contract",
  "obtainer_id": "0.0.101",
  "proxy_account_id": "0.0.100",
  "timestamp": {
    "from": "1633466229.96874612",
    "to": "1633466568.31556926"
  }
}
```

Optional filters

- `timestamp` Return the historical state of the contract. Supports all the operators but returns the latest version of
  the contract within that time range.

#### List Contract Results

`GET /api/v1/contracts/{id}/results`

```json
{
  "results": [
    {
      "amount": 10,
      "bloom": "0x549358c4c2e573e02410ef7b5a5ffa5f36dd7398",
      "call_result": "0x2b048531b38d2882e86044bc972e940ee0a01938",
      "contract_id": "0.0.1002",
      "created_contract_ids": ["0.0.1003"],
      "error_message": "",
      "from": "0x0000000000000000000000000000000000001001",
      "function_parameters": "0xbb9f02dc6f0e3289f57a1f33b71c73aa8548ab8b",
      "gas_consumed": 3000,
      "gas_limit": 10000,
      "gas_used": 8000,
      "timestamp": "12345.10001",
      "to": "0x0000000000000000000000000000000000001002"
    }
  ],
  "links": {
    "next": null
  }
}
```

> _Note:_ Each contract run result object maps closely to the EVM
> [transaction receipt object](https://besu.hyperledger.org/en/stable/Reference/API-Objects/#transaction-receipt-object)

Optional filters

- `limit` Maximum limit will be configurable and lower than current global max limit
- `order`
- `timestamp`
- `from`

#### Get Contract Result

`GET /api/v1/contracts/{id}/results/{timestamp}` & `GET /api/v1/contracts/results/{transactionId}`

```json
{
  "amount": 10,
  "access_list": [
    {
      "address": "0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae",
      "storage_keys": [
        "0x0000000000000000000000000000000000000000000000000000000000000003"
      ]
    },
    {
      "address": "0xbb9bc244d798123fde783fcc1c72d3bb8c189413",
      "storage_keys": [
        "0x0000000000000000000000000000000000000000000000000000000000000007"
      ]
    }
  ],
  "block_hash": "0x410ef7b5a5f",
  "block_number": 50,
  "bloom": "0x549358c4c2e573e02410ef7b5a5ffa5f36dd7398",
  "call_result": "0x2b048531b38d2882e86044bc972e940ee0a01938",
  "contract_id": "0.0.1002",
  "created_contract_ids": ["0.0.1003"],
  "error_message": "",
  "from": "0x0000000000000000000000000000000000001001",
  "function_parameters": "0xbb9f02dc6f0e3289f57a1f33b71c73aa8548ab8b",
  "gas_consumed": 3000,
  "gas_limit": 10000,
  "gas_used": 8000,
  "hash": "0x5b2e3c1a49352f1ca9fb5dfe74b7ffbbb6d70e23a12693444e26058d8a8e6296",
  "logs": [
    {
      "address": "0x0000000000000000000000000000000000001f41",
      "contract_id": "0.0.8001",
      "data": "0x8f705727c88764031b98fc32c314f8f9e463fb62",
      "index": 0,
      "topics": [
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
        "0x59d088293f09d5119d5b55858b989ffce4d398dc"
      ]
    },
    {
      "address": "0x0000000000000000000000000000000000001f42",
      "contract_id": "0.0.8002",
      "data": "0x1513001083c899b1996ec7fa33621e2c340203f0",
      "index": 1,
      "topics": [
        "0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0",
        "0x0000000000000000000000000000000000000000000000000000000000000765"
      ]
    }
  ],
  "state_changes": [
    {
      "address": "0x0000000000000000000000000000000000001f41",
      "contract_id": "0.0.8001",
      "slot": "0x0000000000000000000000000000000000000000000000000000000000000002",
      "value_read": "0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0",
      "value_written": "0x000000000000000000000000000000000000000000c2a8c408d0e29d623347c5"
    },
    {
      "address": "0x0000000000000000000000000000000000001f42",
      "contract_id": "0.0.8002",
      "slot": "0xe1b094dec1b7d360498fa8130bf1944104b7b5d8a48f9ca88c3fc0f96c2d7225",
      "value_read": "0x000000000000000000000000000000000000000000000001eafa3aaed1d27246",
      "value_written": null
    }
  ],
  "timestamp": "12345.10001",
  "to": "0x0000000000000000000000000000000000001002"
}
```

- `access_list` should be retrieved by a join between the `contract_result` and `contract_access_list` tables.
- `block_hash` should be retrieved by a join with the `record_file` table to find the `hash` of the file containing the
  transaction.
- `hash` should be retrieved by a join with the `transaction` table
- `logs` should be retrieved by a join between the `contract_result` and `contract_log` tables.
- `state_changes` should be retrieved by a join between the `contract_result` and `contract_state_change` tables.

> _Note:_ `/api/v1/contracts/results/{transactionId}` will have to extract the correlating contractId and timestamp to
> retrieve the correct contract_result row

#### Get Contract Logs

`GET /api/v1/contracts/{id}/results/logs`

```json
{
  "logs": [
    {
      "address": "0x0000000000000000000000000000000000001234",
      "contract_id": "0.0.4660",
      "data": "0x8f705727c88764031b98fc32c314f8f9e463fb62",
      "index": 0,
      "root_contract_id": "0.0.1",
      "timestamp": "12345.10002",
      "topics": [
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
        "0x59d088293f09d5119d5b55858b989ffce4d398dc"
      ]
    },
    {
      "address": "0x0000000000000000000000000000000000001234",
      "contract_id": "0.0.4660",
      "bloom": "0x8f705727c88764031b98fc32c314f8f9e463fb62",
      "data": "0x1513001083c899b1996ec7fa33621e2c340203f0",
      "index": 1,
      "root_contract_id": "0.0.2",
      "timestamp": "12345.10002",
      "topics": [
        "af846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0",
        "0000000000000000000000000000000000000000000000000000000000000765"
      ]
    }
  ]
}
```

Optional filters

- `limit` Maximum limit will be configurable and lower than current global max limit
- `order`
- `timestamp`
- `index`
- `topic0`
- `topic1`
- `topic2`
- `topic3`

> _Note:_ This API will not have links, as it requires two parameters to page, `consensus_timestamp` for logs from
> different `contract_results` and `index` for when logs from a `contract_result` go on to the next page.

> _Note2:_ In order to support searching on a topic, this API will require a timestamp equals operator or a timestamp
> range (greater than and less than operators) be provided as well when searching on a topic so that indexes are not
> required on all four topics.

> _Note3:_ This API will only return logs for the given contract id. It will not return logs
> generated by a child or parent contract.

### JSON-RPC

On the Ethereum network, all client nodes implement
the [Ethereum JSON-RPC Specification](https://playground.open-rpc.org/?schemaUrl=https://raw.githubusercontent.com/ethereum/eth1.0-apis/assembled-spec/openrpc.json)
methods for ease of interaction by DApps.

The HyperLedger Besu EVM supports the methods captured
at [ETH methods](https://besu.hyperledger.org/en/stable/Reference/API-Methods/#eth-methods)

The Mirror Node should implement a subset of the standard calls used to:

- Support existing Ethereum developers who may call the JSON-RPC endpoints directly.
- Encompass EVM translation logic that can be wrapped by potential Web3 modules.

#### Setup

- Create a new Maven module `web3`
- Create a new Maven module `common` that encompasses all domain POJOs and repositories
- Use Spring WebFlux to establish a JSON-RPC server
  with [JSON-RPC 2.0 specification](https://www.jsonrpc.org/specification) support to service rpc calls
- Use `spring-boot-starter-data-jpa` for database access
- Create a Helm child chart `hedera-mirror-web3` and add to Kubernetes deployment flow
- Add to CI and utilize a Postman collection for endpoint verification

#### Domain/Repository

Existing domain classes can be utilized from the `common` dependencies. Applicable CRUD repositories can
be created using Spring based on `common` domains to extract information from the database.

#### Service

- `Web3Service` interface that describes the supported rpc methods
- Implement `Web3Service` for each of the supported RPC methods. Methods query the appropriate tables and return data in
  the expected format.

#### Request

Requests are typically of the below JSON format:

```json
{
  "id": 1,
  "jsonrpc": "2.0",
  "method": "",
  "params": []
}
```

Its corresponding model:

```java
class JsonRpcRequest<T> {
  private Long id;
  private String jsonrpc;
  private String method;
  private T params;
}
```

#### Response

Responses are typically of the standard [JSON-RPC format](https://www.jsonrpc.org/specification#response_object).

- Successful response

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": "0x1"
  }
  ```

- Failed response
  ```json
  {
    "error": {
      "code": -32600,
      "data": "id field must not be null",
      "message": "Invalid Request"
    },
    "id": 1,
    "jsonrpc": "2.0"
  }
  ```

> _Note:_ `result` data type is dynamic. Either `result` or `error` must be present in a response, not both.

An appropriate set of models would be:

- `JsonRpcResponse`

  ```java
  @Data
  class JsonRpcSuccessResponse<T> {
    private Long id;
    private final String jsonrpc = "2.0";
    private T result;
  }
  ```

- `JsonRpcErrorResponse`

  ```java
  class JsonRpcErrorResponse {
    private Long id;
    private final String jsonrpc = "2.0";
    private JsonRpcError error;

    private class JsonRpcError {
      private int code;
      private String data;
      private String message;
    }
  }
  ```

The result field will be populated with the value to be returned. Additional classes per complex response value should
be added. The value can range from regular data types (String, int, array) to defined Ethereum objects such as:

- [Block](https://besu.hyperledger.org/en/stable/Reference/API-Objects/#block-object)
- [Log](https://besu.hyperledger.org/en/stable/Reference/API-Objects/#log-object)
- [Transaction](https://besu.hyperledger.org/en/stable/Reference/API-Objects/#transaction-object)

#### ETH Method Analysis

The Mirror Node is essentially a full archive node (i.e. it can provide the state of the ledger at present but also at
multiple snapshots of time). However, its current implementation is limited by the lack of connectivity to the actual
Gossip based network of consensus nodes. As a result some network specific details are not available to it unless
provided through the record stream and therefore some `eth_` related methods may not be applicable to its current
iteration. Below is a table of compatibility. All methods are referenced from the Ethereum
Wikis [JSON-RPC API](https://eth.wiki/json-rpc/API)

| Method                                                                                                           | Description                                                                                        | Mirror Node Support Priority | Justification                                                                                                                           |
| ---------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| [eth_accounts](https://eth.wiki/json-rpc/API#eth_accounts)                                                       | Returns a list of addresses owned by client.                                                       | N/A                          | Mirror node is not an Ethereum client                                                                                                   |
| [eth_blockNumber](https://eth.wiki/json-rpc/API#eth_blocknumber)                                                 | Returns the index corresponding to the block number of the current chain head.                     | P1\*                         | Mirror node is able to return the current record file count. However, this may be inaccurate depending on network load.                 |
| [eth_call](https://eth.wiki/json-rpc/API#eth_call)                                                               | Invokes a contract function locally and does not change the state of the blockchain.               | N/A                          | Mirror node is not an Ethereum client                                                                                                   |
| [eth_coinbase](https://eth.wiki/json-rpc/API#eth_coinbase)                                                       | Returns the client coinbase address. The coinbase address is the account to pay mining rewards to. | N/A                          | Mirror node is not an Ethereum client                                                                                                   |
| [eth_estimateGas](https://eth.wiki/json-rpc/API#eth_estimategas)                                                 | Returns an estimate of the gas required for a transaction to complete.                             | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_gasPrice](https://eth.wiki/json-rpc/API#eth_gasprice)                                                       | Returns a percentile gas unit price for the most recent blocks, in Wei.                            | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_getBalance](https://eth.wiki/json-rpc/API#eth_getbalance)                                                   | Returns the account balance of the specified address.                                              | P1\*                         | Mirror node can return an accounts balance. However, as of receipt it may be stale for up to 15 mins due to balance file parse rate.    |
| [eth_getBlockByHash](https://eth.wiki/json-rpc/API#eth_getblockbyhash)                                           | Returns information about the block by hash.                                                       | P2                           | Mirror node is able to return record file information. Block info will be secondary to transactions.                                    |
| [eth_getBlockByNumber](https://eth.wiki/json-rpc/API#eth_getblockbynumber)                                       | Returns information about a block by block number.                                                 | P2                           | Mirror node is able to return record file information. Block info will be secondary to transactions.                                    |
| [eth_getBlockTransactionCountByHash](https://eth.wiki/json-rpc/API#eth_getblocktransactioncountbyhash)           | Returns the number of transactions in the block matching the given block hash.                     | P2                           | Mirror node is able to return record file information. Block info will be secondary to transactions.                                    |
| [eth_getBlockTransactionCountByNumber](https://eth.wiki/json-rpc/API#eth_getblocktransactioncountbynumber)       | Returns the number of transactions in a block matching the specified block number.                 | P2                           | Mirror node is able to return record file information. Block info will be secondary to transactions.                                    |
| [eth_getCode](https://eth.wiki/json-rpc/API#eth_getcode)                                                         | Returns the code of the smart contract at the specified address.                                   | P0                           | Mirror node is able to return contract bytes from file_data table.                                                                      |
| [eth_getFilterChanges](https://eth.wiki/json-rpc/API#eth_getfilterchanges)                                       | Polls the specified filter and returns an array of changes that have occurred since the last poll. | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |
| [eth_getFilterLogs](https://eth.wiki/json-rpc/API#eth_getfilterlogs)                                             | Returns an array of logs for the specified filter.                                                 | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |
| [eth_getLogs](https://eth.wiki/json-rpc/API#eth_getlogs)                                                         | Returns an array of logs matching a specified filter object.                                       | P1                           | Mirror node is able to return contract log rows based on filter parameters.                                                             |
| [eth_getStorageAt](https://eth.wiki/json-rpc/API#eth_getstorageat)                                               | Returns the value of a storage position at a specified address.                                    | P2                           | Mirror node is able to return the contract_state row based on the filter parameters.                                                    |
| [eth_getTransactionByBlockHashAndIndex](https://eth.wiki/json-rpc/API#eth_gettransactionbyblockhashandindex)     | Returns transaction information for the specified block hash and transaction index position.       | P2                           | Mirror node can return contract transaction details mapped by record hash and transaction count.                                        |
| [eth_getTransactionByBlockNumberAndIndex](https://eth.wiki/json-rpc/API#eth_gettransactionbyblocknumberandindex) | Returns transaction information for the specified block number and transaction index position.     | P2                           | Mirror node can return contract transaction details mapped by record number and transaction count.                                      |
| [eth_getTransactionByHash](https://eth.wiki/json-rpc/API#eth_gettransactionbyhash)                               | Returns transaction information for the specified transaction hash.                                | P1                           | Mirror node can return contract transaction metadata details mapped by transaction hash.                                                |
| [eth_getTransactionCount](https://eth.wiki/json-rpc/API#eth_gettransactioncount)                                 | Returns the number of transactions sent from a specified address.                                  | P2                           | Mirror node can return contract transaction count from the specified contract address.                                                  |
| [eth_getTransactionReceipt](https://eth.wiki/json-rpc/API#eth_gettransactionreceipt)                             | Returns the receipt of a transaction by transaction hash.                                          | P0                           | Mirror node can return contract transaction details mapped by transaction hash.                                                         |
| [eth_getUncleByBlockHashAndIndex](https://eth.wiki/json-rpc/API#eth_getunclebyblockhashandindex)                 | Returns uncle specified by block hash and index.                                                   | N/A                          | Hiero has no concept of uncles. Gossip protocol avoids this pitfall.                                                                    |
| [eth_getUncleByBlockNumberAndIndex](https://eth.wiki/json-rpc/API#eth_getunclebyblocknumberandindex)             | Returns uncle specified by block number and index.                                                 | N/A                          | Hiero has no concept of uncles. Gossip protocol avoids this pitfall.                                                                    |
| [eth_getUncleCountByBlockHash](https://eth.wiki/json-rpc/API#eth_getunclecountbyblockhash)                       | Returns the number of uncles in a block from a block matching the given block hash.                | N/A                          | Hiero has no concept of uncles. Gossip protocol avoids this pitfall.                                                                    |
| [eth_getUncleCountByBlockNumber](https://eth.wiki/json-rpc/API#eth_getunclecountbyblocknumber)                   | Returns the number of uncles in a block matching the specified block number.                       | N/A                          | Hiero has no concept of uncles. Gossip protocol avoids this pitfall.                                                                    |
| [eth_getWork](https://eth.wiki/json-rpc/API#eth_getwork)                                                         | Returns the hash of the current block, the seed hash, and the required target boundary condition.  | N/A                          | Hiero uses the Gossip about Gossip protocol which is not proof of work based.                                                           |
| [eth_hashrate](https://eth.wiki/json-rpc/API#eth_hashrate)                                                       | Returns the number of hashes per second with which the node is mining.                             | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_mining](https://eth.wiki/json-rpc/API#eth_mining)                                                           | Whether the client is actively mining new blocks.                                                  | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_newBlockFilter](https://eth.wiki/json-rpc/API#eth_newblockfilter)                                           | Creates a filter to retrieve new block hashes.                                                     | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |
| [eth_newFilter](https://eth.wiki/json-rpc/API#eth_newfilter)                                                     | Creates a log filter.                                                                              | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |
| [eth_newPendingTransactionFilter](https://eth.wiki/json-rpc/API#eth_newpendingtransactionfilter)                 | Creates a filter to retrieve new pending transactions hashes.                                      | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |
| [eth_protocolVersion](https://eth.wiki/json-rpc/API#eth_protocolversion)                                         | Returns current Ethereum protocol version.                                                         | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_sendRawTransaction](https://eth.wiki/json-rpc/API#eth_sendrawtransaction)                                   | Sends a signed transaction.                                                                        | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_sign](https://eth.wiki/json-rpc/API#eth_sign)                                                               | Returns an EIP-191 signature over the provided data                                                | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_signTransaction](https://eth.wiki/json-rpc/API#eth_signtransaction)                                         | Returns and RLP encoded transaction signed by the specified account                                | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_submitHashrate](https://eth.wiki/json-rpc/API#eth_submithashrate)                                           | Submits the mining hashrate.                                                                       | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_submitWork](https://eth.wiki/json-rpc/API#eth_submitwork)                                                   | Submits a Proof of Work (Ethash) solution.                                                         | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_syncing](https://eth.wiki/json-rpc/API#eth_syncing)                                                         | Returns an object with data about the synchronization status, or false if not synchronizing.       | N/A                          | Mirror node is not an EVM bearing client                                                                                                |
| [eth_uninstallFilter](https://eth.wiki/json-rpc/API#eth_uninstallfilter)                                         | Uninstalls a filter with the specified ID.                                                         | N/A                          | Mirror node REST APIs should be stateless and will not support persisting filters. Instead, desired filter should be applied to getLogs |

> _Note:_ Methods may become applicable over time should the Mirror Node become connected to a gossip based network or
> should the REST APIs move from a stateless to stateful design for items such as search

#### RPC Methods

Methods marked with P0 or P1 support serve as a starting subset of Ethereum JSON RPC API methods to be implemented.

- `blockNumber`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"blockNumber","params":[],"id":83}'
  ```

  Response

  ```json
  {
    "id": 83,
    "jsonrpc": "2.0",
    "result": "0x4b7"
  }
  ```

- `getBalance`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"getBalance","params":["0x407d73d8a49eeb85d32cf465507dd71d507100c1", "latest"],"id":1}'
  ```

  Response

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": "0x0234c8a3397aab58"
  }
  ```

- `getCode`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"getCode","params":["0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b", "0x2"],"id":1}'
  ```

  Response

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": "0x600160008035811a818181146012578301005b601b6001356025565b8060005260206000f25b600060078202905091905056"
  }
  ```

- `getLogs`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"getLogs","params":[{ "topics": ["0x000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b"]}],"id":74}'
  ```

  Response

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": [
      {
        "logIndex": "0x1",
        "blockNumber": "0x1b4",
        "blockHash": "0x8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcfdf829c5a142f1fccd7d",
        "transactionHash": "0xdf829c5a142f1fccd7d8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcf",
        "transactionIndex": "0x0",
        "address": "0x16c5785ac562ff41e2dcfdf829c5a142f1fccd7d",
        "data": "0x0000000000000000000000000000000000000000000000000000000000000000",
        "topics": [
          "0x59ebeb90bc63057b6515673c3ecf9438e5058bca0f92585014eced636878c9a5"
        ]
      }
    ]
  }
  ```

- `getTransactionByHash`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"getTransactionByHash","params":["0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944b"],"id":1}'
  ```

  Response

  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
      "blockHash": "0x1d59ff54b1eb26b013ce3cb5fc9dab3705b415a67127a003c3e61eb445bb8df2",
      "blockNumber": "0x5daf3b",
      "from": "0xa7d9ddbe1f17865597fbd27ec712455208b6b76d",
      "gas": "0xc350",
      "gasPrice": "0x4a817c800",
      "hash": "0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944b",
      "input": "0x68656c6c6f21",
      "nonce": "0x15",
      "to": "0xf02c1c8e6114b1dbe8937a39260b5b0a374432bb",
      "transactionIndex": "0x41",
      "value": "0xf3dbb76162000",
      "v": "0x25",
      "r": "0x1b5e176d927f8e9ab405058b2d2457392da3e20f328b16ddabcebc33eaac5fea",
      "s": "0x4ba69724e8f69de52f0125ad8b3c5c2cef33019bac3249e2c0a2192766d1721c"
    }
  }
  ```

- `getTransactionReceipt`

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"getTransactionReceipt","params":["0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238"],"id":1}'
  ```

  Response

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "transactionHash": "0xb903239f8543d04b5dc1ba6579132b143087c68db1b2168786408fcbce568238",
      "transactionIndex": "0x",
      "blockNumber": "0xb",
      "blockHash": "0xc6ef2fc5426d6ad6fd9e2a26abeab0aa2411b7ab17f30a99d3cb96aed1d1055b",
      "cumulativeGasUsed": "0x33bc",
      "gasUsed": "0x4dc",
      "contractAddress": "0xb60e8dd61c5d32be8058bb8eb970870f07233155",
      "logs": [
        {
          "logIndex": "0x1",
          "blockNumber": "0x1b4",
          "blockHash": "0x8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcfdf829c5a142f1fccd7d",
          "transactionHash": "0xdf829c5a142f1fccd7d8216c5785ac562ff41e2dcfdf5785ac562ff41e2dcf",
          "transactionIndex": "0x0",
          "address": "0x16c5785ac562ff41e2dcfdf829c5a142f1fccd7d",
          "data": "0x0000000000000000000000000000000000000000000000000000000000000000",
          "topics": [
            "0x59ebeb90bc63057b6515673c3ecf9438e5058bca0f92585014eced636878c9a5"
          ]
        }
      ],
      "logsBloom": "0x00...0",
      "status": "0x1"
    }
  }
  ```

#### Custom RPC Methods

In addition to the official Ethereum JSON-RPC Specification, some node clients provide additional methods that provide
additional value to many developers
e.g. [Besu RPC Pubc/Sub](https://besu.hyperledger.org/en/stable/HowTo/Interact/APIs/RPC-PubSub/#rpc-pubsub-over-websockets)
The Mirror Node should additional provide support for this.

- subscribe

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"subscribe","params":["newHeads", {}]},"id":1}'
  ```

  Response

  ```json
  {
    "jsonrpc": "2.0",
    "id": 2,
    "result": "0x1"
  }
  ```

  Notification

  ```json
  {
    "jsonrpc": "2.0",
    "method": "subscription",
    "params": {
      "subscription": "0x1",
      "result": {
        "number": "0x40c22",
        "hash": "0x16af2ee1672203c7ac13ff280822008be0f38e1e5bdc675760015ae3192c0e3a",
        "parentHash": "0x1fcf5dadfaf2ab4d985eb05d40eaa23605b0db25d736610c4b87173bfe438f91",
        "nonce": "0x0000000000000000",
        "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
        "logsBloom": "0x00008000000000080000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000040000000000000000000000000000000000000000001000000000000000000000040000000000000000000000000000000000000400000000010000000000000000100000000000020000000000000000000000000000000000010000000000000000000000000000000000000000000",
        "transactionsRoot": "0x5b2e3c1a49352f1ca9fb5dfe74b7ffbbb6d70e23a12693444e26058d8a8e6296",
        "stateRoot": "0xbe8d3bc58bd982421a3ea8b66753404502df0f464ae78a17661d157c406dd38b",
        "receiptsRoot": "0x81b175ec1f4d44fbbd6ba08f1bd3950663b307b7cb35751c067b535cc0b58f12",
        "miner": "0x0000000000000000000000000000000000000000",
        "difficulty": "0x1",
        "totalDifficulty": "0x7c16e",
        "extraData": "0xd783010600846765746887676f312e372e33856c696e757800000000000000002160f780bb1f61eda045c67cdb1297ba37d8349df8035533cb0cf82a7e45f23f3d72bbec125a9f499b3eb110b7d1918d466cb2ede90b38296cfe2aaf452c513f00",
        "size": "0x3a1",
        "gasLimit": "0x47e7c4",
        "gasUsed": "0x11ac3a",
        "timestamp": "0x592afc24",
        "uncles": [],
        "transactions": [
          "0x419c69d21b14e2e8f911def22bb6d0156c876c0e1c61067de836713043364d6c",
          "0x70a5b2cb2cee6e0b199232a1757fc2a9d6053a4691a7afef8508fd88aeeec703",
          "0x4b3035f1d32339fe1a4f88147dc197a0fe5bbd63d3b9dec2dad96a3b46e4fddd"
        ]
      }
    }
  }
  ```

- unsubscribe

  Request

  ```shell
  curl -X POST --data '{"jsonrpc":"2.0","method":"unsubscribe","params":["0x1"]},"id":1}'
  ```

  Response

  ```json
  {
    "jsonrpc": "2.0",
    "id": 2,
    "result": true
  }
  ```

## Non-Functional Requirements

- Support peak smart contract call TPS (400+)
- Support peak smart contract call gas per second (15 million)
- Support max smart contract call size (6K)
- Support max smart contract call state and output size (~4M gas or 900 KiB)
- Latency remains under 10s end to end at peak contract TPS

## Open Questions

1. What will the externalization of the contract call type in the transaction record look like? Still being designed.
2. How will EVM internal transactions (i.e. non-HTS precompiled child transactions) show up in record stream and will
   they follow a hierarchy that highlights transfer succession or will it be flattened? Still being designed.
3. How should we expose EVM internal transactions under `/api/v1/contracts/{id}/results/{timestamp}`
   & `/api/v1/contracts/results/{transactionId}`? Still being designed. One suggestion under is
   ```json
     "evm_internal_transactions": [
       {
         "from": "0x0000000000000000000000000000000000001002",
         "to": "0x0000000000000000000000000000000000001003",
         "type": "call_0",
         "value": "20"
       }
     ]
   ```

## Answered Questions

1. How should we allow searching by topics or logs? Ans: By topics, with timestamp filter on logs.
2. How will transactions triggered from a smart contract be externalized in the record stream? Ans: Each contract
   triggered transaction will show up as a separate transaction and record with an incremented consensus timestamp and a
   parent timestamp populated.
3. Should we show individual function parameters in a normalized form? Ans: We decided against it at this time as it
   might be a performance concern or require parsing the solidity contract. Can revisit in the future by adding a new
   field with the normalized structure.
4. Should `from`, `to`, `transactionHash` and `blockNumber` be included in `api/v1/contract/{id}/result/{timestamp}`
   as it maps closely to
   Ethereum's [transactionReceipt](https://web3js.readthedocs.io/en/v1.2.11/web3-eth.html#gettransaction)
   Ans: Yes, as we can easily extract from transaction and record tables.
5. Would a custom `api/v1/evm` or `api/v1/eth` endpoint be valuable and needed to provide a separation of concern
   between Hiero and Ethereum logic. Ans: Though valuable to the separation of concern it brings too much overhead, and
   the endpoint themselves aren't known to existing developers. Better to put effort on existing endpoints and JSON-RPC
6. With the use of `transaction_id` to retrieve entity metadata rows should we consider a caching and or db mapping to
   extract the entityId and timestamp? Ans: For now caching and internal db mapping not needed. We'll simply do 2 calls
   to get transaction info and then contract details.
7. How should we expose child transactions under `/api/v1/contracts/{id}/results/{timestamp}`
   and `/api/v1/contracts/results/{transactionId}`? Users can get a flattened list of parent and child transactions
   via `/api/v1/transaction/{transactionId}` and use that to query the contract results.
