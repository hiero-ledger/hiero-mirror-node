// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.rest.model.ClprMessage;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprMessageMapper;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprMessageRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";
    private static final String CREATED_TIMESTAMP = "createdTimestamp";

    private final ClprChannelMapper channelMapper;
    private final ClprChannelRepository channelRepository;
    private final ClprMessageMapper messageMapper;
    private final ClprMessageRepository messageRepository;
    private final RestJavaProperties properties;

    @Override
    public List<ClprChannel> getChannels(ClprChannelsRequest request) {
        final var status = request.getStatus();
        if (status == ClprChannelStatus.PENDING) {
            return channelMapper.map(findPendingChannels(request));
        }

        final var data =
                status != null || request.getChannelId() != null ? findChannels(request) : findAllChannels(request);
        return channelMapper.map(data);
    }

    @Override
    public List<ClprMessage> getMessages(ClprMessagesRequest request) {
        final var timestamp = request.getTimestamp();
        if (timestamp.isEmpty()) {
            throw new IllegalArgumentException("timestamp is required");
        }

        final var bounds = getBounds(timestamp, properties.getMaxClprMessagesTimestampRange());
        final var channelId = request.getChannelId();
        final var connectorId = request.getConnectorId();
        final var type = request.getType();
        final var typeName = type != null ? type.name() : null;
        final var page = PageRequest.of(0, request.getLimit(), Sort.by(request.getOrder(), CONSENSUS_TIMESTAMP));

        final var messages = connectorId != null
                ? messageRepository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        channelId, connectorId, bounds.lower(), bounds.upper(), typeName, page)
                : messageRepository.findByChannelIdAndTimestampBetween(
                        channelId, bounds.lower(), bounds.upper(), typeName, page);
        return messageMapper.map(messages);
    }

    /**
     * Without a status or channel id, returns channels together with open pending commitments, ordered by creation
     * timestamp, from a single union query.
     */
    private List<ClprChannelDto> findAllChannels(ClprChannelsRequest request) {
        final var bounds = getBounds(request.getTimestamp(), properties.getMaxClprChannelsTimestampRange());
        return channelRepository.findAllChannelsByCreatedTimestampBetween(
                bounds.lower(), bounds.upper(), getPage(request));
    }

    private List<ClprChannelDto> findChannels(ClprChannelsRequest request) {
        final var channelId = request.getChannelId();
        final var status = request.getStatus();
        final var timestamp = request.getTimestamp();

        if (channelId != null && timestamp.isEmpty()) {
            final var channel = channelRepository.findByChannelId(channelId);
            if (channel == null || status != null && !status.name().equals(channel.status())) {
                return List.of();
            }

            return List.of(channel);
        }

        final var bounds =
                getBounds(timestamp, channelId == null ? properties.getMaxClprChannelsTimestampRange() : null);
        final var statusName = status != null ? status.name() : null;

        if (channelId != null) {
            return channelRepository.findByChannelIdAndTimestampBetween(
                    channelId, bounds.lower(), bounds.upper(), statusName);
        }

        return channelRepository.findByCreatedTimestampBetween(
                bounds.lower(), bounds.upper(), statusName, getPage(request));
    }

    private List<ClprChannelDto> findPendingChannels(ClprChannelsRequest request) {
        if (request.getChannelId() != null) {
            throw new IllegalArgumentException("channel.id can't be combined with status PENDING");
        }

        final var bounds = getBounds(request.getTimestamp(), properties.getMaxClprChannelsTimestampRange());
        return channelRepository.findPendingByCreatedTimestampBetween(bounds.lower(), bounds.upper(), getPage(request));
    }

    private static PageRequest getPage(ClprChannelsRequest request) {
        return PageRequest.of(0, request.getLimit(), Sort.by(request.getOrder(), CREATED_TIMESTAMP));
    }

    /**
     * Validates the timestamp and returns its inclusive bounds. With a {@code maxRange}, a bounded range can't exceed
     * it. Channels without a channel id have one since the query can't be served by a single shard, and messages have
     * one to bound the time partitions scanned.
     */
    private TimestampBounds getBounds(Bound timestamp, @Nullable Duration maxRange) {
        timestamp.verifyUnsupported(RangeOperator.NE);
        timestamp.verifySingleOccurrence();

        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }

        final var bounds = new TimestampBounds(lowerBound, upperBound);
        if (maxRange == null || !timestamp.hasLowerAndUpper()) {
            return bounds;
        }

        if (upperBound - lowerBound > maxRange.toNanos()) {
            throw new IllegalArgumentException("Timestamp range must be within %s".formatted(maxRange));
        }

        return bounds;
    }

    private record TimestampBounds(long lower, long upper) {}
}
