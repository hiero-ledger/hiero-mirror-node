// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ACCOUNT_BALANCE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ENTITY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN;
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
import org.junit.jupiter.params.provider.ValueSource;
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
    @ValueSource(
            strings = {
                CACHE_NAME_ACCOUNT_BALANCE,
                CACHE_NAME_ALIAS,
                CACHE_NAME_CONTRACT_STATE,
                CACHE_NAME_ENTITY,
                CACHE_NAME_EVM_ADDRESS,
                CACHE_NAME_TOKEN,
                CACHE_NAME_TOKEN_ACCOUNT,
                CACHE_NAME_TOKEN_BALANCE
            })
    void historicalCacheLoadsDistinctKeysConcurrently(final String cacheName) throws InterruptedException {
        final var cacheManager =
                new EvmConfiguration(new CacheProperties(), new EvmProperties()).cacheManagerHistorical();
        final var cache = Objects.requireNonNull(cacheManager.getCache(cacheName));

        assertThat(loadConcurrently(cache)).isLessThan(LOAD_TIME.multipliedBy(3));
    }

    @ParameterizedTest
    @CsvSource({
        CACHE_NAME_ACCOUNT_BALANCE + ", 5000",
        CACHE_NAME_ALIAS + ", 5000",
        CACHE_NAME_CONTRACT_STATE + ", 50000",
        CACHE_NAME_ENTITY + ", 5000",
        CACHE_NAME_EVM_ADDRESS + ", 5000",
        CACHE_NAME_TOKEN + ", 1000",
        CACHE_NAME_TOKEN_ACCOUNT + ", 5000",
        CACHE_NAME_TOKEN_BALANCE + ", 5000"
    })
    void historicalCacheMaximumSize(final String cacheName, final long maximumSize) {
        final var cacheManager =
                new EvmConfiguration(new CacheProperties(), new EvmProperties()).cacheManagerHistorical();
        final var cache = (CaffeineCache) Objects.requireNonNull(cacheManager.getCache(cacheName));

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

        assertThat(loadConcurrently(cache(cacheManager))).isGreaterThan(LOAD_TIME.multipliedBy(3));
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

    private static Cache cache(final CacheManager cacheManager) {
        return Objects.requireNonNull(cacheManager.getCache("default"));
    }
}
