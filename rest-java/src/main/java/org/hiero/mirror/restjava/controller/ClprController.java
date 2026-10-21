// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.CHANNEL_ID;
import static org.hiero.mirror.restjava.common.Constants.CONNECTOR_ID;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.DELETED;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.TIMESTAMP;

import com.google.common.collect.ImmutableSortedMap;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprConnector;
import org.hiero.mirror.rest.model.ClprConnectorsResponse;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprConnectorMapper;
import org.hiero.mirror.restjava.parameter.RangeParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.ClprService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RequestMapping(value = "/api/v1/clpr", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
final class ClprController {

    // A 32-byte hex id with an optional eq operator and 0x prefix. Ids are opaque, so no other operator is supported
    private static final String ID_REGEX = "^(eq:)?(0x)?[0-9a-f]{64}$";

    // created_timestamp is the consensus timestamp of the completing transaction, so it's unique per channel and is
    // the sole sort and pagination key
    private static final Function<ClprChannel, Map<String, String>> CHANNEL_EXTRACTOR =
            channel -> ImmutableSortedMap.of(TIMESTAMP, channel.getCreatedTimestamp());

    // Likewise, a connector's created_timestamp is unique, so it's the sole sort and pagination key
    private static final Function<ClprConnector, Map<String, String>> CONNECTOR_EXTRACTOR =
            connector -> ImmutableSortedMap.of(TIMESTAMP, connector.getCreatedTimestamp());

    private final ClprChannelMapper channelMapper;
    private final ClprConnectorMapper connectorMapper;
    private final LinkFactory linkFactory;
    private final ClprService service;

    @GetMapping("/channels")
    ClprChannelsResponse getChannels(
            @RequestParam(name = CHANNEL_ID, required = false)
                    @Pattern(regexp = ID_REGEX, flags = Pattern.Flag.CASE_INSENSITIVE)
                    @Nullable
                    String channelId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(required = false) @Nullable ClprChannelStatus status,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {

        final var request = clprChannelsRequest(channelId, limit, order, status, timestamps);
        final var channels = channelMapper.map(service.getChannels(request));

        final var sort = Sort.by(order, TIMESTAMP);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(channels, pageable, CHANNEL_EXTRACTOR);

        return new ClprChannelsResponse().channels(channels).links(links);
    }

    @GetMapping("/connectors")
    ClprConnectorsResponse getConnectors(
            @RequestParam(name = CHANNEL_ID, required = false)
                    @Pattern(regexp = ID_REGEX, flags = Pattern.Flag.CASE_INSENSITIVE)
                    @Nullable
                    String channelId,
            @RequestParam(name = CONNECTOR_ID, required = false)
                    @Pattern(regexp = ID_REGEX, flags = Pattern.Flag.CASE_INSENSITIVE)
                    @Nullable
                    String connectorId,
            @RequestParam(name = DELETED, required = false) @Nullable Boolean deleted,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {

        final var request = clprConnectorsRequest(channelId, connectorId, deleted, limit, order, timestamps);
        final var connectors = connectorMapper.map(service.getConnectors(request));

        final var sort = Sort.by(order, TIMESTAMP);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(connectors, pageable, CONNECTOR_EXTRACTOR);

        return new ClprConnectorsResponse().connectors(connectors).links(links);
    }

    private ClprChannelsRequest clprChannelsRequest(
            @Nullable String channelId,
            int limit,
            Sort.Direction order,
            @Nullable ClprChannelStatus status,
            TimestampParameter[] timestamps) {
        final var bounds = timestampBounds(timestamps);

        final var builder = ClprChannelsRequest.builder()
                .limit(limit)
                .lowerBound(bounds.lower())
                .order(order)
                .upperBound(bounds.upper());

        if (channelId != null) {
            builder.channelId(toBytes(channelId));
        }

        if (status != null) {
            builder.status(status);
        }

        return builder.build();
    }

    private ClprConnectorsRequest clprConnectorsRequest(
            @Nullable String channelId,
            @Nullable String connectorId,
            @Nullable Boolean deleted,
            int limit,
            Sort.Direction order,
            TimestampParameter[] timestamps) {
        final var bounds = timestampBounds(timestamps);
        final var builder = ClprConnectorsRequest.builder()
                .limit(limit)
                .lowerBound(bounds.lower())
                .order(order)
                .upperBound(bounds.upper());

        if (channelId != null) {
            builder.channelId(toBytes(channelId));
        }

        if (connectorId != null) {
            builder.connectorId(toBytes(connectorId));
        }

        if (deleted != null) {
            builder.deleted(deleted);
        }

        return builder.build();
    }

    private TimestampBounds timestampBounds(TimestampParameter[] timestamps) {
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

        return new TimestampBounds(lowerBound, upperBound);
    }

    private static byte[] toBytes(String id) {
        var hex = id.substring(id.indexOf(':') + 1);
        if (hex.regionMatches(true, 0, "0x", 0, 2)) {
            hex = hex.substring(2);
        }

        return HexFormat.of().parseHex(hex);
    }

    private record TimestampBounds(long lower, long upper) {}
}
