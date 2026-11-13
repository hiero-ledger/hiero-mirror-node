// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_ENDPOINT_MANIFEST;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Objects;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Caches the last known full {@link ClprEndpointManifest}, the merge baseline for a partial
 * {@code SingletonUpdateChange} -- per that type's own documentation, a partial singleton update's baseline is the
 * last emitted value, including across block boundaries, so it can't be reconstructed from a single block's state
 * changes alone. Callers that write a newly merged manifest should call {@link #notify(ClprEndpointManifest)} to
 * warm the cache ahead of their own DB write landing in the same batch.
 */
@NullMarked
@Named
class ClprEndpointManifestService {

    private static final Object CACHE_KEY = "latest";

    private final Cache cache;
    private final ClprEndpointManifestRepository clprEndpointManifestRepository;

    ClprEndpointManifestService(
            @Qualifier(CACHE_CLPR_ENDPOINT_MANIFEST) CacheManager cacheManager,
            ClprEndpointManifestRepository clprEndpointManifestRepository) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME));
        this.clprEndpointManifestRepository = clprEndpointManifestRepository;
    }

    public @Nullable ClprEndpointManifest lookup() {
        try {
            return cache.get(
                    CACHE_KEY, () -> clprEndpointManifestRepository.findLatest().orElse(null));
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up latest clpr_endpoint_manifest", e);
            return null;
        }
    }

    public void notify(ClprEndpointManifest clprEndpointManifest) {
        cache.put(CACHE_KEY, clprEndpointManifest);
    }
}
