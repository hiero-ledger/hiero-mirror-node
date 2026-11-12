// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelData;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private static final String CREATED_TIMESTAMP = "created_timestamp";

    private final ClprChannelMapper channelMapper;
    private final ClprChannelRepository channelRepository;
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

    /**
     * Without a status or channel id, returns channels together with open pending commitments, ordered by creation
     * timestamp, from a single union query.
     */
    private List<ClprChannelData> findAllChannels(ClprChannelsRequest request) {
        final var timestamp = request.getTimestamp();
        validate(timestamp, true);
        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();

        return channelRepository.findAllChannelsByCreatedTimestampBetween(lowerBound, upperBound, getPage(request));
    }

    private List<ClprChannelData> findChannels(ClprChannelsRequest request) {
        final var channelId = request.getChannelId();
        final var status = request.getStatus();
        final var timestamp = request.getTimestamp();

        if (channelId != null && timestamp.isEmpty()) {
            final var channel = channelRepository.findByChannelId(channelId);
            if (channel == null || status != null && !status.name().equals(channel.getStatus())) {
                return List.of();
            }

            return List.of(channel);
        }

        validate(timestamp, channelId == null);
        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();
        final var statusName = status != null ? status.name() : null;

        if (channelId != null) {
            return channelRepository.findByChannelIdAndTimestampBetween(channelId, lowerBound, upperBound, statusName);
        }

        return channelRepository.findByCreatedTimestampBetween(lowerBound, upperBound, statusName, getPage(request));
    }

    private List<ClprChannelData> findPendingChannels(ClprChannelsRequest request) {
        if (request.getChannelId() != null) {
            throw new IllegalArgumentException("channel.id can't be combined with status PENDING");
        }

        final var timestamp = request.getTimestamp();
        validate(timestamp, true);
        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();

        return channelRepository.findPendingByCreatedTimestampBetween(lowerBound, upperBound, getPage(request));
    }

    private static PageRequest getPage(ClprChannelsRequest request) {
        return PageRequest.of(0, request.getLimit(), Sort.by(request.getOrder(), CREATED_TIMESTAMP));
    }

    /**
     * Validates the timestamp bounds. With {@code limitRange}, which is when no channel id is given, a bounded range
     * can't exceed the configured maximum since the query can't be served by a single shard.
     */
    private void validate(Bound timestamp, boolean limitRange) {
        timestamp.verifyUnsupported(RangeOperator.NE);
        timestamp.verifySingleOccurrence();

        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }

        if (!limitRange || !timestamp.hasLowerAndUpper()) {
            return;
        }

        final var maxRange = properties.getMaxClprChannelsTimestampRange();
        if (upperBound - lowerBound > maxRange.toNanos()) {
            throw new IllegalArgumentException("Timestamp range must be within %s".formatted(maxRange));
        }
    }
}
