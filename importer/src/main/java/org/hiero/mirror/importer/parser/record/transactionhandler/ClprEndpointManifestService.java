// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_ENDPOINT_MANIFEST;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.importer.parser.record.ParseFailureListener;
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * Caches the last known full {@link ClprEndpointManifest}, the merge baseline for a partial
 * {@code SingletonUpdateChange} -- per that type's own documentation, a partial singleton update's baseline is the
 * last emitted value, including across block boundaries, so it can't be reconstructed from a single block's state
 * changes alone. Callers that write a newly merged manifest should call {@link #notify(ClprEndpointManifest)} to
 * warm the cache ahead of their own DB write landing in the same batch. That write isn't durable until the
 * enclosing parse transaction commits, so this implements {@link ParseFailureListener} to undo it if that batch
 * instead fails -- otherwise a retry's baseline lookup could see a value that was never actually written to the
 * database.
 */
@NullMarked
@Named
@CacheConfig(cacheNames = CACHE_NAME, cacheManager = CACHE_CLPR_ENDPOINT_MANIFEST)
class ClprEndpointManifestService implements ParseFailureListener {

    private static final Object CACHE_KEY = "latest";

    private final AtomicLong notifiedAtConsensusTimestamp = new AtomicLong(Long.MIN_VALUE);

    // Only needed for the direct evict in handleParseFailure below -- lookup/notify go through @Cacheable/@CachePut.
    private final Cache cache;

    private final ClprEndpointManifestRepository clprEndpointManifestRepository;

    ClprEndpointManifestService(
            @Qualifier(CACHE_CLPR_ENDPOINT_MANIFEST) CacheManager cacheManager,
            ClprEndpointManifestRepository clprEndpointManifestRepository) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME));
        this.clprEndpointManifestRepository = clprEndpointManifestRepository;
    }

    /**
     * Evicts the cached baseline if it was staged by a {@link #notify(ClprEndpointManifest)} at or after
     * {@code recordFileStartTimestamp} -- i.e. by the batch that just failed to parse -- since that value was never
     * durably written and would otherwise wrongly baseline a retry's partial-update merge.
     */
    @Override
    public void handleParseFailure(long recordFileStartTimestamp) {
        if (notifiedAtConsensusTimestamp.get() >= recordFileStartTimestamp) {
            cache.evict(CACHE_KEY);
            notifiedAtConsensusTimestamp.set(Long.MIN_VALUE);
        }
    }

    @Cacheable(key = "'latest'", sync = true)
    public @Nullable ClprEndpointManifest lookup() {
        return clprEndpointManifestRepository.findLatest();
    }

    @CachePut(key = "'latest'")
    public ClprEndpointManifest notify(ClprEndpointManifest clprEndpointManifest) {
        notifiedAtConsensusTimestamp.set(clprEndpointManifest.getConsensusTimestamp());
        return clprEndpointManifest;
    }
}
