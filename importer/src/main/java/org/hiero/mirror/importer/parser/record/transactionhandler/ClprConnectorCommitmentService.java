// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_CLPR_CONNECTOR_COMMITMENT;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import com.google.protobuf.ByteString;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Resolves a {@link ClprConnector}'s {@code commitment} -- the {@code clpr_connector} primary key alongside
 * {@code channelId} -- from its {@code connectorId}, for the handlers ({@code ClprDeregisterConnector},
 * {@code ClprSubmitBundle}) that only know {@code connectorId}. Cached because the mapping is permanent: a
 * commitment is assigned once, by {@code ClprCompleteConnector}, and never changes afterward -- deregistration
 * changes status, not commitment, and a used commitment can never be re-registered -- so there's no staleness to
 * guard against. The only gap is a same-batch cold start, which {@link #notify(ClprConnector)} closes by warming
 * the cache the moment a commitment is assigned, ahead of its own DB write being flushed.
 */
@Named
class ClprConnectorCommitmentService {

    private final Cache cache;
    private final ClprConnectorRepository clprConnectorRepository;

    ClprConnectorCommitmentService(
            @Qualifier(CACHE_CLPR_CONNECTOR_COMMITMENT) CacheManager cacheManager,
            ClprConnectorRepository clprConnectorRepository) {
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.clprConnectorRepository = clprConnectorRepository;
    }

    public @Nullable byte[] lookup(byte[] channelId, byte[] connectorId) {
        final var key = toKey(channelId, connectorId);
        final Optional<byte[]> commitment = cache.get(
                key,
                () -> clprConnectorRepository
                        .findByChannelIdAndConnectorId(channelId, connectorId)
                        .map(ClprConnector::getCommitment));
        return Objects.requireNonNullElse(commitment, Optional.<byte[]>empty()).orElse(null);
    }

    public void notify(ClprConnector connector) {
        final var connectorId = connector.getConnectorId();
        if (connectorId == null) {
            return;
        }
        cache.put(toKey(connector.getChannelId(), connectorId), Optional.of(connector.getCommitment()));
    }

    private CommitmentKey toKey(byte[] channelId, byte[] connectorId) {
        return new CommitmentKey(DomainUtils.fromBytes(channelId), DomainUtils.fromBytes(connectorId));
    }

    private record CommitmentKey(ByteString channelId, ByteString connectorId) {}
}
