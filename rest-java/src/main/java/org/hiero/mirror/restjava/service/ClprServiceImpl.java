// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static com.hederahashgraph.api.proto.java.ClprChannelStatus.valueOf;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private final ClprChannelRepository channelRepository;

    @Override
    public List<ClprChannelDto> getChannels(ClprChannelsRequest request) {
        final var channelId = request.getChannelId();
        final var status = request.getStatus();
        if (channelId != null && status == ClprChannelStatus.PENDING) {
            throw new IllegalArgumentException("channel.id can't be combined with status PENDING");
        }

        final var bounds = getBounds(request.getTimestamp());
        final Integer statusNumber = status != null ? valueOf(status.name()).getNumber() : null;

        // Without a timestamp the bounds are open, so this is the channel's current state
        if (channelId != null) {
            return channelRepository.findByChannelIdAndTimestampBetween(
                    channelId, bounds.lower(), bounds.upper(), statusNumber);
        }

        return channelRepository.findAllByCreatedTimestampBetween(
                bounds.lower(), bounds.upper(), statusNumber, request.getOrder(), request.getLimit());
    }

    /** Validates the timestamp and returns its inclusive bounds. */
    private TimestampBounds getBounds(Bound timestamp) {
        timestamp.verifyUnsupported(RangeOperator.NE);
        timestamp.verifySingleOccurrence();

        final long lowerBound = timestamp.getAdjustedLowerRangeValue();
        final long upperBound = timestamp.adjustUpperBound();
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }

        return new TimestampBounds(lowerBound, upperBound);
    }

    private record TimestampBounds(long lower, long upper) {}
}
