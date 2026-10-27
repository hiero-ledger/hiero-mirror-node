// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.CHANNEL_ID;
import static org.hiero.mirror.restjava.common.Constants.CONNECTOR_ID;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.DELETED;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MESSAGE_ID;
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
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprConnector;
import org.hiero.mirror.rest.model.ClprConnectorsResponse;
import org.hiero.mirror.rest.model.ClprMessage;
import org.hiero.mirror.rest.model.ClprMessagesResponse;
import org.hiero.mirror.rest.model.ClprPendingChannel;
import org.hiero.mirror.rest.model.ClprPendingChannelsResponse;
import org.hiero.mirror.rest.model.ClprPendingConnector;
import org.hiero.mirror.rest.model.ClprPendingConnectorsResponse;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.dto.ClprPendingRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprConnectorMapper;
import org.hiero.mirror.restjava.mapper.ClprMessageMapper;
import org.hiero.mirror.restjava.mapper.ClprPendingChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprPendingConnectorMapper;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.parameter.RangeParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.ClprService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // A path id has no operator
    private static final String PATH_ID_REGEX = "^(0x)?[0-9a-f]{64}$";

    // created_timestamp is the consensus timestamp of the completing transaction, so it's unique per channel and is
    // the sole sort and pagination key
    private static final Function<ClprChannel, Map<String, String>> CHANNEL_EXTRACTOR =
            channel -> ImmutableSortedMap.of(TIMESTAMP, channel.getCreatedTimestamp());

    // Likewise, a connector's created_timestamp is unique, so it's the sole sort and pagination key
    private static final Function<ClprConnector, Map<String, String>> CONNECTOR_EXTRACTOR =
            connector -> ImmutableSortedMap.of(TIMESTAMP, connector.getCreatedTimestamp());

    // Messages sharing a consensus timestamp are tiebroken by message_id, so both are needed to resume a page
    private static final Function<ClprMessage, Map<String, String>> MESSAGE_EXTRACTOR =
            message -> ImmutableSortedMap.of(
                    TIMESTAMP,
                    message.getTimestamp(),
                    MESSAGE_ID,
                    message.getMessageId().toString());

    // A pending channel's created_timestamp is the consensus timestamp of its registering transaction, so it's unique
    private static final Function<ClprPendingChannel, Map<String, String>> PENDING_CHANNEL_EXTRACTOR =
            pendingChannel -> ImmutableSortedMap.of(TIMESTAMP, pendingChannel.getCreatedTimestamp());

    // Likewise, a pending connector's created_timestamp is unique
    private static final Function<ClprPendingConnector, Map<String, String>> PENDING_CONNECTOR_EXTRACTOR =
            pendingConnector -> ImmutableSortedMap.of(TIMESTAMP, pendingConnector.getCreatedTimestamp());

    private final ClprChannelMapper channelMapper;
    private final ClprConnectorMapper connectorMapper;
    private final LinkFactory linkFactory;
    private final ClprMessageMapper messageMapper;
    private final ClprPendingChannelMapper pendingChannelMapper;
    private final ClprPendingConnectorMapper pendingConnectorMapper;
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

    @GetMapping("/channels/pending")
    ClprPendingChannelsResponse getPendingChannels(
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {

        final var bounds = bounds(timestamps);
        final var request = ClprPendingRequest.builder()
                .limit(limit)
                .lowerBound(bounds.lower())
                .order(order)
                .upperBound(bounds.upper())
                .build();
        final var pendingChannels = pendingChannelMapper.map(service.getPendingChannels(request));

        final var sort = Sort.by(order, TIMESTAMP);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(pendingChannels, pageable, PENDING_CHANNEL_EXTRACTOR);

        return new ClprPendingChannelsResponse()
                .pendingChannels(pendingChannels)
                .links(links);
    }

    @GetMapping("/connectors/pending")
    ClprPendingConnectorsResponse getPendingConnectors(
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {

        final var bounds = bounds(timestamps);
        final var request = ClprPendingRequest.builder()
                .limit(limit)
                .lowerBound(bounds.lower())
                .order(order)
                .upperBound(bounds.upper())
                .build();
        final var pendingConnectors = pendingConnectorMapper.map(service.getPendingConnectors(request));

        final var sort = Sort.by(order, TIMESTAMP);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(pendingConnectors, pageable, PENDING_CONNECTOR_EXTRACTOR);

        return new ClprPendingConnectorsResponse()
                .pendingConnectors(pendingConnectors)
                .links(links);
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

    @GetMapping("/channels/{channelId}/messages")
    ClprMessagesResponse getMessages(
            @PathVariable @Pattern(regexp = PATH_ID_REGEX, flags = Pattern.Flag.CASE_INSENSITIVE) String channelId,
            @RequestParam(name = CONNECTOR_ID, required = false)
                    @Pattern(regexp = ID_REGEX, flags = Pattern.Flag.CASE_INSENSITIVE)
                    @Nullable
                    String connectorId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(name = MESSAGE_ID, required = false, defaultValue = "") @Size(max = 2)
                    NumberRangeParameter[] messageIds,
            @RequestParam(defaultValue = "desc") Sort.Direction order,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps,
            @RequestParam(required = false) @Nullable ClprMessageType type) {

        final var request = clprMessagesRequest(channelId, connectorId, limit, messageIds, order, timestamps, type);
        final var messages = messageMapper.map(service.getMessages(request));

        final var sort = Sort.by(order, TIMESTAMP, MESSAGE_ID);
        final var pageable = PageRequest.of(0, limit, sort);
        final var links = linkFactory.create(messages, pageable, MESSAGE_EXTRACTOR);

        return new ClprMessagesResponse().messages(messages).links(links);
    }

    private ClprChannelsRequest clprChannelsRequest(
            @Nullable String channelId,
            int limit,
            Sort.Direction order,
            @Nullable ClprChannelStatus status,
            TimestampParameter[] timestamps) {
        final var bounds = bounds(timestamps);

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
        final var bounds = bounds(timestamps);
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

    @SuppressWarnings("java:S107")
    private ClprMessagesRequest clprMessagesRequest(
            String channelId,
            @Nullable String connectorId,
            int limit,
            NumberRangeParameter[] messageIds,
            Sort.Direction order,
            TimestampParameter[] timestamps,
            @Nullable ClprMessageType type) {
        final var timestampBounds = bounds(timestamps);
        final var messageIdBounds = bounds(messageIds);
        final var builder = ClprMessagesRequest.builder()
                .channelId(toBytes(channelId))
                .limit(limit)
                .messageIdLowerBound(messageIdBounds.lower())
                .messageIdUpperBound(messageIdBounds.upper())
                .order(order)
                .timestampLowerBound(timestampBounds.lower())
                .timestampUpperBound(timestampBounds.upper());

        if (connectorId != null) {
            builder.connectorId(toBytes(connectorId));
        }

        if (type != null) {
            builder.type(type);
        }

        return builder.build();
    }

    private Bounds bounds(RangeParameter<Long>[] params) {
        long lowerBound = 0L; // The most restrictive lower bound (max of all gt/gte/eq)
        long upperBound = Long.MAX_VALUE; // The most restrictive upper bound (min of all lt/lte/eq)

        for (final var param : params) {
            final var operator = param.operator();
            final long value = RangeParameter.toInclusive(operator, param.value());
            if (operator == RangeOperator.EQ) {
                lowerBound = Math.max(lowerBound, value);
                upperBound = Math.min(upperBound, value);
            } else if (param.hasLowerBound()) {
                lowerBound = Math.max(lowerBound, value);
            } else if (param.hasUpperBound()) {
                upperBound = Math.min(upperBound, value);
            }
        }

        return new Bounds(lowerBound, upperBound);
    }

    private static byte[] toBytes(String id) {
        var hex = id.substring(id.indexOf(':') + 1);
        if (hex.regionMatches(true, 0, "0x", 0, 2)) {
            hex = hex.substring(2);
        }

        return HexFormat.of().parseHex(hex);
    }

    private record Bounds(long lower, long upper) {}
}
