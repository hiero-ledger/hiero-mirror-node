// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.importer.util.UtilityTest.ALIAS_ECDSA_SECP256K1;
import static org.hiero.mirror.importer.util.UtilityTest.EVM_ADDRESS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_UNKNOWN;

import com.github.benmanes.caffeine.cache.Cache;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

final class EntityIdServiceImplTransactionTest {

    private static final AccountID ALIAS_ACCOUNT_ID = AccountID.newBuilder()
            .setAlias(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1))
            .build();
    private static final EntityId CREATED = EntityId.of(3000L);
    private static final EntityId DELETED = EntityId.of(1000L);
    private static final AccountID EVM_ADDRESS_ACCOUNT_ID =
            AccountID.newBuilder().setAlias(DomainUtils.fromBytes(EVM_ADDRESS)).build();
    private static final int MAXIMUM_SIZE = 2;
    private static final EntityId RECREATED = EntityId.of(2000L);

    private final EntityRepository entityRepository = mock(EntityRepository.class);

    private EntityIdServiceImpl entityIdService;
    private Cache<Object, Object> nativeCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of(CACHE_NAME));
        cacheManager.setCacheSpecification("maximumSize=" + MAXIMUM_SIZE);
        entityIdService = new EntityIdServiceImpl(cacheManager, entityRepository);
        nativeCache = (Cache<Object, Object>) cacheManager.getCache(CACHE_NAME).getNativeCache();
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createdAndDeletedEntityNotCachedOnCommit() {
        // given
        entityIdService.notify(created(CREATED));
        entityIdService.notify(deleted(CREATED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();

        // when
        complete(STATUS_COMMITTED);

        // then
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(CREATED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void createdMappingsCachedImmediatelyWithoutTransaction() {
        // given
        TransactionSynchronizationManager.clearSynchronization();

        // when
        entityIdService.notify(created(CREATED));

        // then
        assertThat(nativeCache.asMap())
                .containsEntry(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1), Optional.of(CREATED));
    }

    @Test
    void createdMappingsCachedOnCommit() {
        // given
        entityIdService.notify(created(CREATED));

        // when the transaction is in progress, the mappings resolve without being cached
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(CREATED);
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).hasValue(CREATED);
        assertThat(nativeCache.asMap()).isEmpty();

        // then they're cached once it commits
        complete(STATUS_COMMITTED);
        assertThat(nativeCache.asMap())
                .containsEntry(DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1), Optional.of(CREATED))
                .containsEntry(DomainUtils.fromBytes(EVM_ADDRESS), Optional.of(CREATED));
    }

    @Test
    void createdMappingsDiscardedOnRollback() {
        // given
        entityIdService.notify(created(CREATED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(CREATED);

        // when
        persisted(null);
        complete(STATUS_ROLLED_BACK);

        // then the mappings are discarded
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(CREATED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void createdMappingsReloadedFromDatabaseOnUnknownStatus() {
        // given the aliases were looked up and not found before the entity was created
        persisted(null);
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();
        entityIdService.notify(created(CREATED));

        // when it's not known whether the transaction is committed, though it is
        persisted(CREATED);
        complete(STATUS_UNKNOWN);

        // then the mappings are loaded from the database instead of the cache
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.empty());
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(CREATED);
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).hasValue(CREATED);
    }

    @Test
    void deletedAliasResolvesToHollowAccountWithSameEvmAddress() {
        // given the public key alias is loaded from the database after the delete, before the delete is persisted
        persisted(DELETED);
        entityIdService.notify(deleted(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();

        // when a hollow account reuses the evm address
        final var recreated = entity(RECREATED, false);
        recreated.setAlias(EVM_ADDRESS);
        recreated.setEvmAddress(EVM_ADDRESS);
        entityIdService.notify(recreated);
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(RECREATED);

        // then the public key alias resolves to it once the delete is persisted, without relying on the cached mapping
        // to the deleted account expiring
        when(entityRepository.findByAlias(ALIAS_ECDSA_SECP256K1)).thenReturn(Optional.empty());
        when(entityRepository.findByEvmAddress(EVM_ADDRESS)).thenReturn(Optional.of(RECREATED.getId()));
        complete(STATUS_COMMITTED);
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(RECREATED);
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).hasValue(RECREATED);
    }

    @Test
    void deletedEntityNotResolvedAfterEvictionAndReload() {
        // given the deleted account's mappings are loaded from the database, which returns the account until the
        // delete is persisted
        final var aliasKey = DomainUtils.fromBytes(ALIAS_ECDSA_SECP256K1);
        persisted(DELETED);
        entityIdService.notify(deleted(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(nativeCache.policy().getIfPresentQuietly(aliasKey)).isEqualTo(Optional.of(DELETED));

        // when other aliases are looked up until the cache evicts the deleted account's alias
        for (long id = 1L; id <= 1000L && nativeCache.policy().getIfPresentQuietly(aliasKey) != null; id++) {
            final var alias = new byte[] {0x12, 0x20, (byte) (id >> 8), (byte) id};
            final var accountId = AccountID.newBuilder()
                    .setAlias(DomainUtils.fromBytes(alias))
                    .build();
            when(entityRepository.findByAlias(alias)).thenReturn(Optional.of(id));

            // Look up repeatedly so the new entry is used more frequently than the deleted account's and replaces it
            for (int i = 0; i < 5; i++) {
                assertThat(entityIdService.lookup(accountId)).hasValue(EntityId.of(id));
            }

            nativeCache.cleanUp();
        }

        // then the account reloaded from the database is still not resolved
        assertThat(nativeCache.policy().getIfPresentQuietly(aliasKey)).isNull();
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();
        verify(entityRepository, times(2)).findByAlias(ALIAS_ECDSA_SECP256K1);
    }

    @Test
    void deletedMappingsEvictedImmediatelyWithoutTransaction() {
        // given
        TransactionSynchronizationManager.clearSynchronization();
        final var account = entity(DELETED, false);
        account.setAlias(ALIAS_ECDSA_SECP256K1);
        account.setEvmAddress(EVM_ADDRESS);
        entityIdService.notify(account);
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(DELETED);

        // when
        entityIdService.notify(deleted(DELETED));

        // then
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).isEmpty();
    }

    @Test
    void deletedMappingsEvictedOnCommit() {
        // given the database returns the deleted account until the delete is persisted
        persisted(DELETED);
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(DELETED);
        entityIdService.notify(deleted(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();

        // when
        persisted(null);
        complete(STATUS_COMMITTED);

        // then the cached mapping is evicted so the alias is looked up from the database again
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        verify(entityRepository, times(2)).findByAlias(ALIAS_ECDSA_SECP256K1);
    }

    @Test
    void deletedMappingsEvictedOnUnknownStatus() {
        // given
        persisted(DELETED);
        entityIdService.notify(deleted(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();

        // when it's not known whether the delete is persisted
        persisted(null);
        complete(STATUS_UNKNOWN);

        // then the cached mappings are evicted so the alias is looked up from the database again
        assertThat(nativeCache.asMap()).doesNotContainValue(Optional.of(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();
        verify(entityRepository, times(2)).findByAlias(ALIAS_ECDSA_SECP256K1);
    }

    @Test
    void deletedMappingsKeptOnRollback() {
        // given
        persisted(DELETED);
        entityIdService.notify(deleted(DELETED));
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).isEmpty();

        // when
        complete(STATUS_ROLLED_BACK);

        // then the cached mappings are still valid, so they are kept instead of loaded from the database again
        assertThat(entityIdService.lookup(ALIAS_ACCOUNT_ID)).hasValue(DELETED);
        assertThat(entityIdService.lookup(EVM_ADDRESS_ACCOUNT_ID)).hasValue(DELETED);
        verify(entityRepository).findByAlias(ALIAS_ECDSA_SECP256K1);
        verify(entityRepository).findByEvmAddress(EVM_ADDRESS);
    }

    private void complete(final int status) {
        TransactionSynchronizationUtils.triggerAfterCompletion(status);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Entity created(final EntityId entityId) {
        final var entity = entity(entityId, false);
        entity.setAlias(ALIAS_ECDSA_SECP256K1);
        entity.setEvmAddress(EVM_ADDRESS);
        return entity;
    }

    private Entity deleted(final EntityId entityId) {
        return entity(entityId, true);
    }

    private Entity entity(final EntityId entityId, final boolean deleted) {
        final var entity = entityId.toEntity();
        entity.setDeleted(deleted);
        entity.setType(EntityType.ACCOUNT);
        return entity;
    }

    private void persisted(final EntityId entityId) {
        final var id = Optional.ofNullable(entityId).map(EntityId::getId);
        when(entityRepository.findByAlias(ALIAS_ECDSA_SECP256K1)).thenReturn(id);
        when(entityRepository.findByEvmAddress(EVM_ADDRESS)).thenReturn(id);
    }
}
