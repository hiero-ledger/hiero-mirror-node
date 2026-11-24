// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.OPTIONAL;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_HISTORICAL;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_HISTORICAL_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_HISTORICAL_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ACCOUNT_BALANCE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ENTITY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ACCOUNT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_BALANCE;

import com.github.benmanes.caffeine.cache.Cache;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;

/**
 * Verifies that lookups pinned to a historical block timestamp are served from the historical cache: a repeated lookup
 * at the same block does not query the database, a lookup at another block does, and concurrent lookups of the same key
 * share one query. Also verifies that Spring's default {@link SimpleKeyGenerator}, used by the historical caches,
 * compares {@code byte[]} arguments by contents rather than by reference, and that no historical cached repository
 * method takes an argument whose key would fall back to reference equality.
 */
@RequiredArgsConstructor
final class HistoricalCacheTest extends Web3IntegrationTest {

    private static final Set<String> HISTORICAL_CACHE_MANAGERS =
            Set.of(CACHE_MANAGER_HISTORICAL, CACHE_MANAGER_HISTORICAL_CONTRACT_STATE, CACHE_MANAGER_HISTORICAL_TOKEN);

    private static final long KEY_TIMESTAMP = 1_000L;

    private final AccountBalanceRepository accountBalanceRepository;
    private final ContractStateRepository contractStateRepository;
    private final EntityRepository entityRepository;

    @Qualifier(CACHE_MANAGER_HISTORICAL)
    private final CacheManager historicalCacheManager;

    @Qualifier(CACHE_MANAGER_HISTORICAL_CONTRACT_STATE)
    private final CacheManager historicalContractStateCacheManager;

    @Qualifier(CACHE_MANAGER_HISTORICAL_TOKEN)
    private final CacheManager historicalTokenCacheManager;

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenRepository tokenRepository;

