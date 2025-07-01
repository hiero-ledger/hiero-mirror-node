// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.hiero.mirror.importer.config.CacheConfiguration.*;
import static org.hiero.mirror.importer.util.Utility.aliasToEvmAddress;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@CustomLog
@Named
public class EntityIdServiceImpl implements EntityIdService {

    private static final Optional<EntityId> EMPTY = Optional.of(EntityId.EMPTY);

    private final Cache cache;

    private final EntityRepository entityRepository;

    public EntityIdServiceImpl(
            @Qualifier(CACHE_ALIAS) CacheManager aliasCacheManager, EntityRepository entityRepository) {
        this.cache = aliasCacheManager.getCache(CACHE_NAME);
        this.entityRepository = entityRepository;
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
                        ? cacheLookup(accountId.getAlias(), () -> findByEvmAddress(alias))
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
            case CONTRACTNUM -> Optional.ofNullable(EntityId.of(contractId));
            case EVM_ADDRESS ->
                cacheLookup(
                        contractId.getEvmAddress(),
                        () -> findByEvmAddress(toBytes(contractId.getEvmAddress()), throwRecoverableError));
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

    private @Nonnull Optional<EntityId> cacheLookup(ByteString key, Callable<Optional<EntityId>> loader) {
        try {
            return Objects.requireNonNullElse(cache.get(key, loader), Optional.empty());
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up alias or EVM address {} from cache", key, e);
            return Optional.empty();
        }
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

    @Override
    public void notify(Entity entity) {
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted())) {
            return;
        }

        byte[] aliasBytes = entity.getAlias() != null ? entity.getAlias() : entity.getEvmAddress();
        if (aliasBytes == null) {
            return;
        }

        var alias = DomainUtils.fromBytes(aliasBytes);
        var entityId = Optional.ofNullable(entity.toEntityId());
        EntityType type = entity.getType();

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

    private Optional<EntityId> findByEvmAddress(byte[] evmAddress) {
        return findByEvmAddress(evmAddress, true);
    }

    private Optional<EntityId> findByEvmAddress(byte[] evmAddress, boolean throwRecoverableError) {
        var id = Optional.ofNullable(DomainUtils.fromEvmAddress(evmAddress))
                .or(() -> entityRepository.findByEvmAddress(evmAddress).map(EntityId::of));

        if (id.isEmpty() && throwRecoverableError) {
            Utility.handleRecoverableError("Entity not found for EVM address {}", Hex.encodeHexString(evmAddress));
        }

        return id;
    }

    private Optional<EntityId> findByAlias(byte[] alias) {
        return entityRepository.findByAlias(alias).map(EntityId::of);
    }

    // Try to fall back to the 20-byte evm address recovered from the ECDSA secp256k1 alias
    private Optional<EntityId> findByAliasEvmAddress(byte[] alias) {
        var evmAddress = aliasToEvmAddress(alias);
        if (evmAddress == null) {
            Utility.handleRecoverableError("Unable to find entity for alias {}", Hex.encodeHexString(alias));
            return Optional.empty();
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Trying to find entity by evm address {} recovered from public key alias {}",
                    Hex.encodeHexString(evmAddress),
                    Hex.encodeHexString(alias));
        }

        // Check cache first in case the 20-byte evm address hasn't persisted to db
        return cacheLookup(fromBytes(evmAddress), () -> findByEvmAddress(evmAddress));
    }
}
