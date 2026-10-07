// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_ALIAS;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.importer.util.Utility.aliasToEvmAddress;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@CustomLog
@Named
public class EntityIdServiceImpl implements EntityIdService, TransactionSynchronization {

    private static final Optional<EntityId> EMPTY = Optional.of(EntityId.EMPTY);
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final Cache cache;
    private final EntityRepository entityRepository;

    /**
     * Temporary cache populated when entities are created in the current transaction. It's visible to lookups within
     * the transaction, but its entries are added to the cache only once the transaction commits.
     */
    private final Map<ByteString, EntityId> inTransactionCache = new ConcurrentHashMap<>();

    /**
     * The entities deleted in the current transaction. The deletes are written to the db at the end of
     * the transaction, and their cached mappings are evicted once it commits. Until then, any lookup result resolving to
     * these entities, whether from the cache or the db, is ignored so entities deleted earlier in the transaction
     * aren't returned.
     */
    private final Set<EntityId> inTransactionDeletes = ConcurrentHashMap.newKeySet();

    public EntityIdServiceImpl(@Qualifier(CACHE_ALIAS) CacheManager cacheManager, EntityRepository entityRepository) {
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.entityRepository = entityRepository;
    }

    /**
     * Called once the current transaction completes.
     * On status COMMITTED - adds the in-transaction cache entries to the cache.
     * On status UNKNOWN - evicts the cache entries that match the keys of the in-transaction cache entries. That is
     * because new mappings may or may not be persisted, so better be safe by removing those keys from the cache.
     * On status !ROLLED_BACK - evicts each cache entry with value matching one from the inTransactionDeletes set.
     * At the end both inTransactionCache and inTransactionDeletes are cleared.
     */
    @Override
    public void afterCompletion(int status) {
        try {
            if (status == STATUS_COMMITTED) {
                inTransactionCache.forEach((key, entityId) -> cache.put(key, Optional.of(entityId)));
            } else if (status == STATUS_UNKNOWN) {
                inTransactionCache.keySet().forEach(cache::evict);
            }
            if (status != STATUS_ROLLED_BACK) {
                evictCacheEntries(inTransactionDeletes);
            }
        } finally {
            inTransactionDeletes.clear();
            inTransactionCache.clear();
        }
    }

    @Override
    public Optional<EntityId> lookup(AccountID accountId) {
        if (accountId == null || accountId.equals(AccountID.getDefaultInstance())) {
            return EMPTY;
        }

        return switch (accountId.getAccountCase()) {
            case ACCOUNTNUM -> Optional.ofNullable(EntityId.of(accountId));
            case ALIAS -> {
                byte[] alias = toBytes(accountId.getAlias());
                yield alias.length == EVM_ADDRESS_LENGTH
                        ? lookupEvmAddress(accountId.getAlias(), alias, true)
                        : cacheLookup(accountId.getAlias(), () -> findByAlias(alias))
                                .or(() -> findByAliasEvmAddress(alias));
            }
            default -> {
                Utility.handleRecoverableError(
                        "Invalid Account Case for AccountID {}: {}", accountId, accountId.getAccountCase());
                yield Optional.empty();
            }
        };
    }

    @Override
    public Optional<EntityId> lookup(AccountID... accountIds) {
        return doLookups(accountIds, this::lookup);
    }

    @Override
    public Optional<EntityId> lookup(ContractID contractId) {
        return lookup(contractId, true);
    }

    @Override
    public Optional<EntityId> lookup(ContractID contractId, boolean throwRecoverableError) {
        if (contractId == null || contractId.equals(ContractID.getDefaultInstance())) {
            return EMPTY;
        }

        return switch (contractId.getContractCase()) {
            case CONTRACTNUM -> convertSafely(contractId);
            case EVM_ADDRESS ->
                lookupEvmAddress(
                        contractId.getEvmAddress(), toBytes(contractId.getEvmAddress()), throwRecoverableError);
            default -> {
                Utility.handleRecoverableError("Invalid ContractID: {}", contractId);
                yield Optional.empty();
            }
        };
    }

    @Override
    public Optional<EntityId> lookup(ContractID... contractIds) {
        return doLookups(contractIds, this::lookup);
    }

    @Override
    public void notify(Entity entity) {
        if (entity == null) {
            return;
        }

        if (Boolean.TRUE.equals(entity.getDeleted())) {
            delete(entity);
            return;
        }

        if (Boolean.FALSE.equals(entity.getDeleted()) && !inTransactionDeletes.isEmpty()) {
            inTransactionDeletes.remove(entity.toEntityId());
        }

        final byte[] aliasBytes = entity.getAlias() != null ? entity.getAlias() : entity.getEvmAddress();
        if (aliasBytes == null) {
            return;
        }

        final var alias = fromBytes(aliasBytes);
        final var entityId = entity.toEntityId();
        final var type = entity.getType();

        switch (type) {
            case ACCOUNT -> {
                stageCacheEntry(alias, entityId);
                // Accounts can have an alias and an EVM address so warm the cache with both
                if (entity.getAlias() != null && entity.getEvmAddress() != null) {
                    stageCacheEntry(fromBytes(entity.getEvmAddress()), entityId);
                }
            }
            case CONTRACT -> stageCacheEntry(alias, entityId);
            default -> Utility.handleRecoverableError("Invalid Entity: {} entity can't have alias", type);
        }
    }