    @Test
    void accountBalance() {
        final long accountId = domainBuilder.entity().persist().getId();
        final long treasury = systemEntity.treasuryAccount().getId();
        final long timestamp = domainBuilder.timestamp();

        assertCached(
                historicalCacheManager,
                CACHE_NAME_ACCOUNT_BALANCE,
                () -> accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestamp, treasury),
                () -> accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestamp + 1, treasury));
    }

    @Test
    void byteArrayKeysAreComparedByContents() {
        final byte[] address = {1, 2, 3};
        final var key = SimpleKeyGenerator.generateKey(address, KEY_TIMESTAMP);
        final var sameContents = SimpleKeyGenerator.generateKey(address.clone(), KEY_TIMESTAMP);
        final var otherContents = SimpleKeyGenerator.generateKey(new byte[] {1, 2, 4}, KEY_TIMESTAMP);

        assertThat(key).isEqualTo(sameContents).hasSameHashCodeAs(sameContents).isNotEqualTo(otherContents);
    }

    @Test
    void collectionOfByteArraysIsComparedByReference() {
        final byte[] address = {1, 2, 3};
        final var key = SimpleKeyGenerator.generateKey(List.of(address), KEY_TIMESTAMP);
        final var sameContents = SimpleKeyGenerator.generateKey(List.of(address.clone()), KEY_TIMESTAMP);

        assertThat(key).isNotEqualTo(sameContents);
    }

    @Test
    void concurrentLookupsShareOneQuery() throws Exception {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();
        final int callers = 32;
        final var start = new CountDownLatch(1);
        final var results = new ArrayList<Future<?>>(callers);
        final long before = loads(historicalCacheManager, CACHE_NAME_ENTITY);

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

        assertThat(loads(historicalCacheManager, CACHE_NAME_ENTITY) - before).isOne();
    }

    @Test
    void contractState() {
        final var change = domainBuilder.contractStateChange().persist();
        final long contractId = change.getContractId();
        final long timestamp = change.getConsensusTimestamp();

        // A copy of the slot proves byte array arguments are keyed by content, not identity
        assertCached(
                historicalContractStateCacheManager,
                CACHE_NAME,
                () -> contractStateRepository.findStorageByBlockTimestamp(
                        contractId, Arrays.copyOf(change.getSlot(), change.getSlot().length), timestamp),
                () -> contractStateRepository.findStorageByBlockTimestamp(contractId, change.getSlot(), timestamp + 1));
    }

    @Test
    void emptyResultIsCached() {
        final long id = domainBuilder.id();
        final long timestamp = domainBuilder.timestamp();
        final long before = loads(historicalCacheManager, CACHE_NAME_ENTITY);

        assertThat(entityRepository.findActiveByIdAndTimestamp(id, timestamp)).isEmpty();
        domainBuilder.entity(id, timestamp).persist();

        // The entity did not exist at that block when first queried; the cached absence is still served
        assertThat(entityRepository.findActiveByIdAndTimestamp(id, timestamp)).isEmpty();
        assertThat(loads(historicalCacheManager, CACHE_NAME_ENTITY) - before).isOne();
    }

    @Test
    void entityByEvmAddress() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                historicalCacheManager,
                CACHE_NAME_EVM_ADDRESS,
                () -> entityRepository.findActiveByEvmAddressAndTimestamp(
                        Arrays.copyOf(entity.getEvmAddress(), entity.getEvmAddress().length), timestamp),
                () -> entityRepository.findActiveByEvmAddressAndTimestamp(entity.getEvmAddress(), timestamp + 1));
    }

    @Test
    void entityByEvmAddressOrAlias() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                historicalCacheManager,
                CACHE_NAME_ALIAS,
                () -> entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        Arrays.copyOf(entity.getAlias(), entity.getAlias().length), timestamp),
                () -> entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(entity.getAlias(), timestamp + 1));
    }

    @Test
    void entityById() {
        final var entity = domainBuilder.entity().persist();
        final long timestamp = entity.getTimestampLower();

        assertCached(
                historicalCacheManager,
                CACHE_NAME_ENTITY,
                () -> entityRepository.findActiveByIdAndTimestamp(entity.getId(), timestamp),
                () -> entityRepository.findActiveByIdAndTimestamp(entity.getId(), timestamp + 1));
    }

    @Test
    void historicalCacheableMethodsHaveNoCollectionParameters() throws ClassNotFoundException {
        final var methods = historicalCacheableMethods();
        final var byteArrayMethods = methods.stream()
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(byte[].class))
                .map(Method::getName)
                .toList();
        final var collectionMethods = methods.stream()
                .filter(m -> Arrays.stream(m.getParameterTypes())
                        .anyMatch(t -> Iterable.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t)))
                .map(m -> m.getDeclaringClass().getSimpleName() + "." + m.getName())
                .toList();

        assertThat(byteArrayMethods)
                .contains(
                        "findActiveByEvmAddressAndTimestamp",
                        "findActiveByEvmAddressOrAliasAndTimestamp",
                        "findStorageByBlockTimestamp");
        assertThat(collectionMethods).isEmpty();
    }

    @Test
    void multipleParametersAreWrappedInSimpleKey() {
        assertThat(SimpleKeyGenerator.generateKey(new byte[] {1, 2, 3}, KEY_TIMESTAMP))
                .isInstanceOf(SimpleKey.class);
    }

    @Test
    void singleByteArrayParameterIsWrappedInSimpleKey() {
        final byte[] address = {1, 2, 3};
        final var key = SimpleKeyGenerator.generateKey((Object) address);

        assertThat(key).isInstanceOf(SimpleKey.class).isEqualTo(SimpleKeyGenerator.generateKey((Object)
                address.clone()));
    }

    @Test
    void singleNonArrayParameterIsUsedAsKey() {
        assertThat(SimpleKeyGenerator.generateKey(KEY_TIMESTAMP)).isEqualTo(KEY_TIMESTAMP);
    }

    @Test
    void token() {
        final var token = domainBuilder.token().persist();
        final long timestamp = token.getTimestampLower();

        assertCached(
                historicalTokenCacheManager,
                CACHE_NAME,
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
                historicalCacheManager,
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
                historicalCacheManager,
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
    private <T> void assertCached(
            final CacheManager cacheManager,
            final String cacheName,
            final Supplier<T> sameBlock,
            final Supplier<T> otherBlock) {
        final long before = loads(cacheManager, cacheName);
        final var first = sameBlock.get();
        assertThat(first).asInstanceOf(OPTIONAL).isPresent();
        assertThat(sameBlock.get()).isEqualTo(first);
        assertThat(loads(cacheManager, cacheName) - before).isOne();

        otherBlock.get();
        assertThat(loads(cacheManager, cacheName) - before).isEqualTo(2L);
    }

    /** Repository methods cached by a historical cache manager with the default key generator. */
    private Collection<Method> historicalCacheableMethods() throws ClassNotFoundException {
        final var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(final AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        final var methods = new ArrayList<Method>();
        for (final var candidate : scanner.findCandidateComponents(getClass().getPackageName())) {
            final var repository =
                    ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader());
            for (final var method : repository.getDeclaredMethods()) {
                final var cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
                if (cacheable != null
                        && HISTORICAL_CACHE_MANAGERS.contains(cacheable.cacheManager())
                        && cacheable.key().isEmpty()
                        && cacheable.keyGenerator().isEmpty()) {
                    methods.add(method);
                }
            }
        }

        assertThat(methods).isNotEmpty();
        return methods;
    }

    /** Cumulative load count; clearing a cache between tests does not reset its statistics. */
    @SuppressWarnings("unchecked")
    private long loads(final CacheManager cacheManager, final String cacheName) {
        final var cache = Objects.requireNonNull(cacheManager.getCache(cacheName));
        return ((Cache<Object, Object>) cache.getNativeCache()).stats().loadCount();
    }
}
