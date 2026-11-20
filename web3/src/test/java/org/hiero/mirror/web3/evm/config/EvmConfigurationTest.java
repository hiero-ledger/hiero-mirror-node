// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.SimpleKey;

final class EvmConfigurationTest {

    private static final int CALLERS = 100;
    private static final Duration LOAD_TIME = Duration.ofMillis(250);
    private static final long TIMESTAMP = 1_700_000_000_000_000_000L;

    /**
     * {@code @Cacheable(sync = true)} loads hold the lock of the key's hash bin for the whole query. A freshly created
     * historical cache must already have enough bins that concurrent misses on unrelated keys run in parallel instead of
     * queueing behind each other. A rare collision costs one extra load, so allow up to two load times.
     */
    @ParameterizedTest
    @CsvSource({
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ACCOUNT_BALANCE,
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ALIAS,
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ENTITY,
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_EVM_ADDRESS,
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_TOKEN_ACCOUNT,
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_TOKEN_BALANCE,
        CACHE_MANAGER_HISTORICAL_CONTRACT_STATE + ", " + CACHE_NAME,
        CACHE_MANAGER_HISTORICAL_TOKEN + ", " + CACHE_NAME
    })
    void historicalCacheLoadsDistinctKeysConcurrently(final String cacheManagerName, final String cacheName)
            throws InterruptedException {
        final var cache = historicalCache(cacheManagerName, cacheName);

        assertThat(loadConcurrently(cache)).isLessThan(LOAD_TIME.multipliedBy(3));
    }

    @ParameterizedTest
    @CsvSource({
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ACCOUNT_BALANCE + ", 5000",
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ALIAS + ", 5000",
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_ENTITY + ", 5000",
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_EVM_ADDRESS + ", 5000",
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_TOKEN_ACCOUNT + ", 5000",
        CACHE_MANAGER_HISTORICAL + ", " + CACHE_NAME_TOKEN_BALANCE + ", 5000",
        CACHE_MANAGER_HISTORICAL_CONTRACT_STATE + ", " + CACHE_NAME + ", 50000",
        CACHE_MANAGER_HISTORICAL_TOKEN + ", " + CACHE_NAME + ", 1000"
    })
    void historicalCacheMaximumSize(final String cacheManagerName, final String cacheName, final long maximumSize) {
        final var cache = (CaffeineCache) historicalCache(cacheManagerName, cacheName);

        assertThat(cache.getNativeCache().policy().eviction())
                .hasValueSatisfying(
                        eviction -> assertThat(eviction.getMaximum()).isEqualTo(maximumSize));
    }

    /**
     * Control for {@link #historicalCacheLoadsDistinctKeysConcurrently}: without an initial capacity the same burst
     * queues several loads deep, proving the test detects the problem.
     */
    @Test
    void unsizedCacheQueuesDistinctKeyLoads() throws InterruptedException {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification("expireAfterWrite=10m,maximumSize=5000");

        assertThat(loadConcurrently(cache(cacheManager, CACHE_NAME))).isGreaterThan(LOAD_TIME.multipliedBy(3));
    }

    private static Cache cache(final CacheManager cacheManager, final String cacheName) {
        return Objects.requireNonNull(cacheManager.getCache(cacheName));
    }

    /** Returns a cache from a freshly created historical cache manager with the default {@link CacheProperties}. */
    private static Cache historicalCache(final String cacheManagerName, final String cacheName) {
        final var configuration = new EvmConfiguration(new CacheProperties(), new EvmProperties());
        final var cacheManager = switch (cacheManagerName) {
            case CACHE_MANAGER_HISTORICAL -> configuration.cacheManagerHistorical();
            case CACHE_MANAGER_HISTORICAL_CONTRACT_STATE -> configuration.cacheManagerHistoricalContractState();
            case CACHE_MANAGER_HISTORICAL_TOKEN -> configuration.cacheManagerHistoricalToken();
            default -> throw new IllegalArgumentException("Unknown cache manager " + cacheManagerName);
        };
        return cache(cacheManager, cacheName);
    }

    /**
     * Loads {@link #CALLERS} distinct keys at once through {@link Cache#get(Object, java.util.concurrent.Callable)},
     * the path used by {@code @Cacheable(sync = true)}, and returns the wall time until all complete. Keys mirror the
     * {@link SimpleKey} Spring builds for an {@code (id, blockTimestamp)} lookup.
     */
    private static Duration loadConcurrently(final Cache cache) throws InterruptedException {
        final var loads = new AtomicInteger();
        final var start = new CountDownLatch(1);
        final long begin;

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CALLERS; i++) {
                final var key = new SimpleKey(1_000L + i, TIMESTAMP);
                executor.submit(() -> {
                    start.await();
                    return cache.get(key, () -> {
                        loads.incrementAndGet();
                        Thread.sleep(LOAD_TIME);
                        return key;
                    });
                });
            }
            begin = System.nanoTime();
            start.countDown();
        }

        assertThat(loads).hasValue(CALLERS);
        return Duration.ofNanos(System.nanoTime() - begin);
    }
}
