# HIP-1340 Mirror Node EOA Code Delegation - Design Document

## Overview

This design document outlines the implementation plan for supporting HIP-1340 EOA code delegation in the Hiero mirror node.
The implementation will enable extraction, storage, and querying of code delegation data from consensus node transactions,
performing `eth_call`, `eth_estimateGas` transactions with code delegations in the mirror node and querying the persisted
code delegations from the existing REST APIs.

## Goals

- Enhance the database schema to store code delegations
- Ingest code delegation creation, update, and deletion transactions from the record stream
- Ingest code delegation creations via `CryptoCreateTransactionBody` transactions
- Ingest code delegation updates and deletions via `CryptoUpdateTransactionBody` transactions
- Expose code delegation information via the existing account REST APIs with the new code field
- Support efficient querying and pagination of code delegations by account id and timestamp
- Track code delegation usage in transactions for audit and analytics purposes

## Non-Goals

- Validating code delegation logic (code delegations are executed by consensus nodes)
- Simulating code delegation creation, deletion or update in web3 (only execution will be supported)

## Architecture

The HIP-1340 implementation follows the established mirror node architecture pattern:

1. **Transaction Processing**: Code delegation-related transactions are processed by dedicated transaction handlers
2. **Database Storage**: Code delegation data is stored in normalized tables with proper indexing for efficient queries
3. **REST APIs**: The existing accounts endpoints expose the new code delegation field. The existing contract endpoints will
   be enhanced to work for EOAs with code delegations as if they are contracts.
4. **WEB3 API**: Code delegation executions can be simulated with `eth_call`, `eth_estimateGas`.

## Background

