// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.CHANNEL_ID;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.parameter.ClprChannelIdParameter;
import org.hiero.mirror.restjava.parameter.RangeParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.ClprChannelService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RequestMapping(value = "/api/v1/clpr/channels", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
final class ClprChannelsController {

    // created_timestamp is the consensus timestamp of the completing transaction, so it's unique per channel and is
    // the sole sort and pagination key
    private static final Function<ClprChannel, Map<String, String>> EXTRACTOR =
            channel -> ImmutableSortedMap.of(TIMESTAMP, channel.getCreatedTimestamp());

    private final LinkFactory linkFactory;
    private final ClprChannelMapper mapper;
    private final ClprChannelService service;

    @GetMapping
    ClprChannelsResponse getChannels(
            @RequestParam(name = CHANNEL_ID, required = false) @Nullable ClprChannelIdParameter channelId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(required = false) @Nullable ClprChannelStatus status,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {

        final var request = clprChannelsRequest(channelId, limit, order, status, timestamps);
        final var channels = mapper.map(service.getChannels(request));

        final var sort = Sort.by(order, TIMESTAMP);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(channels, pageable, EXTRACTOR);

        return new ClprChannelsResponse().channels(channels).links(links);
    }

    private ClprChannelsRequest clprChannelsRequest(
            @Nullable ClprChannelIdParameter channelId,
            int limit,
            Sort.Direction order,
            @Nullable ClprChannelStatus status,
            TimestampParameter[] timestamps) {
        long lowerBound = 0L; // The most restrictive lower bound (max of all gt/gte/eq)
        long upperBound = Long.MAX_VALUE; // The most restrictive upper bound (min of all lt/lte/eq)

        for (final var timestamp : timestamps) {
            final var operator = timestamp.operator();
            final long value = RangeParameter.toInclusive(operator, timestamp.value());
            if (operator == RangeOperator.EQ) {
                lowerBound = Math.max(lowerBound, value);
                upperBound = Math.min(upperBound, value);
            } else if (timestamp.hasLowerBound()) {
                lowerBound = Math.max(lowerBound, value);
            } else if (timestamp.hasUpperBound()) {
                upperBound = Math.min(upperBound, value);
            }
        }

        final var builder = ClprChannelsRequest.builder()
                .limit(limit)
                .lowerBound(lowerBound)
                .order(order)
                .upperBound(upperBound);

        if (channelId != null) {
            builder.channelId(channelId.value());
        }

        if (status != null) {
            builder.status(status);
        }

        return builder.build();
    }
}
