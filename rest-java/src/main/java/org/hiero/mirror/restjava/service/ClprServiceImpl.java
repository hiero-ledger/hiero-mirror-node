// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.hiero.mirror.restjava.jooq.domain.tables.ClprChannel.CLPR_CHANNEL;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprConnectorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private final ClprChannelRepository channelRepository;
    private final ClprConnectorRepository connectorRepository;
    private final QueryProperties queryProperties;

    @Override
    public List<ClprChannel> getChannels(final ClprChannelsRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        final var channelId = request.getChannelId();
        validateRange(lowerBound, upperBound);
        validateWindow(lowerBound, upperBound, channelId != null);

        final var sort = Sort.by(request.getOrder(), CLPR_CHANNEL.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var status = request.getStatus();

        if (channelId != null) {
            return status == null
                    ? channelRepository.findByChannelIdAndCreatedTimestampBetween(
                            channelId, lowerBound, upperBound, page)
                    : channelRepository.findByChannelIdAndCreatedTimestampBetweenAndStatus(
                            channelId, lowerBound, upperBound, status.name(), page);
        }

        return status == null
                ? channelRepository.findByCreatedTimestampBetween(lowerBound, upperBound, page)
                : channelRepository.findByCreatedTimestampBetweenAndStatus(lowerBound, upperBound, status.name(), page);
    }

    @Override
    public List<ClprConnector> getConnectors(final ClprConnectorsRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        validateRange(lowerBound, upperBound);

        // Only channel_id routes to a single shard, so any other filter without it would scatter-gather across all
        // shards. connector_id isn't the distribution column either, so it needs channel.id too.
        final boolean timestampFiltered = lowerBound > 0L || upperBound < Long.MAX_VALUE;
        if (request.getChannelId() == null
                && (request.getConnectorId() != null || request.getDeleted() != null || timestampFiltered)) {
            throw new IllegalArgumentException(
                    "channel.id is required when filtering by connector.id, deleted or timestamp");
        }

        return connectorRepository.findAll(request);
    }

    private void validateRange(final long lowerBound, final long upperBound) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }
    }

    private void validateWindow(final long lowerBound, final long upperBound, final boolean shardLocal) {
        if (shardLocal || lowerBound <= 0L || upperBound == Long.MAX_VALUE) {
            // Open-ended ranges (a default bound) aren't capped
            return;
        }

        // Without channel.id the query scatter-gathers across shards, so cap the window
        final var maxRange = queryProperties.getMaxClprTimestampRange();
        if (upperBound - lowerBound > maxRange.toNanos()) {
            throw new IllegalArgumentException(
                    "Timestamp range by the lower and upper bounds must not exceed %s".formatted(maxRange));
        }
    }
}