[HIP-1340](https://hips.hedera.com/hip/hip-1340) introduces one of the key features of the Ethereum's Pectra upgrade,
the facility of EOA (Externally Owner Account) Code Delegation in the Hiero network. Code delegations can be created and
updated in:

- `CryptoCreateTransactionBody`
- `CryptoUpdateTransactionBody`
- `EthereumTransactionBody` - with the new type 4 Ethereum transactions

Code delegation execution will be identified through `ContractCallTransactionBody` transactions to the address of the EOA
and the transaction will be executed in the context of the EOA. For more detailed explanation, please check HIP-1340.

## API Changes

```
GET /api/v1/accounts
```

```
GET /api/v1/accounts/{idOrAliasOrEvmAddress}
```

In both API endpoints the returned account model will contain an additional parameter, named `code`. Its value will be
the persisted code delegation, if any. The format is: `0xef0100 || 20-byte address`

```
GET /api/v1/contracts/results
```

```
GET /api/v1/contracts/results/{transactionIdOrHash}
```

In both API endpoints the returned response needs to contain the new `authorization_list` bytes field.

In all `GET /api/v1/contracts/*` endpoints the DB queries need to be modified so that they work with an EOA account the
same way as the existing queries with contract id:

1. Create a subquery that gets the contract id from the `contract` table. If it is found then return it. Otherwise, try to
   find the id in the `code_delegation` table. We don't have an effective way to determine in which table to search directly,
   so we keep the old implementation with priority.
2. The rest of the join statements in the existing queries will remain the same.

## Database Schema Design

### Query Requirements

The code delegations will be fetched by contract ID for latest blocks and by (contract ID + timestamp) for historical blocks.
The database indexes must support these query patterns efficiently for optimal performance.

### 1. Code Delegation Table

Create new tables to store code delegation information with history support:

```sql
-- add_code_delegations_support.sql

-- Main code delegation table (current state)
create table if not exists code_delegation
(
    entity_id              bigint,       not null,
    created_timestamp      bigint,       not null,
    delegation_identifier  bytea,        not null,
    timestamp_range        int8range,

    primary key (entity_id)
);

-- Code delegation history table
create table if not exists code_delegation_history
(
    like code_delegation including defaults
);

select create_distributed_table('code_delegation', 'entity_id', colocate_with => 'entity');
select create_distributed_table('code_delegation_history', 'entity_id', colocate_with => 'entity');
```

Each EOA can have only one `delegation_identifier` set. To delete it, the address part of the identifier is set to the empty
address - 0x0000000000000000000000000000000000000000.

### 2. Ethereum Table

The existing `ethereum_transaction` table needs to be altered and a new column needs to be added - `authorization_list`
of type bytea.

```sql
alter table if exists ethereum_transaction add column if not exists authorization_list bytea;
```

## Importer Module Changes

### 1. Create Domain Models

Create new domain classes following the EntityHistory pattern:

#### AbstractCodeDelegation.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/transaction/AbstractCodeDelegation.java
public abstract class AbstractCodeDelegation implements History {

    private EntityId entityId;
    private Long createdTimestamp;
    private byte[] codeDelegation;
    private Range<Long> timestampRange;

}
```

#### CodeDelegation.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/transaction/CodeDelegation.java
@Upsertable
public class CodeDelegation extends AbstractCodeDelegation {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

#### CodeDelegationHistory.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/hook/CodeDelegationHistory.java
public class CodeDelegationHistory extends AbstractHook {
    // Only the parent class should contain fields so that they're shared with both the history and non-history tables.
}
```

### 2. Transaction Handler Enhancements

Existing transaction handlers will be enhanced to extract code delegation information from transaction bodies:

The following transaction handlers will be modified to process `CodeDelegation`:

- `EthereumTransactionHandler.java` - the `authorization_list` field from the `EthereumTransactionBody` needs to be saved
  similarly to the other byte array fields.

A child frame of the parent Ethereum transaction will be a `CryptoCreateTransaction` or a `CryptoUpdateTransaction`
(depending on whether the affected EOA already exists or not). Their protobufs will be changed to have an additional
`code` field that will keep the code delegation.

- `CryptoCreateTransactionHandler.java` - read the `code` field and save the code delegation via the `EntityListener`
- `CryptoUpdateTransactionHandler.java` - read the `code` field and save the code delegation via the `EntityListener`

```
create CodeDelegation with:
    - entity ID from transaction
    - delegation_identifier from code field from the transaction body
save CodeDelegation via entityListener
```

### 3. Entity Listener Enhancement

Extend `EntityListener` interface to handle code delegations:

```java
// importer/src/main/java/org/hiero/mirror/importer/parser/record/entity/EntityListener.java
public interface EntityListener {
    // ... existing methods ...

    void onCodeDelegation(CodeDelegation codeDelegation);
}
```

### 4. Create Repositories

#### CodeDelegationRepository.java

```java

@Repository
public interface CodeDelegationRepository extends JpaRepository<CodeDelegation, Long> {
    // Methods for querying code delegations by entity id and timestamp
}
```

#### CodeDelegationHistoryRepository.java

```java

public interface CodeDelegationHistoryRepository extends CrudRepository<CodeDelegation, Long>, RetentionRepository {

    @Modifying
    @Query(nativeQuery = true, value = "delete from code_delegation_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
```

### 5. Parser

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

It will parse the rlp encoded transaction similarly to the existing `Eip2930EthereumTransactionParser`. The only difference
will be that the new transaction type will need to decode the authorizationList field as well.

## Web3 Module Changes

### 1. Create Repository

```java
//org.hiero.mirror.web3.repository.CodeDelegationRepository
@Repository
public interface CodeDelegationRepository extends CrudRepository<CodeDelegation, Long> {

    Optional<CodeDelegation> findByEntityId(final long entityId);

    /**
     *  Finds the historical code delegation for a specific block timestamp. The query needs to reflect what was the
     *  latest state of the code delegation up until the given block timestamp. The code_delegation and code_delegation_history
     *  tables to be queried with a union.
     */
    @Query(
            value =
                    """
                    (
                        select *
                        from code_delegation
                        where entity_id = ?1 and lower(timestamp_range) <= ?2
                    )
                    union all
                    (
                        select *
                        from code_delegation_history
                        where entity_id = ?1 and lower(timestamp_range) <= ?2
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<CodeDelegation> findByEntityIdAndTimestamp(final long entityId, final long blockTimestamp);
}
```

### 2. Update ContractBytecodeReadableKVState

`protected Bytecode readFromDataSource(@NonNull ContractID contractID);`
This method needs to be updated to try to find a contract with the `contractRepository`. If it is not found,
search by contract id in the `codeDelegationRepository`.

## Testing Strategy

### 1. Unit Tests

- Transaction handler tests for code delegation persisting
- Repository tests for data access

### 2. Integration Tests

- End-to-end API tests
  - Web3 integration tests for executing a contract call with code delegation:
    - with a delegation to an existing contract
    - with a delegation to another EOA
    - with a delegation to a contract that needs funds to execute the transaction but the EOA does not have enough funds - to verify that the EOA's context is used as expected
    - with a delegation to a non-existing address - should result in a hollow account creation. The result is a no-op
    - with a delegation to a system contract - should result in an error
  - REST spec tests that the `/accounts` endpoint returns the new `code` field as expected
- Database migration tests

### 3. Acceptance Tests

- Executing a contract call to EOA with code delegation to another contract
- Call the REST `/accounts/{accountId}` endpoint to verify the code delegation is returned as expected

### 4. k6 Tests

- Performance testing of code delegation execution