    private @NonNull Optional<EntityId> cacheLookup(ByteString key, Callable<Optional<EntityId>> loader) {
        try {
            final var created = inTransactionCache.get(key);
            final var entityId = created != null
                    ? Optional.of(created)
                    : Objects.requireNonNullElse(cache.get(key, loader), Optional.<EntityId>empty());
            return entityId.isPresent() && inTransactionDeletes.contains(entityId.get()) ? Optional.empty() : entityId;
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up alias or EVM address {} from cache", key, e);
            return Optional.empty();
        }
    }

    // It's possible for failed EthereumTransactions to attempt to call non-existent addresses that show up in receipt
    private Optional<EntityId> convertSafely(ContractID contractId) {
        final var entityId = EntityId.tryOf(contractId);
        return EntityId.isEmpty(entityId) ? Optional.empty() : Optional.ofNullable(entityId);
    }

    private void delete(final Entity entity) {
        final var type = entity.getType();
        if (type != null && type != EntityType.ACCOUNT && type != EntityType.CONTRACT) {
            return;
        }

        final var entityId = entity.toEntityId();
        if (!registerSynchronization()) {
            // If not within a transaction evict right away
            evictCacheEntries(Set.of(entityId));
            return;
        }

        inTransactionDeletes.add(entityId);
    }

    private <T extends GeneratedMessage> Optional<EntityId> doLookups(
            T[] entityIdProtos, Function<T, Optional<EntityId>> loader) {
        for (T entityIdProto : entityIdProtos) {
            var entityId = loader.apply(entityIdProto);
            if (!entityId.isEmpty() && !EntityId.isEmpty(entityId.get())) {
                return entityId;
            }
        }
        return EMPTY;
    }

    /**
     * Evicts every cache entry matching a value from the entityIds set.
     * If the cache can't be searched by value, it's cleared as the only safe fallback.
     */
    private void evictCacheEntries(final Set<EntityId> entityIds) {
        if (entityIds.isEmpty()) {
            return;
        }

        if (!(cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache)) {
            cache.clear();
            return;
        }

        nativeCache
                .asMap()
                .values()
                .removeIf(value -> value instanceof Optional<?> entityId
                        && entityId.isPresent()
                        && entityIds.contains(entityId.get()));
    }

    private Optional<EntityId> findByAlias(byte[] alias) {
        return entityRepository.findByAlias(alias).map(EntityId::of);
    }

    // Try to fall back to the 20-byte evm address recovered from the ECDSA secp256k1 alias
    private Optional<EntityId> findByAliasEvmAddress(byte[] alias) {
        var evmAddress = aliasToEvmAddress(alias);
        if (evmAddress == null) {
            Utility.handleRecoverableError("Unable to find entity for alias {}", HEX_FORMAT.formatHex(alias));
            return Optional.empty();
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Trying to find entity by evm address {} recovered from public key alias {}",
                    HEX_FORMAT.formatHex(evmAddress),
                    HEX_FORMAT.formatHex(alias));
        }

        // Check the mappings created in the current transaction and the cache first in case the 20-byte evm address
        // hasn't persisted to db
        return lookupEvmAddress(fromBytes(evmAddress), evmAddress, true);
    }

    private Optional<EntityId> findByEvmAddress(byte[] evmAddress, boolean throwRecoverableError) {
        final var entityId = entityRepository.findByEvmAddress(evmAddress).map(EntityId::of);

        if (entityId.isEmpty() && throwRecoverableError) {
            Utility.handleRecoverableError("Entity not found for EVM address {}", HEX_FORMAT.formatHex(evmAddress));
        }

        return entityId;
    }

    private Optional<EntityId> lookupEvmAddress(ByteString key, byte[] evmAddress, boolean throwRecoverableError) {
        final var encoded = DomainUtils.fromEvmAddress(evmAddress);
        if (encoded != null) {
            return Optional.of(encoded);
        }

        return cacheLookup(key, () -> findByEvmAddress(evmAddress, throwRecoverableError));
    }

    /**
     * Registers this service to be called back once the current transaction completes.
     * @return false if there's no transaction to register with
     */
    private boolean registerSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        if (!TransactionSynchronizationManager.getSynchronizations().contains(this)) {
            // Calls afterCompletion() once the current transaction commits or rolls back
            TransactionSynchronizationManager.registerSynchronization(this);
        }

        return true;
    }

    /**
     * Stages a cache entry for an entity created in the current transaction, to be cached once the transaction
     * commits. If no transaction to register with is found, caches it right away.
     */
    private void stageCacheEntry(final ByteString key, final EntityId entityId) {
        if (!registerSynchronization()) {
            cache.put(key, Optional.of(entityId));
            return;
        }

        inTransactionCache.put(key, entityId);
    }
}
