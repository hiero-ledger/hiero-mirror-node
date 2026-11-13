// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;
import static org.hiero.mirror.restjava.common.Constants.CHANNEL_ID;
import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.STATUS;
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
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.Links;
import org.hiero.mirror.restjava.common.LinkFactory;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.ClprService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RequestMapping(value = "/api/v1/clpr/channels", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
final class ClprController {

    private static final String CHANNEL_ID_PATTERN = "^(?i)(eq:)?0x[0-9a-f]{64}$";
    private static final String EQ_PREFIX = "eq:";
    private static final Function<ClprChannel, Map<String, String>> EXTRACTOR =
            channel -> ImmutableSortedMap.of(TIMESTAMP, channel.getCreatedTimestamp());
    private static final String STATUS_PATTERN = "^(?i)(eq:)?(PENDING|ACTIVE|PAUSED|CLOSING|DRAINED|CLOSED)$";

    private final ClprService clprService;
    private final LinkFactory linkFactory;

    @GetMapping
    ResponseEntity<ClprChannelsResponse> getChannels(
            @RequestParam(name = CHANNEL_ID, required = false) @Nullable @Pattern(regexp = CHANNEL_ID_PATTERN)
                    String channelId,
            @RequestParam(defaultValue = DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(defaultValue = "desc") Direction order,
            @RequestParam(name = STATUS, required = false) @Nullable @Pattern(regexp = STATUS_PATTERN) String status,
            @RequestParam(name = TIMESTAMP, required = false, defaultValue = "") @Size(max = 2)
                    TimestampParameter[] timestamps) {
        final var request = ClprChannelsRequest.builder()
                .channelId(
                        channelId != null
                                ? HexFormat.of().parseHex(stripEq(channelId).substring(2))
                                : null)
                .limit(limit)
                .order(order)
                .status(
                        status != null
                                ? ClprChannelStatus.fromValue(stripEq(status).toUpperCase())
                                : null)
                .timestamp(Bound.of(timestamps, TIMESTAMP, null))
                .build();

        final var channels = clprService.getChannels(request);

        // A channel id returns at most one channel, so there is no next page
        final var links = request.getChannelId() != null
                ? new Links()
                : linkFactory.create(channels, PageRequest.of(0, limit, Sort.by(order, TIMESTAMP)), EXTRACTOR);

        final var response = new ClprChannelsResponse();
        response.setChannels(channels);
        response.setLinks(links);
        return ResponseEntity.ok(response);
    }

    private static String stripEq(String value) {
        return value.regionMatches(true, 0, EQ_PREFIX, 0, EQ_PREFIX.length())
                ? value.substring(EQ_PREFIX.length())
                : value;
    }
}
