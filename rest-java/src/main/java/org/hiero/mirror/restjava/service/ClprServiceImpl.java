// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.hiero.mirror.restjava.jooq.domain.tables.ClprChannel.CLPR_CHANNEL;

import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class ClprServiceImpl implements ClprService {

    private final QueryProperties queryProperties;
    private final ClprChannelRepository repository;

    @Override
    public List<ClprChannel> getChannels(final ClprChannelsRequest request) {
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("Invalid range provided for timestamp");
        }

        final var channelId = request.getChannelId();
        if (channelId == null && lowerBound > 0L && upperBound < Long.MAX_VALUE) {
            // Without channel.id the query scatter-gathers across shards, so cap the window. Open-ended ranges
            // (a default bound) aren't capped.
            final var maxRange = queryProperties.getMaxClprChannelsTimestampRange();
            if (upperBound - lowerBound > maxRange.toNanos()) {
                throw new IllegalArgumentException(
                        "Timestamp range by the lower and upper bounds must not exceed %s".formatted(maxRange));
            }
        }

        final var sort = Sort.by(request.getOrder(), CLPR_CHANNEL.CREATED_TIMESTAMP.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var status = request.getStatus();

        if (channelId != null) {
            return status == null
                    ? repository.findByChannelIdAndCreatedTimestampBetween(channelId, lowerBound, upperBound, page)
                    : repository.findByChannelIdAndCreatedTimestampBetweenAndStatus(
                            channelId, lowerBound, upperBound, status.name(), page);
        }

        return status == null
                ? repository.findByCreatedTimestampBetween(lowerBound, upperBound, page)
                : repository.findByCreatedTimestampBetweenAndStatus(lowerBound, upperBound, status.name(), page);
    }
}
