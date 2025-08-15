# HIP-1195 Mirror Node Hooks Support - Design Document

## Overview

This design document outlines the implementation plan for supporting HIP-1195 hooks in the Hiero mirror node. The
implementation will enable extraction, storage, and querying of hook data from blockchain transactions, supporting the
new REST APIs for hooks and hook storage.

## Goals

- Enhance the database schema to store hook information, hook storage data, and historical state
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
4. **Historical Tracking**: Hook state changes are tracked using the existing history table pattern

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
      "hook_id": 1,
      "owner_id": "0.0.123",
      "type": "LAMBDA"
    }
  ],
  "links": {
    "next": "/api/v1/accounts/0.0.123/hooks?timestamp=gt:1726874345.123456789&limit=1"
  }
}
```

**Note**: Hooks in the response are always sorted based on their `created_timestamp` in ascending order. The `timestamp`
parameter in the `links.next` URL uses the format `timestamp=gt:1726874345.123456789` for pagination. _Team needs to
review this approach._

### 2. Hook Storage API

**Note**: We are currently trying to figure out whether to call it "state" or "storage". Will need to verify from
Michael on what they support on their APIs.

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

## Database Schema Design

### Query Requirements

Hooks can be fetched based on the following criteria:

- **By owner_id**: Get all hooks owned by a specific account
- **By contract_id**: Get all hooks associated with a specific contract
- **By timestamp**: Support pagination and time-based filtering
- **By hook_id**: Get specific hooks by their ID (combined with owner_id for uniqueness)
- **By owner_id + created_timestamp**: Support efficient sorting and pagination of hooks by owner
- **By contract_id + created_timestamp**: Support efficient sorting and pagination of hooks by contract

The database indexes must support these query patterns efficiently for optimal performance.

### 1. Hooks Table

Create a new table to store hook information:

```sql
-- V1.111.0__add_hooks_support.sql
create type hook_type as enum ('LAMBDA', 'PURE');
create type extension_point as enum ('ACCOUNT_ALLOWANCE_HOOK');

create table if not exists hook
(
    id                bigserial primary key,
    owner_id          bigint not null,
    hook_id           bigint not null,
    admin_key         bytea,
    contract_id       bigint,
    created_timestamp bigint not null,
    deleted           boolean not null default false,
    extension_point   extension_point not null,
    type              hook_type not null,
    timestamp_range   int8range not null,

    constraint hook__owner_hook_id unique (owner_id, hook_id)
);

-- Indexes to support the required query patterns
create index if not exists hook__owner_id_timestamp on hook using gist (owner_id, timestamp_range);
create index if not exists hook__owner_id_created_timestamp on hook (owner_id, created_timestamp);
create index if not exists hook__contract_id_timestamp on hook using gist (contract_id, timestamp_range) where contract_id is not null;
create index if not exists hook__contract_id_created_timestamp on hook (contract_id, created_timestamp) where contract_id is not null;
```

### 2. Hook History Table

For historical tracking:

```sql
CREATE TABLE hook_history
(
    id                BIGINT    NOT NULL,
    owner_id          BIGINT    NOT NULL,
    hook_id           BIGINT    NOT NULL,
    admin_key         BYTEA,
    contract_id       BIGINT,
    created_timestamp BIGINT    NOT NULL,
    deleted           BOOLEAN DEFAULT FALSE,
    extension_point   TEXT      NOT NULL,
    type              TEXT      NOT NULL,
    timestamp_range   INT8RANGE NOT NULL,

    PRIMARY KEY (id, timestamp_range)
);

CREATE INDEX hook_history__owner_id_timestamp ON hook_history (owner_id, timestamp_range);
```

### 3. Hook Storage Table

For hook EVM storage data:

```sql
-- V1.111.1__add_hook_storage_table.sql
CREATE TABLE hook_storage
(
    hook_id   BIGINT NOT NULL,
    owner_id  BIGINT NOT NULL,
    slot      BYTEA  NOT NULL,
    value     BYTEA  NOT NULL,
    timestamp BIGINT NOT NULL,

    PRIMARY KEY (hook_id, owner_id, slot),
    CONSTRAINT fk_hook_storage__hook FOREIGN KEY (owner_id, hook_id) REFERENCES hook (owner_id, hook_id)
);

