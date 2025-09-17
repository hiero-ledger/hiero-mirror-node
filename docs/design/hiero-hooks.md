# HIP-1195 Mirror Node Hooks Support - Design Document

## Overview

This design document outlines the implementation plan for supporting HIP-1195 hooks in the Hiero mirror node. The
implementation will enable extraction, storage, and querying of hook data from consensus node transactions, supporting
the
new REST APIs for hooks and hook storage.

## Goals

- Enhance the database schema to store hook information and hook storage data
- Ingest hook creation, update, and deletion transactions from the record stream
- Ingest hook storage updates via `LambdaSStoreTransactionBody` transactions and sidecars
- Expose hook information via new REST APIs for account hooks and hook storage
- Support efficient querying and pagination of hooks by owner, contract, and timestamp
- Track hook usage in transactions for audit and analytics purposes

## Non-Goals

- Execute or validate hook logic (hooks are executed by consensus nodes)
- Support real-time hook execution or simulation

## Architecture

The HIP-1195 implementation follows the established mirror node architecture pattern:

1. **Transaction Processing**: Hook-related transactions are processed by dedicated transaction handlers
2. **Database Storage**: Hook data is stored in normalized tables with proper indexing for efficient queries
3. **REST APIs**: New endpoints expose hook information following existing API patterns

The implementation supports dual ingestion paths for hook storage updates:

- **Primary**: Direct `LambdaSStoreTransactionBody` transactions
- **Secondary**: Hook storage updates via transaction record sidecars

## Background

