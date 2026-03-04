# HIP-1340 Mirror Node EOA Code Delegation - Design Document

## Overview

This design document outlines the implementation plan for supporting HIP-1340 EOA code delegation in the Hiero mirror node.
The implementation will enable extraction, storage, and querying of code delegation data from consensus node transactions,
performing `eth_call`, `eth_estimateGas` and `debug_traceTransaction` transactions with code delegations and querying the
persisted code delegations from the existing REST APIs. The code delegations will be kept as `delegation_address`
in both DB and REST API.

## Goals

- Enhance the database schema to store code delegations
- Ingest code delegation creations via `CryptoCreateTransactionBody` transactions
- Ingest code delegation updates and deletions via `CryptoUpdateTransactionBody` transactions
- Expose code delegation information via the existing account REST APIs with the new `delegation_address` field
- Support efficient querying and pagination of code delegations by account id and timestamp

## Non-Goals

- Validating code delegation logic (code delegations are executed by consensus nodes)
- Simulating code delegation creation, deletion or update in web3 (only execution will be supported)

## Architecture

The HIP-1340 implementation follows the established mirror node architecture pattern:

1. **Transaction Processing**: Code delegation-related transactions are processed by dedicated transaction handlers
2. **Database Storage**: Code delegation data is stored in existing tables with proper indexing for efficient queries
3. **REST APIs**: The existing accounts endpoints expose the new code delegation field called `delegation_address`. The existing contract endpoints will be enhanced to work for EOAs with code delegations as if they are contracts.
4. **Web3 API**: Code delegation executions can be simulated with `eth_call`, `eth_estimateGas` and `debug_traceTransaction`.

## Background

[HIP-1340](https://hips.hedera.com/hip/hip-1340) introduces one of the key features of the Ethereum's Pectra upgrade, the facility of EOA (Externally Owner
Account) Code Delegation in the Hiero network. Code delegations can be created and updated in:

- `CryptoCreateTransactionBody`
- `CryptoUpdateTransactionBody`
- `EthereumTransactionBody` - with the new type 4 Ethereum transactions

Code delegation execution will be identified through `ContractCallTransactionBody` transactions to the address of the EOA
and the transaction will be executed in the context of the EOA. For more detailed explanation, please check HIP-1340.

## Database Schema Design

### Query Requirements

The code delegations will be fetched by contract ID for latest blocks and by (contract ID + timestamp) for historical blocks.

### 1. Entity Table

The existing `entity` and `entity_history` tables need to be altered to store code delegation information in a new column
called `delegation_address`.

```sql
-- add_code_delegations_support.sql

alter table if exists entity
add column if not exists delegation_address bytea null;

alter table if exists entity_history
add column if not exists delegation_address bytea null;
```

Each EOA can have only one `delegation_address` set. To delete it, the address part of the identifier is set to the empty
address - 0x0000000000000000000000000000000000000000.

### 2. Ethereum Table

The existing `ethereum_transaction` table will be altered and a new column will be added - `authorization_list`
of type `jsonb`. It will preserve the `authorization_list` from the Ethereum transaction as a json array:

Example:

```
"authorization_list": [
    {
      "chain_id": "0x01",
      "address": "0x1111111111111111111111111111111111111111",
      "nonce": 5,
      "y_parity": 1,
      "r": "0x2222222222222222222222222222222222222222222222222222222222222222",
      "s": "0x3333333333333333333333333333333333333333333333333333333333333333"
    },
    ...
  ]
```

The `authorization_list` will always be needed as an atomic value and it needs to be returned in its raw form in all REST API
endpoints as described in section "API Changes".

```sql
alter table if exists ethereum_transaction
add column if not exists authorization_list jsonb null default null;
```

## Importer Module Changes

### 1. Domain Models

#### EthereumTransaction.java

```java
public class EthereumTransaction implements Persistable<Long> {

    // This field needs to be added to the existing class.
    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Authorization> authorizationList;
}
```

where `Authorization` needs to be created as follows:

