// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.hiero.mirror.restjava.jooq.domain.tables.ClprChannel.CLPR_CHANNEL;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprChannelPendingCommitment.CLPR_CHANNEL_PENDING_COMMITMENT;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprConnector.CLPR_CONNECTOR;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprConnectorPendingCommitment.CLPR_CONNECTOR_PENDING_COMMITMENT;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprMessage.CLPR_MESSAGE;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.dto.ClprPendingRequest;
import org.hiero.mirror.restjava.repository.ClprChannelPendingCommitmentRepository;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprConnectorPendingCommitmentRepository;
import org.hiero.mirror.restjava.repository.ClprConnectorRepository;
import org.hiero.mirror.restjava.repository.ClprMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private final ClprChannelPendingCommitmentRepository channelPendingCommitmentRepository;
    private final ClprChannelRepository channelRepository;
    private final ClprConnectorPendingCommitmentRepository connectorPendingCommitmentRepository;
    private final ClprConnectorRepository connectorRepository;
    private final ClprMessageRepository messageRepository;
    private final QueryProperties queryProperties;

    @Override
    public List<ClprChannel> getChannels(final ClprChannelsRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        final var channelId = request.getChannelId();
        validateRange(lowerBound, upperBound);
        final var status = request.getStatus();

        if (channelId != null && (lowerBound > 0L || upperBound < Long.MAX_VALUE)) {
            // With channel.id, timestamp means "as of" and only eq is supported
            if (lowerBound != upperBound) {
                throw new IllegalArgumentException("timestamp must use the eq operator when filtering by channel.id");
            }

            return channelRepository.findByChannelIdAndTimestamp(
                    channelId, lowerBound, status == null ? null : status.name());
        }

        if (channelId == null) {
            validateWindow(lowerBound, upperBound);
        }

        final var sort = Sort.by(request.getOrder(), CLPR_CHANNEL.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);

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

        final var channelId = request.getChannelId();
        final var connectorId = request.getConnectorId();
        if (channelId != null && connectorId != null && timestampFiltered) {
            // With both ids, timestamp means "as of" and only eq is supported
            if (lowerBound != upperBound) {
                throw new IllegalArgumentException(
                        "timestamp must use the eq operator when filtering by channel.id and connector.id");
            }

            return connectorRepository.findByChannelIdAndConnectorIdAndTimestamp(
                    channelId, connectorId, lowerBound, request.getDeleted());
        }

        validateWindow(lowerBound, upperBound);
        final var sort = Sort.by(request.getOrder(), CLPR_CONNECTOR.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        if (channelId == null) {
            return connectorRepository.findByCreatedTimestampBetween(lowerBound, upperBound, page);
        }

        return connectorRepository.findByChannelId(
                channelId, lowerBound, upperBound, connectorId, request.getDeleted(), page);
    }

    @Override
    public List<ClprMessageDto> getMessages(final ClprMessagesRequest request) {
        final long timestampLowerBound = request.getTimestampLowerBound();
        final long timestampUpperBound = request.getTimestampUpperBound();
        final long messageIdLowerBound = request.getMessageIdLowerBound();
        final long messageIdUpperBound = request.getMessageIdUpperBound();
        validateRange(timestampLowerBound, timestampUpperBound);
        if (messageIdLowerBound > messageIdUpperBound) {
            throw new IllegalArgumentException("Invalid range provided for message.id");
        }

        // Without a timestamp bound the query can't be pruned to the right clpr_message partition(s). connector.id is
        // served by its own selective index, so it doesn't need one.
        final boolean timestampFiltered = timestampLowerBound > 0L || timestampUpperBound < Long.MAX_VALUE;
        if (!timestampFiltered) {
            if (messageIdLowerBound > 0L || messageIdUpperBound < Long.MAX_VALUE) {
                throw new IllegalArgumentException("timestamp is required when filtering by message.id");
            }

            if (request.getType() != null) {
                throw new IllegalArgumentException("timestamp is required when filtering by type");
            }
        }

        validateWindow(timestampLowerBound, timestampUpperBound);

        final var channelId = request.getChannelId();
        final var sort = Sort.by(
                request.getOrder(), CLPR_MESSAGE.CONSENSUS_TIMESTAMP.getName(), CLPR_MESSAGE.MESSAGE_ID.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var type = request.getType() == null ? null : request.getType().name();
        final var connectorId = request.getConnectorId();
        return connectorId == null
                ? messageRepository.findByChannelId(
                        channelId,
                        timestampLowerBound,
                        timestampUpperBound,
                        messageIdLowerBound,
                        messageIdUpperBound,
                        type,
                        page)
                : messageRepository.findByChannelIdAndConnectorId(
                        channelId,
                        connectorId,
                        timestampLowerBound,
                        timestampUpperBound,
                        messageIdLowerBound,
                        messageIdUpperBound,
                        type,
                        page);
    }

    @Override
    public List<ClprChannelPendingCommitment> getPendingChannels(final ClprPendingRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        validateRange(lowerBound, upperBound);

        final var sort = Sort.by(request.getOrder(), CLPR_CHANNEL_PENDING_COMMITMENT.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        return channelPendingCommitmentRepository.findOpenByCreatedTimestampBetween(lowerBound, upperBound, page);
    }

    @Override
    public List<ClprConnectorPendingCommitment> getPendingConnectors(final ClprPendingRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        validateRange(lowerBound, upperBound);

        final var sort = Sort.by(request.getOrder(), CLPR_CONNECTOR_PENDING_COMMITMENT.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        return connectorPendingCommitmentRepository.findOpenByCreatedTimestampBetween(lowerBound, upperBound, page);
    }

    private void validateRange(final long lowerBound, final long upperBound) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }
    }

    private void validateWindow(final long lowerBound, final long upperBound) {
        if (lowerBound <= 0L || upperBound == Long.MAX_VALUE) {
            // Open-ended ranges (a default bound) aren't capped
            return;
        }

        final var maxRange = queryProperties.getMaxClprTimestampRange();
        if (upperBound - lowerBound > maxRange.toNanos()) {
            throw new IllegalArgumentException(
                    "Timestamp range by the lower and upper bounds must not exceed %s".formatted(maxRange));
        }
    }
}