CREATE INDEX hook_storage__hook_timestamp ON hook_storage (hook_id, owner_id, timestamp);
CREATE INDEX hook_storage__timestamp ON hook_storage (timestamp);
```

### 4. Transaction Table Enhancement

Add hook_id tracking to the transaction table:

```sql
-- V1.111.2__add_transaction_hook_id.sql
ALTER TABLE transaction
    ADD COLUMN hook_id BIGINT;
CREATE INDEX transaction__hook_id ON transaction (hook_id) WHERE hook_id IS NOT NULL;
```

## Importer Module Changes

### 1. Domain Models

Create new domain classes:

#### Hook.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/Hook.java
@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public class Hook implements History {
    @Id
    private Long id;

    private Long ownerId;
    private Long hookId;
    private byte[] adminKey;
    private Long contractId;
    private Long createdTimestamp;
    private Boolean deleted;
    private String extensionPoint;
    private String type;
    private Range<Long> timestampRange;

    // Getters, setters, and utility methods
}
```

#### HookStorage.java

```java
// common/src/main/java/org/hiero/mirror/common/domain/entity/HookStorage.java
@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
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
        private byte[] slot;
    }
}
```

### 2. Transaction Handler Enhancements

Modify existing transaction handlers to extract `HookCreationDetails`:

#### CryptoCreateTransactionHandler.java

```java

@Override
private void doUpdateEntity(Entity entity, RecordItem recordItem) {
    // ... existing code ...

    processHooks(entity, recordItem);
    entityListener.onEntity(entity);
}

private void processHooks(Entity entity, RecordItem recordItem) {
    var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
    if (!transactionBody.getHookCreationDetailsList().isEmpty()) {
        for (var hookDetails : transactionBody.getHookCreationDetailsList()) {
            createHook(entity, hookDetails, recordItem);
        }
    }
}

private void createHook(Entity entity, HookCreationDetails hookDetails, RecordItem recordItem) {
    var hook = Hook.builder()
            .ownerId(entity.getId())
            .hookId(hookDetails.getHookId())
            .extensionPoint(hookDetails.getExtensionPoint().name())
            .createdTimestamp(recordItem.getConsensusTimestamp())
            .deleted(false)
            .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
            .build();

    // Set hook type and admin key based on hook type
    switch (hookDetails.getHookCase()) {
        case PURE_EVM_HOOK:
            hook.setType("PURE");
            var pureHook = hookDetails.getPureEvmHook();
            if (pureHook.hasAdminKey()) {
                hook.setAdminKey(pureHook.getAdminKey().toByteArray());
            }
            // Set contract ID from runtime bytecode or creation
            break;
        case LAMBDA_EVM_HOOK:
            hook.setType("LAMBDA");
            var lambdaHook = hookDetails.getLambdaEvmHook();
            if (lambdaHook.hasAdminKey()) {
                hook.setAdminKey(lambdaHook.getAdminKey().toByteArray());
            }
            // Set contract ID from runtime bytecode or creation
            break;
    }

    entityListener.onHook(hook);
}
```

Similar modifications will be made to:

- `CryptoUpdateTransactionHandler.java`
- `ContractCreateTransactionHandler.java`
- `ContractUpdateTransactionHandler.java`

#### ContractCallTransactionHandler.java

For tracking hook-triggered contract calls:

