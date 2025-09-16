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
- Provide REST APIs for hook data
- Store hook storage state
- Support historical replay of hook executions

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

The `LambdaSStoreTransactionBody` and `ContractCallTransactionBody` will include a `hook_id` field to indicate which
hook is performing the operation.

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

Response format:

```json
{
  "hook_id": 1,
  "owner_id": "0.0.123",
  "storage": [
    {
      "key": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "timestamp": "1586567700.453054000",
      "value": "0x00000000000000000000000000000000000000000000000000000000000003e8"
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.123/hooks/1/storage?key=gt:0x0000000000000000000000000000000000000000000000000000000000000001&limit=1"
  }
}
```

#### Query Parameters

| Parameter | Type    | Description                                                                                               | Default | Validation                                                                             |
| --------- | ------- | --------------------------------------------------------------------------------------------------------- | ------- | -------------------------------------------------------------------------------------- |
| `key`     | string  | Filter storage entries by storage key. Supports comparison operators: `eq:`, `gt:`, `gte:`, `lt:`, `lte:` | none    | Must be a valid 32-byte hex string (64 hex characters) when using comparison operators |
| `limit`   | integer | Maximum number of storage entries to return                                                               | `25`    | Must be between 1 and 100                                                              |
| `order`   | string  | Sort order for results                                                                                    | `asc`   | Must be either `asc` or `desc`                                                         |

**Examples:**

- `/api/v1/accounts/0.0.123/hooks/1/storage` - Get all storage entries for hook
- `/api/v1/accounts/0.0.123/hooks/1/storage?limit=10` - Get first 10 storage entries
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
    owner_id
    bigint
    not
    null,
    hook_id
    bigint
    not
    null,
    admin_key
    bytea,
    contract_id
    bigint,
    created_timestamp
    bigint
    not
    null,
    deleted
    boolean
    not
    null
    default
    false,
    extension_point
    hook_extension_point
    not
    null,
    type
    hook_type
    not
    null,

    primary
    key
(
    owner_id,
    hook_id
)
    );

-- Index to support owner_id lookups (primary key already provides owner_id + hook_id lookups)
create index if not exists hook__owner_id on hook (owner_id) where deleted = false;

-- Distribute table for Citus using owner_id as distribution column
select create_distributed_table('hook', 'owner_id');
```

### 2. Hook Storage Table

For hook EVM storage data:

```sql
-- add_hook_storage_table.sql
create table hook_storage
(
    hook_id   bigint not null,
    owner_id  bigint not null,
    key       bytea  not null,
    value     bytea  not null,
    timestamp bigint not null,

    primary key (hook_id, owner_id, key)
);

create index hook_storage__hook_timestamp on hook_storage (hook_id, owner_id, timestamp);
create index hook_storage__timestamp on hook_storage (timestamp);

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
    @Id
    private HookId id;

    private byte[] adminKey;
    private Long contractId;
    private Long createdTimestamp;
    private Boolean deleted;
    private String extensionPoint;
    private String type;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HookId implements Serializable {
        private Long ownerId;
        private Long hookId;
    }

    // Getters, setters, and utility methods
}
```

#### HookStorage.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/HookStorage.java
public class HookStorage {
    @Id
    private HookStorageId id;

    private byte[] value;
    private Long timestamp;

    @Data
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HookStorageId implements Serializable {
        private Long hookId;
        private Long ownerId;
        private byte[] key;
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
        - contract_id = transaction entity ID if ContractCreate/Update, null if CryptoCreate/Update
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
}
```

Implement in `SqlEntityListener`:

```java

@Override
public void onHook(Hook hook) {
    hookRepository.save(hook);
}

@Override
public void onHookStorage(HookStorage hookStorage) {
    hookStorageRepository.save(hookStorage);
}
```

### 3.1. Hook Storage Updates Processing

Hook storage updates can be delivered through two mechanisms, and the mirror node should support both:

1. **Dedicated Transaction Type**: `LambdaSStoreTransactionBody`
2. **Transaction Record Sidecars**: Similar to contract state changes

#### Option 1: LambdaSStoreTransactionBody (Primary Method)

A new `LambdaSStoreTransactionHandler` will be created to process dedicated hook storage transactions. The handler will:

- Extract the hook_id and owner_id from the transaction body
- Process all storage entries in the transaction
- Create HookStorage entities for each entry with composite ID (hook_id, owner_id, key)

**Processing Logic:**

```
for each storageEntry in transactionBody.storageUpdatesList:
    create HookStorage entity with:
        - composite ID (hook_id, owner_id, storage_key)
        - storage value
        - consensus timestamp
    save HookStorage via entityListener
```

#### Option 2: Transaction Record Sidecars (Alternative Method)

Hook storage updates can also be delivered via transaction record sidecars, similar to how contract state changes are
handled. The base `AbstractTransactionHandler` will be enhanced to process hook storage sidecars from any transaction
type that includes them.

#### Implementation Strategy

The mirror node should support **both approaches** simultaneously:

1. **Primary Path**: `LambdaSStoreTransactionBody` for explicit hook storage operations
2. **Secondary Path**: Sidecar records for hook storage updates that occur during other transactions (e.g., during
   contract calls that trigger hooks)

**Advantages of Dual Support:**

- **Flexibility**: Accommodates different hook execution patterns
- **Consistency**: Aligns with existing contract state change handling
- **Completeness**: Captures all storage updates regardless of trigger mechanism
- **Future-proofing**: Supports evolution of hook execution models

### 4. BlockItemTransformer Enhancement

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