```java
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Authorization {

    private String address;  // Example: "0x1111111111111111111111111111111111111111"
    private String chainId;  // Example: "0x01"
    private Long nonce;      // Example: 5
    private String r;        // Example: "0x2222222222222222222222222222222222222222222222222222222222222222"
    private String s;        // Example: "0x3333333333333333333333333333333333333333333333333333333333333333"
    private Integer yParity; // Example: 1
}
```

#### AbstractEntity.java

```java
public class AbstractEntity implements History {

    // This field needs to be added to the existing class.
    @ToString.Exclude
    private byte[] delegationAddress;
}
```

### 2. Transaction Handler Enhancements

The following transaction handlers will be modified:

- `EthereumTransactionHandler.java` - the `authorization_list` field from the `EthereumTransactionBody` needs to be saved.
  For this purpose the RLP encoded field needs to be parsed and an `Authorization` model needs to be built for each entry
  so it can be properly persisted as a `jsonb` filed in the DB.

A child transaction of the parent Ethereum transaction will be a `CryptoCreateTransaction` or a `CryptoUpdateTransaction`
(depending on whether the affected EOA already exists or not). Their protobufs will be changed to have an additional
`delegation_address` field that will keep the code delegation.

- `CryptoCreateTransactionHandler.java` - read the `delegation_address` field and save it in the entity
- `CryptoUpdateTransactionHandler.java` - read the `delegation_address` field and save it in the entity. If the `delegation_address` is equal to 0x0000000000000000000000000000000000000000, this means deletion of the code delegation.

### 3. Parser

A new `Eip7702EthereumTransactionParser` needs to be defined to handle the new Ethereum 4 transaction type.

```java
@Named
public final class Eip7702EthereumTransactionParser extends AbstractEthereumTransactionParser {

    public static final int EIP7702_TYPE_BYTE = 4;
    private static final byte[] EIP7702_TYPE_BYTES = Integers.toBytes(EIP7702_TYPE_BYTE);
    private static final String TRANSACTION_TYPE_NAME = "7702";
    private static final int EIP7702_TYPE_RLP_ITEM_COUNT = 13;

    public EthereumTransaction decode(byte[] transactionBytes);

    protected byte[] encode(EthereumTransaction ethereumTransaction);
}
```

It will parse the RLP encoded transaction similarly to the existing `Eip2930EthereumTransactionParser`. The only difference
will be that the new transaction type will need to decode the authorizationList field as well.

## API Changes

- `GET /api/v1/accounts`
- `GET /api/v1/accounts/{idOrAliasOrEvmAddress}`

In both API endpoints the returned account model will contain an additional parameter, named `delegation_address`. Its value will be
the persisted code delegation, if any. The format is: `20-byte address`. If a code delegation is not set (equal to `null`
in the DB) or if it was deleted (persisted as address 0x0000000000000000000000000000000000000000 in the DB), the
`delegation_address` field will be returned as `0x` from the REST endpoints above.

- `GET /api/v1/contracts`
- `GET /api/v1/contracts/{contractIdOrAddress}`

These endpoints will not be changed and will continue to work only for contracts.

> The listed below `GET /api/v1/contracts/*` endpoints will be modified so that they work with an EOA account the same way as the
> existing queries with contract id.

In order to do this the existing DB queries need to be enhanced.

- `GET /api/v1/contracts/{contractIdOrAddress}/results/{timestamp}`
- `GET /api/v1/contracts/results/{transactionIdOrHash}`

The returned response will contain the new `authorization_list` json field that comes from the `ethereum_transaction` table.
Only the select part of the existing queries against the `ethereum_transaction` table need to be changed to include the
new column.

- `GET /api/v1/contracts/{contractIdOrAddress}/results`
- `GET /api/v1/contracts/results`

The existing query that is used by both endpoints needs to be left joined with the `ethereum_transaction` table on:
`ContractResult.consensus_timestamp = EthereumTransaction.consensus_timestamp`
and select the `authorization_list` json field as well.

- `GET /api/v1/contracts/{contractIdOrAddress}/results/logs`
- `GET /api/v1/contracts/{contractIdOrAddress}/state`
- `GET /api/v1/contracts/results/{transactionIdOrHash}/actions`
- `GET /api/v1/contracts/results/{transactionIdOrHash}/opcodes`
- `GET /api/v1/contracts/results/logs`