```java

@Override
private void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
    var transactionBody = recordItem.getTransactionBody().getContractCall();

    // Track hook ID that triggered this contract call
    if (transactionBody.hasHookId()) {
        transaction.setHookId(transactionBody.getHookId());
    }

    // ... existing code ...
}
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

#### LambdaSStoreTransactionHandler.java

```java
// New transaction handler for hook storage updates via LambdaSStoreTransactionBody
@Named
class LambdaSStoreTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;

    LambdaSStoreTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.LAMBDASSTORE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getLambdaSStore();
        return EntityId.of(transactionBody.getOwnerId());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getLambdaSStore();

        // Store hook_id reference for transaction tracking
        transaction.setHookId(transactionBody.getHookId());

        processStorageUpdates(transactionBody, recordItem);
    }

    private void processStorageUpdates(LambdaSStoreTransactionBody transactionBody, RecordItem recordItem) {
        for (var storageEntry : transactionBody.getStorageUpdatesList()) {
            var hookStorage = HookStorage.builder()
                    .id(HookStorage.HookStorageId.builder()
                            .hookId(transactionBody.getHookId())
                            .ownerId(EntityId.of(transactionBody.getOwnerId()).getId())
                            .slot(DomainUtils.toBytes(storageEntry.getSlot()))
                            .build())
                    .value(DomainUtils.toBytes(storageEntry.getValue()))
                    .timestamp(recordItem.getConsensusTimestamp())
                    .build();

            entityListener.onHookStorage(hookStorage);
        }
    }
}
```

#### Transaction Type Addition

The new transaction type needs to be added to the `TransactionType` enum:

```java
// Add to TransactionType.java
LAMBDASSTORE(LambdaSStoreTransactionSupplier .class); // Replace 99 with appropriate proto ID
```

#### Transaction Supplier

Following the existing pattern, we need to add a transaction supplier:

```java
// monitor/src/main/java/org/hiero/mirror/monitor/publish/transaction/LambdaSStoreTransactionSupplier.java
@Data
public class LambdaSStoreTransactionSupplier implements TransactionSupplier<LambdaSStoreTransaction> {

    @Min(1)
    private final long hookId = 1;

    @NotBlank
    private String ownerId;

    @NotBlank
    private final String slot = "0x0000000000000000000000000000000000000000000000000000000000000000";

    @NotBlank
    private final String value = "0x00000000000000000000000000000000000000000000000000000000000003e8";

    @Min(1)
    private final long maxTransactionFee = 10_000_000L;

    @Getter(lazy = true)
    private final AccountId ownerAccountId = AccountId.fromString(ownerId);

    @Override
    public LambdaSStoreTransaction get() {
        return new LambdaSStoreTransaction()
                .setHookId(hookId)
                .setOwnerId(getOwnerAccountId())
                .addStorageUpdate(slot, value)
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee));
    }
}
```

#### Transaction Domain Model Enhancement

The Transaction domain model needs to be updated to include the hook_id field:

```java
// common/src/main/java/org/hiero/mirror/common/domain/transaction/Transaction.java
@Data
@Entity
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Transaction {
    // ... existing fields ...

    private Long hookId;  // New field to track hook_id from LambdaSStoreTransactionBody and ContractCallTransactionBody

    // ... existing methods ...
}
```

#### Option 2: Transaction Record Sidecars (Alternative Method)

Hook storage updates can also be delivered via transaction record sidecars, similar to how contract state changes are
handled:

```java
// Enhanced base transaction handler to process hook storage sidecars
public abstract class AbstractTransactionHandler implements TransactionHandler {

    // ... existing methods ...

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        doUpdateTransaction(transaction, recordItem);

        // Process hook storage updates from sidecars
        processHookStorageSidecars(recordItem);
    }

    protected void processHookStorageSidecars(RecordItem recordItem) {
        var sidecarRecords = recordItem.getSidecarRecords();

        for (var sidecar : sidecarRecords) {
            if (sidecar.hasHookStorageUpdates()) {
                var hookStorageUpdate = sidecar.getHookStorageUpdates();

                for (var storageEntry : hookStorageUpdate.getStorageUpdatesList()) {
                    var hookStorage = HookStorage.builder()
                            .id(HookStorage.HookStorageId.builder()
                                    .hookId(storageEntry.getHookId())
                                    .ownerId(storageEntry.getOwnerId())
                                    .slot(DomainUtils.toBytes(storageEntry.getSlot()))
                                    .build())
                            .value(DomainUtils.toBytes(storageEntry.getValue()))
                            .timestamp(recordItem.getConsensusTimestamp())
                            .build();

                    entityListener.onHookStorage(hookStorage);
                }
            }
        }
    }
}
```

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

### 4. Repository Classes

#### HookRepository.java

```java
// importer/src/main/java/org/hiero/mirror/importer/repository/HookRepository.java
@Repository
public interface HookRepository extends JpaRepository<Hook, Long> {

