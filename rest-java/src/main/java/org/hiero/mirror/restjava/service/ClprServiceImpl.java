// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprPendingChannelMapper;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprPendingChannelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private static final String CREATED_TIMESTAMP = "created_timestamp";

    private final ClprChannelMapper channelMapper;
    private final ClprChannelRepository channelRepository;
    private final ClprPendingChannelMapper pendingChannelMapper;
    private final ClprPendingChannelRepository pendingChannelRepository;
    private final RestJavaProperties properties;

    @Override
    public List<org.hiero.mirror.rest.model.ClprChannel> getChannels(ClprChannelsRequest request) {
        if (request.getStatus() == ClprChannelStatus.PENDING) {
            return pendingChannelMapper.map(findPendingChannels(request));
        }

        return channelMapper.map(findChannels(request));
    }

    private Collection<ClprChannel> findChannels(ClprChannelsRequest request) {
        final var channelId = request.getChannelId();
        final var status = request.getStatus();
        final var timestamp = request.getTimestamp();

        if (channelId != null && timestamp.isEmpty()) {
            return channelRepository
                    .findById(channelId)
                    .filter(channel ->
                            status == null || channel.getStatus().name().equals(status.name()))
                    .stream()
                    .toList();
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

    private List<ClprChannelPendingCommitment> findPendingChannels(ClprChannelsRequest request) {
        if (request.getChannelId() != null) {
            throw new IllegalArgumentException("channel.id can't be combined with status PENDING");
        }

        final var timestamp = request.getTimestamp();
        validate(timestamp, true);
        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();

        return pendingChannelRepository.findOpenByCreatedTimestampBetween(lowerBound, upperBound, getPage(request));
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
