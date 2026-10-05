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

    /*
     * The entities deleted in the current transaction. Delete isn't persisted until the end of the transaction so
     * until then the database still returns the entity. Any mapping to these entities is ignored during the
     * transaction and evicted once it completes.
     */
    private final Set<EntityId> pendingDeletes = ConcurrentHashMap.newKeySet();

    public EntityIdServiceImpl(@Qualifier(CACHE_ALIAS) CacheManager cacheManager, EntityRepository entityRepository) {
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.entityRepository = entityRepository;
    }

    @Override
    public void afterCompletion(int status) {
        try {
            if (status != STATUS_ROLLED_BACK) {
                evict(pendingDeletes);
            }
        } finally {
            pendingDeletes.clear();
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

        if (Boolean.FALSE.equals(entity.getDeleted()) && !pendingDeletes.isEmpty()) {
            pendingDeletes.remove(entity.toEntityId());
        }

        final byte[] aliasBytes = entity.getAlias() != null ? entity.getAlias() : entity.getEvmAddress();
        if (aliasBytes == null) {
            return;
        }

        final var alias = fromBytes(aliasBytes);
        final var entityId = Optional.of(entity.toEntityId());
        final var type = entity.getType();

        switch (type) {
            case ACCOUNT -> {
                cache.put(alias, entityId);
                // Accounts can have an alias and an EVM address so warm the cache with both
                if (entity.getAlias() != null && entity.getEvmAddress() != null) {
                    cache.put(fromBytes(entity.getEvmAddress()), entityId);
                }
            }
            case CONTRACT -> cache.put(alias, entityId);
            default -> Utility.handleRecoverableError("Invalid Entity: {} entity can't have alias", type);
        }
    }

    private @NonNull Optional<EntityId> cacheLookup(ByteString key, Callable<Optional<EntityId>> loader) {
        try {
            final var entityId = Objects.requireNonNullElse(cache.get(key, loader), Optional.<EntityId>empty());
            return entityId.isPresent() && pendingDeletes.contains(entityId.get()) ? Optional.empty() : entityId;
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
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // If not within a transaction evict right away
            evict(Set.of(entityId));
            return;
        }

        if (!TransactionSynchronizationManager.getSynchronizations().contains(this)) {
            // Calls afterCompletion() once the current transaction commits or rolls back
            TransactionSynchronizationManager.registerSynchronization(this);
        }

        pendingDeletes.add(entityId);
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

    private void evict(final Set<EntityId> entityIds) {
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

        // Check cache first in case the 20-byte evm address hasn't persisted to db
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
}