    // Query by owner_id (for account hooks API)
    @Query("SELECT h FROM Hook h WHERE h.ownerId = :ownerId AND upper(h.timestampRange) IS NULL ORDER BY h.createdTimestamp")
    List<Hook> findActiveHooksByOwner(@Param("ownerId") Long ownerId);

    // Query by owner_id with timestamp pagination
    @Query("SELECT h FROM Hook h WHERE h.ownerId = :ownerId AND h.createdTimestamp > :timestamp AND upper(h.timestampRange) IS NULL ORDER BY h.createdTimestamp")
    List<Hook> findActiveHooksByOwnerAfterTimestamp(@Param("ownerId") Long ownerId, @Param("timestamp") Long timestamp,
            Pageable pageable);

    // Query by contract_id
    @Query("SELECT h FROM Hook h WHERE h.contractId = :contractId AND upper(h.timestampRange) IS NULL ORDER BY h.createdTimestamp")
    List<Hook> findActiveHooksByContract(@Param("contractId") Long contractId);

    // Query by contract_id with timestamp pagination
    @Query("SELECT h FROM Hook h WHERE h.contractId = :contractId AND h.createdTimestamp > :timestamp AND upper(h.timestampRange) IS NULL ORDER BY h.createdTimestamp")
    List<Hook> findActiveHooksByContractAfterTimestamp(@Param("contractId") Long contractId,
            @Param("timestamp") Long timestamp, Pageable pageable);

    // Query by specific hook_id and owner_id
    @Query("SELECT h FROM Hook h WHERE h.ownerId = :ownerId AND h.hookId = :hookId AND upper(h.timestampRange) IS NULL")
    Optional<Hook> findActiveHook(@Param("ownerId") Long ownerId, @Param("hookId") Long hookId);

    // Query with timestamp range filtering for pagination support
    @Query("SELECT h FROM Hook h WHERE h.createdTimestamp > :timestampGt AND upper(h.timestampRange) IS NULL ORDER BY h.createdTimestamp")
    List<Hook> findActiveHooksAfterTimestamp(@Param("timestampGt") Long timestampGt, Pageable pageable);
}
```

#### HookStorageRepository.java

```java
// importer/src/main/java/org/hiero/mirror/importer/repository/HookStorageRepository.java
@Repository
public interface HookStorageRepository extends JpaRepository<HookStorage, HookStorage.HookStorageId> {

    @Query("SELECT hs FROM HookStorage hs WHERE hs.id.hookId = :hookId AND hs.id.ownerId = :ownerId ORDER BY hs.id.slot")
    List<HookStorage> findByHookIdAndOwnerId(@Param("hookId") Long hookId, @Param("ownerId") Long ownerId);
}
```

## REST API Implementation

### 1. Controller

#### HookController.java

```java
// rest-java/src/main/java/org/hiero/mirror/restjava/controller/HookController.java
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class HookController {

    private final HookService hookService;

    @GetMapping("/{idOrAliasOrEvmAddress}/hooks")
    public HooksResponse getAccountHooks(
            @PathVariable String idOrAliasOrEvmAddress,
            @RequestParam(value = "hook.id", required = false) String hookIdParam,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "asc") String order) {

        var accountId = EntityIdParameter.valueOf(idOrAliasOrEvmAddress).getId();
        return hookService.getAccountHooks(accountId, hookIdParam, limit, order);
    }

    @GetMapping("/{idOrAliasOrEvmAddress}/hooks/{hookId}/storage")
    public HookStorageResponse getHookStorage(
            @PathVariable String idOrAliasOrEvmAddress,
            @PathVariable Long hookId,
            @RequestParam(value = "key", required = false) String keyParam,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "asc") String order) {

        var accountId = EntityIdParameter.valueOf(idOrAliasOrEvmAddress).getId();
        return hookService.getHookStorage(accountId, hookId, keyParam, limit, order);
    }
}
```

### 2. Service Layer

#### HookService.java

```java
// rest-java/src/main/java/org/hiero/mirror/restjava/service/HookService.java
@Service
@RequiredArgsConstructor
public class HookService {

