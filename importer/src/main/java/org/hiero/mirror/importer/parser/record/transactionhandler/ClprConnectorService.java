// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_CONNECTOR_COMMITMENT;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Objects;
import lombok.Value;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Resolves a {@link ClprConnector}'s {@code commitment} -- the {@code clpr_connector} primary key alongside
 * {@code channelId} -- from its {@code connectorId}, for handlers that only know {@code connectorId}. Cached
 * since the mapping is permanent once assigned (HIP-1535: a used commitment can never be re-registered). Callers
 * that assign a new commitment should call {@link #notify(ClprConnector)} to warm the cache ahead of their own DB
 * write landing in the same batch.
 */
@NullMarked
@Named
class ClprConnectorService {

    private final Cache cache;
    private final ClprConnectorRepository clprConnectorRepository;

    ClprConnectorService(
            @Qualifier(CACHE_CLPR_CONNECTOR_COMMITMENT) CacheManager cacheManager,
            ClprConnectorRepository clprConnectorRepository) {
        this.cache = Objects.requireNonNull(cacheManager.getCache(CACHE_NAME));
        this.clprConnectorRepository = clprConnectorRepository;
    }

    public byte @Nullable [] lookup(byte[] channelId, byte[] connectorId) {
        final var key = toKey(channelId, connectorId);
        try {
            return cache.get(
                    key,
                    () -> clprConnectorRepository
                            .findCommitmentByChannelIdAndConnectorId(channelId, connectorId)
                            .orElse(null));
        } catch (Cache.ValueRetrievalException e) {
            Utility.handleRecoverableError("Error looking up clpr_connector commitment", e);
            return null;
        }
    }

    public void notify(ClprConnector connector) {
        final var connectorId = connector.getConnectorId();
        if (connectorId == null) {
            return;
        }
        cache.put(toKey(connector.getChannelId(), connectorId), connector.getCommitment());
    }

    private ConnectorKey toKey(byte[] channelId, byte[] connectorId) {
        return new ConnectorKey(channelId, connectorId);
    }

    // Not a record: Lombok's generated equals/hashCode compares array fields by content (Arrays.equals), unlike a
    // record's, which would compare these byte[] fields by reference.
    @Value
    private static class ConnectorKey {
        byte[] channelId;
        byte[] connectorId;
    }
}