[HIP-1195](https://hips.hedera.com/hip/hip-1195) introduces programmable hooks to the Hiero network, allowing users to
customize behavior at specific
extension points. Hooks can be attached to accounts and contracts via:

- `CryptoCreateTransactionBody`
- `CryptoUpdateTransactionBody`
- `ContractCreateTransactionBody`
- `ContractUpdateTransactionBody`

Hook execution will be identified through `ContractCallTransactionBody` transactions to system address `0x16d` that
reference a parent transaction via `parent_consensus_timestamp`. The parent transaction will contain the hook context
and hook_id information.

## API Requirements

The implementation must support these new REST endpoints:

### 1. Account Hooks API

```
GET /api/v1/accounts/{idOrAliasOrEvmAddress}/hooks
```

Response format:

```json
{
  "hooks": [
    {
      "admin_key": {
        "_type": "ED25519",
        "key": "0x302a300506032b6570032100e5b2…"
      },
      "contract_id": "0.0.456",
      "created_timestamp": "1726874345.123456789",
      "deleted": false,
      "extension_point": "ACCOUNT_ALLOWANCE_HOOK",
      "hook_id": 5,
      "owner_id": "0.0.123",
      "type": "LAMBDA"
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.123/hooks?hook.id=lt:5&limit=1"
  }
}
```

**Note**: Hooks in the response are always sorted based on their `hook_id` in descending order. The
`hook.id`
parameter in the `links.next` URL uses the format `hook.id=lt:5` for pagination.

#### Query Parameters

| Parameter | Type    | Description                                                                                 | Default | Validation                                                       |
| --------- | ------- | ------------------------------------------------------------------------------------------- | ------- | ---------------------------------------------------------------- |
| `hook.id` | string  | Filter hooks by hook ID. Supports comparison operators: `eq:`, `gt:`, `gte:`, `lt:`, `lte:` | none    | Must be a valid positive integer when using comparison operators |
| `limit`   | integer | Maximum number of hooks to return                                                           | `25`    | Must be between 1 and 100                                        |
| `order`   | string  | Sort order for results                                                                      | `desc`  | Must be either `asc` or `desc`                                   |

**Examples:**

- `/api/v1/accounts/0.0.123/hooks` - Get all hooks for account
- `/api/v1/accounts/0.0.123/hooks?limit=10` - Get first 10 hooks
- `/api/v1/accounts/0.0.123/hooks?hook.id=eq:5` - Get hook with ID 5
- `/api/v1/accounts/0.0.123/hooks?hook.id=lt:10&limit=5` - Get 5 hooks with ID less than 10

### 2. Hook Storage API

```
GET /api/v1/accounts/{idOrAliasOrEvmAddress}/hooks/{hook_id}/storage
```

Returns the storage state for a hook (similar to `/api/v1/contracts/{contractIdOrAddress}/state`):

- **Without timestamp parameter**: Returns current state from `hook_storage` table
- **With timestamp parameter**: Returns historical state from `hook_storage_change` table

Response format:

```json
{
  "hook_id": 1,
  "owner_id": "0.0.123",
  "storage": [
    {
      "key": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "value": "0x00000000000000000000000000000000000000000000000000000000000003e8",
      "timestamp": "1726874345.123456789"
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.123/hooks/1/storage?key=gt:0x0000000000000000000000000000000000000000000000000000000000000001&limit=1"
  }
}
```

#### Query Parameters

| Parameter   | Type    | Description                                                                                                                                                                                                                                                                                                                             | Default | Validation                                                                             |
| ----------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- | -------------------------------------------------------------------------------------- |
| `key`       | string  | Filter storage entries by storage key. Supports comparison operators: `eq:`, `gt:`, `gte:`, `lt:`, `lte:`                                                                                                                                                                                                                               | none    | Must be a valid 32-byte hex string (64 hex characters) when using comparison operators |
| `timestamp` | string  | Query historical state at specific timestamp. If not provided, returns current state. Supports comparison operators: `eq:`, `gt:`, `gte:`, `lt:`, `lte:`. Examples: `1234567890`, `1234567890.000000100`, `eq:1234567890`, `gt:1234567890.000000400`, `gte:1234567890.000000500`, `lt:1234567890.000000600`, `lte:1234567890.000000700` | none    | Must be a valid timestamp in seconds.nanoseconds format                                |
| `limit`     | integer | Maximum number of storage entries to return                                                                                                                                                                                                                                                                                             | `25`    | Must be between 1 and 100                                                              |
| `order`     | string  | Sort order for results                                                                                                                                                                                                                                                                                                                  | `asc`   | Must be either `asc` or `desc`                                                         |

**Examples:**

- `/api/v1/accounts/0.0.123/hooks/1/storage` - Get current storage state for hook
- `/api/v1/accounts/0.0.123/hooks/1/storage?timestamp=eq:1726874345.123456789` - Get historical storage state at
  specific
  timestamp
- `/api/v1/accounts/0.0.123/hooks/1/storage?limit=10` - Get first 10 current storage entries
- `/api/v1/accounts/0.0.123/hooks/1/storage?key=eq:0x0000000000000000000000000000000000000000000000000000000000000001` -
  Get specific storage entry
-

`/api/v1/accounts/0.0.123/hooks/1/storage?key=gt:0x0000000000000000000000000000000000000000000000000000000000000001&limit=5` -
Get 5 storage entries with key greater than specified value

## Database Schema Design

### Query Requirements

Hooks can be fetched based on the following criteria:

- **By owner_id**: Get all hooks owned by a specific account
- **By owner_id + hook_id**: Get specific hooks by their composite primary key

The database indexes must support these query patterns efficiently for optimal performance.

### 1. Hooks Table

Create a new table to store hook information:

```sql
-- add_hooks_support.sql
create type hook_type as enum ('LAMBDA', 'PURE');
create type hook_extension_point as enum ('ACCOUNT_ALLOWANCE_HOOK');

create table if not exists hook
(
    owner_id          bigint                not null,
    hook_id           bigint                not null,
    admin_key         bytea,
    contract_id       bigint,
    created_timestamp bigint                not null,
    deleted           boolean               not null default false,
    extension_point   hook_extension_point  not null,
    type              hook_type             not null,

    primary key (owner_id, hook_id)
);

-- Distribute table for Citus using owner_id as distribution column
select create_distributed_table('hook', 'owner_id');
```

### 2. Hook Storage Tables

For hook EVM storage data, we use a two-table approach similar to contract storage:

#### 2.1. Hook Storage Change Table (Historical)

```sql
-- add_hook_storage_change_table.sql
create table hook_storage_change
(
    hook_id             bigint not null,
    owner_id            bigint not null,
    key                 bytea  not null,
    value               bytea  not null,
    consensus_timestamp bigint not null,

    primary key (hook_id, owner_id, key, consensus_timestamp)
);

create index hook_storage_change__hook_timestamp on hook_storage_change (hook_id, owner_id, consensus_timestamp);

-- Distribute table for Citus using owner_id as distribution column (co-locate with hook table)
select create_distributed_table('hook_storage_change', 'owner_id');
```

#### 2.2. Hook Storage Table (Current State)

```sql
-- add_hook_storage_table.sql
create table hook_storage
(
    hook_id            bigint not null,
    owner_id           bigint not null,
    key                bytea  not null,
    value              bytea  not null,
    created_timestamp  bigint not null,
    modified_timestamp bigint not null,

    primary key (hook_id, owner_id, key)
);

-- Distribute table for Citus using owner_id as distribution column (co-locate with hook table)
select create_distributed_table('hook_storage', 'owner_id');
```

## Importer Module Changes

### 1. Domain Models

Create new domain classes:

#### Hook.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/Hook.java
public class Hook {
    private HookId id;

    private byte[] adminKey;
    private Long contractId;
    private Long createdTimestamp;
    private Boolean deleted;
    private ExtensionPoint extensionPoint;
    private HookType type;

    public static class HookId implements Serializable {
        private Long ownerId;
        private Long hookId;
    }

    // Getters, setters, and utility methods
}
```

#### HookStorage.java (Current State)

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/HookStorage.java
public class HookStorage {
    private HookStorageId id;
    private byte[] value;
    private Long createdTimestamp;
    private Long modifiedTimestamp;

    public static class HookStorageId implements Serializable {
        private Long hookId;
        private Long ownerId;
        private byte[] key;
    }
}
```

#### HookStorageChange.java (Historical Changes)

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/HookStorageChange.java
public class HookStorageChange {
    private HookStorageChangeId id;
    private byte[] value;

    public static class HookStorageChangeId implements Serializable {
        private Long hookId;
        private Long ownerId;
        private byte[] key;
        private Long consensusTimestamp;
    }
}
```

### 2. Transaction Handler Enhancements

Existing transaction handlers will be enhanced to extract hook information from transaction bodies:

#### Hook Creation Processing

The following transaction handlers will be modified to process `HookCreationDetails`:

- `CryptoCreateTransactionHandler.java`
- `CryptoUpdateTransactionHandler.java`
- `ContractCreateTransactionHandler.java`
- `ContractUpdateTransactionHandler.java`

**Processing Logic:**

```
for each hookDetails in transactionBody.hookCreationDetailsList:
    create Hook entity with:
        - composite ID (owner_id, hook_id) from transaction
        - extension point from protobuf enum
        - hook type (PURE or LAMBDA)
        - admin key if present
        - contract_id
        - deleted = false
    save Hook via entityListener
```

### 3. Entity Listener Enhancement

Extend `EntityListener` interface to handle hooks:

```java
// importer/src/main/java/org/hiero/mirror/importer/parser/record/entity/EntityListener.java
public interface EntityListener {
    // ... existing methods ...

    void onHook(Hook hook);

    void onHookStorage(HookStorage hookStorage);

    void onHookStorageChange(HookStorageChange hookStorageChange);
}
```

### 3.1. Hook Execution Processing

Hook execution appears in the record stream as `ContractCall` transactions to system contract `0.0.365` (EVM address `0x16d`) as children of the triggering transaction. These can be identified by checking `ContractCallTransactionBody.contractID == 0.0.365` and the presence of `parentConsensusTimestamp`. The execution order follows a specific sequence based on the `CryptoTransferTransactionBody` structure:

**Hook Execution Order:**

1. **HBAR Transfers**: All hooks (pre/post) for accounts in `TransferList.accountAmounts`, in order
2. **Fungible Token Transfers**: All hooks (pre/post) for `TokenTransferList` fungible transfers, in order
3. **NFT Transfers**: All hooks (pre/post) for `TokenTransferList` NFT transfers, in order
   - For NFT transfers with both sender and receiver hooks: sender hook executes first
4. **Post Hook Execution**: Repeat steps 1-3 in the same order for any post-hook calls

**Record Stream Structure:**

- **Parent Transaction**: The original `CryptoTransfer` transaction
- **Child Transactions**: Sequential `ContractCall` transactions to `0x16d`, one for each hook execution following the
  above order
- **Hook Storage Updates**: `ContractStateChanges` in sidecars targeting contract `0.0.365` represent hook storage
  updates

**Processing Logic:**

```
if (transactionBody is ContractCallTransactionBody && recipient == 0x16d):
    if (transactionRecord.hasParentConsensusTimestamp()):
        parentTransaction = context.get(Transaction.class, parentConsensusTimestamp)
        if (parentTransaction != null):
            // Determine hook execution context using parent transaction body and child index
            hookExecutionIndex = getChildTransactionIndex(transaction, parentTransaction)
            hookContext = deriveHookFromCryptoTransferOrder(parentTransaction.body, hookExecutionIndex)

            // Process sidecar ContractStateChanges for contract 0.0.365
            for each stateChange in transaction.sidecars where contractId == 365:
                create HookStorageChange entity with:
                    - hook_id from hookContext
                    - owner_id from hookContext
                    - storage key and value from state change
                    - consensus_timestamp from current transaction

                // Also update current state table
                create/update HookStorage entity for current state
```

**Hook Context Derivation Logic:**

The `deriveHookFromCryptoTransferOrder` function maps child transaction index to hook context based on the execution
order:

```
deriveHookFromCryptoTransferOrder(cryptoTransferBody, hookExecutionIndex):
    executionOrder = []

    // 1. Add HBAR transfer hooks (pre/post)
    for each accountAmount in cryptoTransferBody.transfers.accountAmounts:
        if (accountAmount.accountId has hooks):
            executionOrder.add(HookContext(accountAmount.accountId, PRE_HOOK))
            executionOrder.add(HookContext(accountAmount.accountId, POST_HOOK))

    // 2. Add fungible token transfer hooks (pre/post)
    for each tokenTransferList in cryptoTransferBody.tokenTransfers:
        if (tokenTransferList is fungible):
            for each transfer in tokenTransferList.transfers:
                if (transfer.accountId has hooks):
                    executionOrder.add(HookContext(transfer.accountId, PRE_HOOK))
                    executionOrder.add(HookContext(transfer.accountId, POST_HOOK))

    // 3. Add NFT transfer hooks (pre/post) - sender first for each NFT
    for each tokenTransferList in cryptoTransferBody.tokenTransfers:
        if (tokenTransferList is NFT):
            for each nftTransfer in tokenTransferList.nftTransfers:
                if (nftTransfer.senderAccountId has hooks):
                    executionOrder.add(HookContext(nftTransfer.senderAccountId, PRE_HOOK))
                    executionOrder.add(HookContext(nftTransfer.senderAccountId, POST_HOOK))
                if (nftTransfer.receiverAccountId has hooks):
                    executionOrder.add(HookContext(nftTransfer.receiverAccountId, PRE_HOOK))
                    executionOrder.add(HookContext(nftTransfer.receiverAccountId, POST_HOOK))

    return executionOrder[hookExecutionIndex]
```

### 3.2. Hook Storage Updates Processing

Hook storage updates are delivered through `ContractStateChanges` in the sidecars of `ContractCall` transactions to
system contract `0x16d`. Each hook execution creates storage changes targeting contract `0.0.365` that represent updates
to that specific hook's storage.

**Key Implementation Details:**

1. **Hook Identification**: The specific hook being executed is determined by the order of child transactions and the
   parent transaction context
2. **Storage Contract**: All hook storage changes target contract ID `365` (`0x16d`)
3. **Processing Order**: Child transactions are processed sequentially, maintaining hook execution order

**Processing Logic:**

```
// In ContractCallTransactionHandler when recipient == 0x16d
if (transactionRecord.hasParentConsensusTimestamp()):
    parentTransaction = context.get(Transaction.class, parentConsensusTimestamp)
    if (parentTransaction != null):
        // Determine which hook this execution represents
        hookExecutionIndex = getChildTransactionIndex(transaction, parentTransaction)
        hookId = deriveHookIdFromContext(parentTransaction, hookExecutionIndex)
        ownerId = extractOwnerIdFromParentContext(parentTransaction)

        // Process contract state changes for system contract 0x16d
        for each sidecar in transaction.sidecars:
            for each stateChange in sidecar.contractStateChanges where contractId == 365:
                // Create historical change record
                create HookStorageChange entity with:
                    - composite ID (hookId, ownerId, stateChange.slot, consensus_timestamp)
                    - value from stateChange.valueWritten
                save HookStorageChange via entityListener

                // Update current state table
                create/update HookStorage entity with:
                    - composite ID (hookId, ownerId, stateChange.slot)
                    - value from stateChange.valueWritten (latest)
                save HookStorage via entityListener
```

**Implementation Strategy:**

The mirror node will primarily rely on processing `ContractStateChanges` from hook execution sidecars, as this is the
primary mechanism for hook storage updates. The existing contract state change processing infrastructure can be
leveraged and extended to handle hook storage updates by:

1. **Detecting Hook Context**: Identifying when `ContractStateChanges` target the hook system contract (`0.0.365`)
2. **Hook Resolution**: Mapping the child transaction sequence to specific hook IDs using parent transaction context
3. **Dual Storage**: Writing to both historical (`hook_storage_change`) and current state (`hook_storage`) tables

### 4. BlockItemTransformer Enhancement

**Note**: Block Stream–specific changes are currently unavailable in HIP. The following represents an initial draft of
potential changes, which will be revisited once further details are confirmed.

The `BlockItemTransformer` will be enhanced to handle hook-related block items during processing:

**Hook Creation Processing:**

- Process `HookCreationDetails` from account/contract creation and update transactions
- Transform protobuf hook data into domain entities
- Validate hook configuration and constraints

**Hook Storage Processing:**

- Extract hook storage updates from `LambdaSStoreTransactionBody` transactions
- Process hook storage sidecars from various transaction types
- Transform storage key-value pairs into `HookStorage` entities

**Implementation Approach:**

```
if (transactionBody has HookCreationDetails):
    for each hookDetails in hookCreationDetailsList:
        transform hookDetails to Hook entity
        set composite ID (owner_id, hook_id)
        map extension point enum
        extract admin key if present
        pass to EntityListener

if (transactionBody is LambdaSStoreTransactionBody):
    for each storageEntry in storageUpdatesList:
        transform to HookStorage entity
        set composite ID (hook_id, owner_id, key)
        pass to EntityListener
```

### 5. Repository Classes

#### HookRepository.java

```java

@Repository
public interface HookRepository extends JpaRepository<Hook, Hook.HookId> {
    // Methods for querying hooks by owner and supporting pagination
}
```

#### HookStorageRepository.java

```java

@Repository
public interface HookStorageRepository extends JpaRepository<HookStorage, HookStorage.HookStorageId> {
    // Methods for querying hook storage by hook and owner
}
```

## REST API Implementation

### 1. Controller Layer

A new `HookController` will be created to handle HTTP requests for both hooks and hook storage endpoints. The controller
will:

- Parse and validate account identifiers (ID, alias, or EVM address)
- Extract query parameters for filtering and pagination
- Delegate business logic to the service layer
- Return properly formatted response objects

### 2. Service Layer

A `HookService` will implement the business logic for hook queries:

**Account Hooks Service Logic:**

- Validate that the account exists
- Query hooks by owner_id with deletion filtering
- Apply hook.id comparison filters (eq, gt, lt, etc.)
- Implement pagination using hook_id-based cursors
- Build response with proper next page links

**Hook Storage Service Logic:**

- Validate that the hook exists for the given account
- Query storage entries by composite key (hook_id, owner_id)
- Apply storage key comparison filters
- Implement pagination using storage key cursors
- Build response with historical timestamps

### 3. Response Models

Response models will be generated from OpenAPI specifications and mapped using MapStruct mappers to convert between
domain entities and API responses.

## Testing Strategy

### 1. Unit Tests

- Transaction handler tests for hook extraction
- Service layer tests for business logic
- Repository tests for data access

### 2. Integration Tests

- End-to-end API tests
- Database migration tests
- Transaction processing tests

### 3. Acceptance Tests

#### Hook Creation and Retrieval

```gherkin
Feature: Hook Management

  Scenario: Create account with hooks and retrieve via API
    Given a transaction creates an account with hooks:
      | type   | extension_point        | admin_key |
      | LAMBDA | ACCOUNT_ALLOWANCE_HOOK | key123    |
      | PURE   | ACCOUNT_ALLOWANCE_HOOK | key456    |
    When the mirror node processes the transactions
    And I query "/api/v1/accounts/{account_id}/hooks"
    Then the response contains 2 hooks
    And both hooks have null contract_id since they belong to an account
    And one hook has type "LAMBDA"
    And one hook has type "PURE"


  Scenario: Hook pagination works correctly
    Given consensus nodes create an account with 50 hooks
    When the mirror node processes the transactions
    And I query "/api/v1/accounts/{account_id}/hooks?limit=10"
    Then I receive 10 hooks
    And the response includes a next page link
    When I follow the next page link
    Then I receive the next 10 hooks

  Scenario: Hook filtering by hook_id
    Given consensus nodes have assigned hook IDs to an account
    When the mirror node processes the transactions
    And I query "/api/v1/accounts/{account_id}/hooks?hook.id=gte:{some_hook_id}"
    Then I receive only hooks with IDs greater than or equal to the specified value
```

#### Hook Storage Management

**Note**: Will not be part of first phase

```gherkin
Feature: Hook Storage

  Scenario: Hook storage updates via LambdaSStoreTransaction
    Given a hook exists with owner_id 123 and hook_id 1
    When a LambdaSStoreTransaction updates storage:
      | key   | value |
      | 0x001 | 0xabc |
      | 0x002 | 0xdef |
    Then the hook storage is persisted with correct timestamps
    When I query "/api/v1/accounts/0.0.123/hooks/1/storage"
    Then I receive 2 storage entries

  Scenario: Hook storage updates via transaction sidecars
    Given a hook exists with owner_id 123 and hook_id 1
    When a ContractCall transaction includes hook storage sidecars:
      | key   | value |
      | 0x003 | 0x123 |
    Then the storage is persisted from the sidecar
    And it appears in the storage API response

  Scenario: Hook storage pagination by key
    Given a hook has 100 storage entries
    When I query "/api/v1/accounts/{account_id}/hooks/{hook_id}/storage?limit=25"
    Then I receive 25 storage entries ordered by key
    And pagination links work correctly
```

### 4. Test Data

- Sample transactions with `HookCreationDetails`
- Hook storage update scenarios
- Edge cases and error conditions

## Migration Strategy

### 1. Database Migrations

- Create new tables in proper sequence
- Add indexes for performance
- Consider partitioning for large datasets

### 2. Backward Compatibility

- Ensure existing APIs remain functional
- Graceful handling of missing hook data
- Version-aware processing

### 3. Performance Considerations

- Efficient querying with proper indexes
- Pagination for large result sets
- Caching strategies where appropriate

## Monitoring

### Logging

- Hook processing events
- API request/response logging
- Error conditions and recovery

## Non-Functional Requirements

### 1. Performance Requirements

- Support processing of hook transactions at consensus node peak TPS (10,000+ TPS)
- Hook API response times must remain under 500ms for 95th percentile at normal load
- Hook storage API must support efficient querying of large storage datasets (>1M entries per hook)
- Database queries must complete within 100ms for properly indexed operations
- Support concurrent hook processing without blocking other transaction types

### 2. Scalability Requirements

- Hook tables must support horizontal partitioning for large-scale deployments
- API endpoints must support standard pagination patterns with configurable limits
- Storage requirements must be manageable with automated cleanup policies

### 3. Reliability Requirements

- Hook data ingestion must maintain the same reliability guarantees as existing transaction types
- API availability must match existing REST API SLAs
- Data consistency must be maintained across hook, hook_storage, and transaction tables

## Open Questions

1. **Indexing Strategy**: Which additional indexes might be needed for complex hook queries that we haven't identified
   yet?

2. **Protobuf Changes and Record Stream Timeline**: Check with Michael Tinker on the availability timeline for protobuf
   changes and record stream implementation for hook support.
