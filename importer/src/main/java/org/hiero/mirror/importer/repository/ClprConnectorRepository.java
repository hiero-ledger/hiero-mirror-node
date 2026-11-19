// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_CONNECTOR_COMMITMENT;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

/**
 * {@code commitment} -- the {@code clpr_connector} primary key alongside {@code channelId} -- is resolved here from
 * {@code connectorId} for handlers that only know {@code connectorId}. Cached since the mapping is permanent once
 * assigned (HIP-1535: a used commitment can never be re-registered). Callers that assign a new commitment should
 * call {@link #notify(ClprConnector)} to warm the cache ahead of their own DB write landing in the same batch.
 */
@CacheConfig(cacheNames = CACHE_NAME, cacheManager = CACHE_CLPR_CONNECTOR_COMMITMENT)
public interface ClprConnectorRepository extends CrudRepository<ClprConnector, AbstractClprConnector.Id> {

    // Explicit query since channel_id is part of the embedded id, not a top-level property
    @Query("select * from clpr_connector where channel_id = :channelId and connector_id = :connectorId")
    Optional<ClprConnector> findByChannelIdAndConnectorId(byte[] channelId, byte[] connectorId);

    // Spring Data JDBC treats a byte[] return type as a collection of bytes, so read the commitment from the entity
    @Cacheable(key = "@spelHelper.getCacheKey(#channelId, #connectorId)", sync = true)
    default byte @Nullable [] findCommitmentByChannelIdAndConnectorId(byte[] channelId, byte[] connectorId) {
        return findByChannelIdAndConnectorId(channelId, connectorId)
                .map(ClprConnector::getCommitment)
                .orElse(null);
    }

    @CachePut(
            key = "@spelHelper.getCacheKey(#connector.channelId, #connector.connectorId)",
            condition = "#connector.connectorId != null")
    default byte[] notify(ClprConnector connector) {
        return connector.getCommitment();
    }
}
