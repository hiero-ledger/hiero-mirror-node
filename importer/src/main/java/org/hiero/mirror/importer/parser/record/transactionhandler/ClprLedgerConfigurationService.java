// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_LEDGER_CONFIGURATION;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Objects;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Caches the last known full {@link ClprLedgerConfiguration}, the merge baseline for a partial
 * {@code SingletonUpdateChange} -- per that type's own documentation, a partial singleton update's baseline is the
 * last emitted value, including across block boundaries, so it can't be reconstructed from a single block's state
 * changes alone. Callers that write a newly merged configuration should call {@link #notify(ClprLedgerConfiguration)}
 * to warm the cache ahead of their own DB write landing in the same batch.
 */
@NullMarked
@Named
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

    public @Nullable ClprLedgerConfiguration lookup() {
        try {
            return cache.get(CACHE_KEY, clprLedgerConfigurationRepository::findLatest);
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up latest clpr_ledger_configuration", e);
            return null;
        }
    }

    public void notify(ClprLedgerConfiguration clprLedgerConfiguration) {
        cache.put(CACHE_KEY, clprLedgerConfiguration);
    }
}
