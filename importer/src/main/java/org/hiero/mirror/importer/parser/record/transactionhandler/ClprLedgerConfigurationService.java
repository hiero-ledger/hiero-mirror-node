// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_LEDGER_CONFIGURATION;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

import jakarta.inject.Named;
import java.util.Objects;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Caches the last known full {@link ClprLedgerConfiguration}, the merge baseline for a partial
 * {@code SingletonUpdateChange} -- per that type's own documentation, a partial singleton update's baseline is the
 * last emitted value, including across block boundaries, so it can't be reconstructed from a single block's state
 * changes alone. Callers that write a newly merged configuration should call {@link #notify(ClprLedgerConfiguration)}
 * to warm the cache ahead of their own DB write landing in the same batch. That write isn't durable until the
 * enclosing parse transaction commits, so {@link #notify(ClprLedgerConfiguration)} registers a transaction
 * synchronization to undo the cache write if that transaction doesn't commit -- otherwise a retry's baseline lookup
 * could see a value that was never actually written to the database.
 */
@NullMarked
@Named
@CacheConfig(cacheNames = CACHE_NAME, cacheManager = CACHE_CLPR_LEDGER_CONFIGURATION)
class ClprLedgerConfigurationService {

    private static final Object CACHE_KEY = "latest";

    private final Cache cache;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;

    ClprLedgerConfigurationService(
            @Qualifier(CACHE_CLPR_LEDGER_CONFIGURATION) CacheManager cacheManager,
            ClprLedgerConfigurationRepository clprLedgerConfigurationRepository) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME));
        this.clprLedgerConfigurationRepository = clprLedgerConfigurationRepository;
    }

    @Cacheable(key = "'latest'", sync = true)
    public @Nullable ClprLedgerConfiguration lookup() {
        return clprLedgerConfigurationRepository.findLatest();
    }

    @CachePut(key = "'latest'")
    public ClprLedgerConfiguration notify(ClprLedgerConfiguration clprLedgerConfiguration) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cache.evict(CACHE_KEY);
                    }
                }
            });
        }

        return clprLedgerConfiguration;
    }
}