    private final HookRepository hookRepository;
    private final HookStorageRepository hookStorageRepository;
    private final EntityService entityService;

    public HooksResponse getAccountHooks(Long accountId, String hookIdParam, int limit, String order) {
        // Validate account exists
        entityService.lookup(accountId);

        // Parse hook ID filter if provided
        var hooks = hookRepository.findActiveHooksByOwner(accountId);

        // Apply filtering and pagination
        // Build response with proper linking
        return buildHooksResponse(hooks, accountId, hookIdParam, limit, order);
    }

    public HookStorageResponse getHookStorage(Long accountId, Long hookId, String keyParam, int limit, String order) {
        // Validate hook exists
        var hook = hookRepository.findActiveHook(accountId, hookId)
                .orElseThrow(() -> new EntityNotFoundException("Hook not found"));

        // Get storage entries
        var storageEntries = hookStorageRepository.findByHookIdAndOwnerId(hookId, accountId);

        // Apply filtering and pagination
        // Build response with proper linking
        return buildStorageResponse(hook, storageEntries, keyParam, limit, order);
    }

    private HooksResponse buildHooksResponse(List<Hook> hooks, Long accountId, String hookIdParam, int limit,
            String order) {
        // Implementation for response building with pagination
    }

    private HookStorageResponse buildStorageResponse(Hook hook, List<HookStorage> storage, String keyParam, int limit,
            String order) {
        // Implementation for storage response building with pagination
    }
}
```

### 3. Response Models

#### HooksResponse.java

```java
// rest-java/src/main/java/org/hiero/mirror/restjava/dto/HooksResponse.java
@Data
@Builder
public class HooksResponse {
    private List<HookDto> hooks;
    private Links links;

    @Data
    @Builder
    public static class HookDto {
        private KeyDto adminKey;
        private String contractId;
        private String createdTimestamp;
        private Boolean deleted;
        private String extensionPoint;
        private Long hookId;
        private String ownerId;
        private String type;
    }
}
```

#### HookStorageResponse.java

```java
// rest-java/src/main/java/org/hiero/mirror/restjava/dto/HookStorageResponse.java
@Data
@Builder
public class HookStorageResponse {
    private Long hookId;
    private String ownerId;
    private List<StorageEntryDto> storage;
    private Links links;

    @Data
    @Builder
    public static class StorageEntryDto {
        private String key;
        private String timestamp;
        private String value;
    }
}
```

## Testing Strategy

### 1. Unit Tests

- Transaction handler tests for hook extraction
- Service layer tests for business logic
- Repository tests for data access

### 2. Integration Tests

- End-to-end API tests
- Database migration tests
- Transaction processing tests

### 3. Test Data

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

1. **Historical Storage Queries**: Do we need to support querying historical states of hook storage, or only the current
   state? This impacts the storage table design.

2. **Storage Size Limits**: What are the practical limits for hook storage per account? Should we enforce quotas?

3. **Indexing Strategy**: Which additional indexes might be needed for complex hook queries that we haven't identified
   yet?

4. **Protobuf Changes and Record Stream Timeline**: Check with Michael Tinker on the availability timeline for protobuf
   changes and record stream implementation for hook support.

5. **API Naming Convention - Hook Storage vs State**: Should the API endpoints use "storage" or "state" terminology?
   This should be aligned with what Michael Tinker is using in their consensus node APIs to maintain consistency across
   the platform.

## Future Enhancements

### 1. Additional Extension Points

- Support for new hook types as they're added
- Dynamic extension point discovery: As new extension points are added to the network (beyond the initial
  `ACCOUNT_ALLOWANCE_HOOK`), the mirror node should automatically recognize and process them without requiring code
  changes. This could be achieved by:
  - Reading extension point definitions from protobuf enums dynamically
  - Using configuration-driven processing for unknown extension points
  - Automatic database schema adaptation for new hook metadata fields

### 2. Advanced Querying

- Complex filtering capabilities
- Full-text search on hook metadata
- Historical hook state queries

### 3. Performance Optimizations

- Read replicas for hook data
- Advanced caching strategies
- Query optimization