No changes in the response format of these endpoints. The results will be fetched directly by contract id with the existing
DB queries as during import there will be no difference between the contract result of a regular contract call and a contract
result from an EOA code delegation call.

## Web3 Module Changes

### 1. Enhance TransactionExecutionService

The existing `TransactionExecutionService` currently supports only contract create and contract call transactions, which was
sufficient to cover all scenarios up until now. The specifics of the code delegation creation require support of sending
ethereum transactions to the `TransactionExecutor` from hedera-app.

```
private TransactionBody buildEthereumTransactionBody(final CallServiceParameters params) {
        return defaultTransactionBodyBuilder(params)
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .ethereumData(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(params.getEthereumData().toArray()))
                        .build())
                .transactionFee(<fixed_transaction_fee>)
                .build();
}
```

### 2. Enhance CallServiceParameters

The interface needs to have a new method:

```
public interface CallServiceParameters {

    Bytes getEthereumData();
}

```

### 3. Adapt ContractExecutionParameters

`ContractExecutionParameters` is one of the classes that implement `CallServiceParameters`. The `ethereumData` is not
relevant for it, so return `null` by default.

```
@Value
@Builder
public class ContractExecutionParameters implements CallServiceParameters {

    @Override
    public Bytes getEthereumData() {
        return null;
    }
}
```

### 4. Adapt ContractDebugParameters

The `ethereumData` needs to be added in this model.

```
@Value
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class ContractDebugParameters implements CallServiceParameters {

    Bytes ethereumData;
}
```

If the parameters have this `ethereumData` set, then the new method in `TransactionExecutionService` to build an Ethereum
transaction will be used with priority. In all other cases - fallback to the current implementation.

### 5. Enhance OpcodeServiceImpl

When `ContractDebugParameters` are built, we need to pass the new `ethereumData` field so that it can be used in the
`TransactionExecutionService` to create the Ethereum transaction body.

### 6. Enhance Account

The `Account` model needs to have a new field `delegation_address` (or however it is named in hedera-app) that will
keep the code delegation in the state as part of the account model.

### 7. Update ContractBytecodeReadableKVState

`protected Bytecode readFromDataSource(@NonNull ContractID contractID);`
This method needs to be updated to try to find a contract with the `contractRepository`. If it is not found,
search by contract id in the `entityRepository` and if an account is found return the `delegation_address`.

### 8. Enhance AbstractAliasedAccountReadableKVState

The `AbstractAliasedAccountReadableKVState` needs to be changed to set the new field `delegation_address` (or however
it is named in hedera-app) that will set the code delegation from the entity to the built account model.

## Testing Strategy

### 1. Unit Tests

- Transaction handler tests for code delegation persisting
- `Eip7702EthereumTransactionParser` tests for transaction parsing
- `ContractBytecodeReadableKVState` tests to verify that the code delegations are returned as expected
- `AccountReadableKVState` tests to verify that the code delegations are returned as expected when an account with delegation is fetched

### 2. Integration Tests

- End-to-end API tests
  - Web3 integration tests for executing a contract call with code delegation:
    - with a delegation to an existing contract
    - with a delegation to another EOA
    - with a delegation to a contract that needs funds to execute the transaction but the EOA does not have enough funds - to verify that the EOA's context is used as expected
    - with a delegation to a non-existing address - should result in a hollow account creation. The result is a no-op
    - with a delegation to a system contract - should result in a no-op
    - with `debug_traceTransaction` call to verify that the Ethereum calls are made successfully and that we can provide historical support
  - REST spec tests that the `/accounts` endpoint returns the new `delegation_address` field as expected
  - REST spec tests that the contract results endpoints return the new `authorization_list` field as expected
- Database migration tests

### 3. Acceptance Tests

- Executing a contract call to EOA with code delegation to another contract
- Call the REST `/accounts/{accountId}` endpoint to verify the delegation identifier is returned as expected
- Call the REST contract results endpoint to verify the authorization list is returned as expected

### 4. k6 Tests

- Performance testing of code delegation execution
