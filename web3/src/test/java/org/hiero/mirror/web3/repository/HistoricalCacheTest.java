// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.OPTIONAL;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_HISTORICAL;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ACCOUNT_BALANCE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ENTITY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ACCOUNT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_BALANCE;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;

/**
 * Verifies that lookups pinned to a historical block timestamp are served from the historical cache: a repeated lookup
 * at the same block does not query the database, a lookup at another block does, and concurrent lookups of the same key
 * share one query.
 */
@RequiredArgsConstructor
final class HistoricalCacheTest extends Web3IntegrationTest {

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;

    @Qualifier(CACHE_MANAGER_HISTORICAL)
    private final CacheManager historicalCacheManager;

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenRepository tokenRepository;

    @Test
    void accountBalance() {
        final long accountId = domainBuilder.entity().persist().getId();
        final long treasury = systemEntity.treasuryAccount().getId();
        final long timestamp = domainBuilder.timestamp();

        assertCached(
                CACHE_NAME_ACCOUNT_BALANCE,
                () -> accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestamp, treasury),
                () -> accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestamp + 1, treasury));
    }

    @Test
    void concurrentLookupsShareOneQuery() throws Exception {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();
        final int callers = 32;
        final var start = new CountDownLatch(1);
        final var results = new ArrayList<Future<?>>(callers);
        final long before = loads(CACHE_NAME_ENTITY);

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return entityRepository.findActiveByIdAndTimestamp(entity.getId(), timestamp);
                }));
            }
            start.countDown();
            for (final var result : results) {
                assertThat(result.get()).asInstanceOf(OPTIONAL).contains(entity);
            }
        }

        assertThat(loads(CACHE_NAME_ENTITY) - before).isOne();
    }

    @Test
    void contractState() {
        final var change = domainBuilder.contractStateChange().persist();
        final long contractId = change.getContractId();
        final long timestamp = change.getConsensusTimestamp();

        // A copy of the slot proves byte array arguments are keyed by content, not identity
        assertCached(
                CACHE_NAME_CONTRACT_STATE,
                () -> contractStateRepository.findStorageByBlockTimestamp(
                        contractId, Arrays.copyOf(change.getSlot(), change.getSlot().length), timestamp),
                () -> contractStateRepository.findStorageByBlockTimestamp(contractId, change.getSlot(), timestamp + 1));
    }

    @Test
    void emptyResultIsCached() {
        final long id = domainBuilder.id();
        final long timestamp = domainBuilder.timestamp();
        final long before = loads(CACHE_NAME_ENTITY);

        assertThat(entityRepository.findActiveByIdAndTimestamp(id, timestamp)).isEmpty();
        domainBuilder.entity(id, timestamp).persist();

        // The entity did not exist at that block when first queried; the cached absence is still served
        assertThat(entityRepository.findActiveByIdAndTimestamp(id, timestamp)).isEmpty();
        assertThat(loads(CACHE_NAME_ENTITY) - before).isOne();
    }

    @Test
    void entityByEvmAddress() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                CACHE_NAME_EVM_ADDRESS,
                () -> entityRepository.findActiveByEvmAddressAndTimestamp(entity.getEvmAddress(), timestamp),
                () -> entityRepository.findActiveByEvmAddressAndTimestamp(entity.getEvmAddress(), timestamp + 1));
    }

    @Test
    void entityByEvmAddressOrAlias() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                CACHE_NAME_ALIAS,
                () -> entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(entity.getAlias(), timestamp),
                () -> entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(entity.getAlias(), timestamp + 1));
    }

    @Test
    void entityById() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                CACHE_NAME_ENTITY,
                () -> entityRepository.findActiveByIdAndTimestamp(entity.getId(), timestamp),
                () -> entityRepository.findActiveByIdAndTimestamp(entity.getId(), timestamp + 1));
    }

    @Test
    void token() {
        final var token = domainBuilder.token().persist();
        final long timestamp = token.getTimestampLower();

        assertCached(
                CACHE_NAME_TOKEN,
                () -> tokenRepository.findByTokenIdAndTimestamp(token.getTokenId(), timestamp),
                () -> tokenRepository.findByTokenIdAndTimestamp(token.getTokenId(), timestamp + 1));
    }

    @Test
    void tokenAccount() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();
        final long accountId = tokenAccount.getAccountId();
        final long tokenId = tokenAccount.getTokenId();
        final long timestamp = tokenAccount.getTimestampLower();

        assertCached(
                CACHE_NAME_TOKEN_ACCOUNT,
                () -> tokenAccountRepository.findByIdAndTimestamp(accountId, tokenId, timestamp),
                () -> tokenAccountRepository.findByIdAndTimestamp(accountId, tokenId, timestamp + 1));
    }

    @Test
    void tokenBalance() {
        final long accountId = domainBuilder.id();
        final long tokenId = domainBuilder.id();
        final long treasury = systemEntity.treasuryAccount().getId();
        final long timestamp = domainBuilder.timestamp();

        assertCached(
                CACHE_NAME_TOKEN_BALANCE,
                () -> tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId, accountId, timestamp, treasury),
                () -> tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId, accountId, timestamp + 1, treasury));
    }

    /**
     * Calls {@code sameBlock} twice and {@code otherBlock} once, expecting one load for the repeated block and a
     * second load for the other block.
     */
    private <T> void assertCached(final String cacheName, final Supplier<T> sameBlock, final Supplier<T> otherBlock) {
        final long before = loads(cacheName);
        final var first = sameBlock.get();
        assertThat(first).asInstanceOf(OPTIONAL).isPresent();
        assertThat(sameBlock.get()).isEqualTo(first);
        assertThat(loads(cacheName) - before).isOne();

        otherBlock.get();
        assertThat(loads(cacheName) - before).isEqualTo(2L);
    }

    /** Cumulative load count; clearing a cache between tests does not reset its statistics. */
    @SuppressWarnings("unchecked")
    private long loads(final String cacheName) {
        final var cache = Objects.requireNonNull(historicalCacheManager.getCache(cacheName));
        return ((Cache<Object, Object>) cache.getNativeCache()).stats().loadCount();
    }
}
