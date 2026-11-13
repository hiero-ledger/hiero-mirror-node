// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelHistory;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprMessagesResponse;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class ClprControllerTest extends ControllerTest {

    private final ClprChannelMapper channelMapper;
    private final ClprMessageMapper messageMapper;

    @DisplayName("/api/v1/clpr/channels")
    @Nested
    final class ChannelsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "clpr/channels";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persistChannel(100L, ClprChannelStatus.ACTIVE);
            return uriSpec.uri("");
        }

        @Test
        void list() {
            // given
            final var channel1 = persistChannel(100L, ClprChannelStatus.ACTIVE);
            final var channel2 = persistChannel(200L, ClprChannelStatus.PAUSED);

            // when
            final var actual = restClient.get().uri("").retrieve().toEntity(ClprChannelsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getChannels()).isEqualTo(channelMapper.map(asData(channel2, channel1)));
                assertThat(response.getLinks().getNext()).isNull();
            });
            assertThat(actual.getHeaders().containsHeader(HttpHeaders.LINK)).isFalse();
            // Based on application.yml response headers configuration
            assertThat(actual.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
        }

        @Test
        void empty() {
            // when
            final var actual = restClient.get().uri("").retrieve().body(ClprChannelsResponse.class);

            // then
            assertThat(actual)
                    .isNotNull()
                    .satisfies(response -> assertThat(response.getChannels()).isEmpty());
        }

        @Test
        void orderAndStatus() {
            // given
            final var active1 = persistChannel(100L, ClprChannelStatus.ACTIVE);
            persistChannel(200L, ClprChannelStatus.PAUSED);
            final var active2 = persistChannel(300L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?status=eq:ACTIVE&order=asc")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEqualTo(channelMapper.map(asData(active1, active2)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"status=active", "status=ACTIVE"})
        void statusWithoutOperatorIsCaseInsensitive(String query) {
            // given
            final var channel = persistChannel(100L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient.get().uri("?" + query).retrieve().body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEqualTo(channelMapper.map(asData(channel)));
        }

        @Test
        void timestampBounds() {
            // given
            persistChannel(100L, ClprChannelStatus.ACTIVE);
            final var inRange = persistChannel(200L, ClprChannelStatus.ACTIVE);
            persistChannel(300L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?timestamp=gte:0.000000200&timestamp=lt:0.000000300")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEqualTo(channelMapper.map(asData(inRange)));
        }

        @Test
        void limitAndNextLink() {
            // given
            persistChannel(100L, ClprChannelStatus.ACTIVE);
            final var channel2 = persistChannel(200L, ClprChannelStatus.ACTIVE);
            final var channel3 = persistChannel(300L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient.get().uri("?limit=2").retrieve().toEntity(ClprChannelsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getChannels()).isEqualTo(channelMapper.map(asData(channel3, channel2)));
                assertThat(response.getLinks().getNext())
                        .isEqualTo("/api/v1/clpr/channels?limit=2&timestamp=lt:0.000000200");
            });
            assertThat(actual.getHeaders().containsHeader(HttpHeaders.LINK)).isTrue();
        }

        @Test
        void byChannelId() {
            // given
            final var channel = persistChannel(100L, ClprChannelStatus.ACTIVE);
            persistChannel(200L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}", "eq:" + hex(channel.getChannelId()))
                    .retrieve()
                    .toEntity(ClprChannelsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getChannels()).isEqualTo(channelMapper.map(asData(channel)));
                assertThat(response.getLinks().getNext()).isNull();
            });
        }

        @Test
        void byChannelIdWithoutOperator() {
            // given
            final var channel = persistChannel(100L, ClprChannelStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}", hex(channel.getChannelId()))
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEqualTo(channelMapper.map(asData(channel)));
        }

        @Test
        void byChannelIdNotFound() {
            // when
            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}", hex(domainBuilder.bytes(32)))
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEmpty();
        }

        @Test
        void byChannelIdAsOfTimestamp() {
            // given
            final var channelId = domainBuilder.bytes(32);
            final var history = persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
            persistChannel(channelId, 100L, ClprChannelStatus.PAUSED, Range.atLeast(200L));

            // when
            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&timestamp=eq:0.000000150", hex(channelId))
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels()).isEqualTo(channelMapper.map(asData(history)));
        }

        @Test
        void pending() {
            // given
            persistChannel(100L, ClprChannelStatus.ACTIVE);
            persistPending(200L, ClprPendingCommitmentStatus.COMPLETED);
            final var open1 = persistPending(300L, ClprPendingCommitmentStatus.OPEN);
            final var open2 = persistPending(400L, ClprPendingCommitmentStatus.OPEN);

            // when
            final var actual =
                    restClient.get().uri("?status=eq:PENDING").retrieve().body(ClprChannelsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getChannels())
                    .extracting(channel -> channel.getOwnershipCommitment())
                    .containsExactly(hex(open2.getOwnershipCommitment()), hex(open1.getOwnershipCommitment()));
            assertThat(actual.getChannels())
                    .allSatisfy(channel ->
                            assertThat(channel.getStatus().getValue()).isEqualTo("PENDING"));
        }

        @Test
        void pendingWithChannelId() {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> restClient
                    .get()
                    .uri("?status=PENDING&channel.id={id}", hex(domainBuilder.bytes(32)))
                    .retrieve()
                    .toBodilessEntity();

            // then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "channel.id can't be combined with status PENDING");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "channel.id=0x1234",
                    "channel.id=gt:0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809",
                    "status=eq:UNKNOWN",
                    "status=ne:ACTIVE",
                    "limit=0",
                    "limit=101",
                    "order=up",
                    "timestamp=ne:1",
                    "timestamp=abc",
                    "timestamp=gt:1&timestamp=gt:2",
                    "timestamp=gte:2&timestamp=lte:1",
                    "timestamp=1&timestamp=2&timestamp=3",
                    "timestamp=gte:0&timestamp=lte:2592001", // over the 30d maximum without channel.id
                })
        void invalidParameters(String query) {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable)
                    () -> restClient.get().uri("?" + query).retrieve().toBodilessEntity();

            // then
            org.assertj.core.api.Assertions.assertThatThrownBy(callable)
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
    }

    @DisplayName("/api/v1/clpr/channels/{channelId}/messages")
    @Nested
    final class MessagesEndpointTest extends EndpointTest {

        private static final String RANGE = "timestamp=gte:0&timestamp=lte:1";

        private byte[] channelId;

        @Override
        protected String getUrl() {
            return "clpr/channels";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            channelId = domainBuilder.bytes(32);
            persistMessage(channelId, 100L, ClprMessageType.DATA);
            return uriSpec.uri("/{id}/messages?timestamp=gte:0", hex(channelId));
        }

        @Test
        void list() {
            // given
            channelId = domainBuilder.bytes(32);
            final var message1 = persistMessage(channelId, 100L, ClprMessageType.DATA);
            final var message2 = persistMessage(channelId, 200L, ClprMessageType.RESPONSE);
            persistMessage(domainBuilder.bytes(32), 150L, ClprMessageType.DATA); // another channel

            // when
            final var actual = get("timestamp=gte:0");

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getMessages()).isEqualTo(messageMapper.map(asData(message2, message1)));
                assertThat(response.getLinks().getNext()).isNull();
            });
            assertThat(actual.getHeaders().getCacheControl()).isNotBlank();
        }

        @Test
        void empty() {
            // given
            channelId = domainBuilder.bytes(32);

            // when
            final var actual = get("timestamp=gte:0");

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getMessages()).isEmpty();
                assertThat(response.getLinks().getNext()).isNull();
            });
        }

        @Test
        void acknowledged() {
            // given
            channelId = domainBuilder.bytes(32);
            final var builder = ClprChannel.builder()
                    .ackedMessageId(1L)
                    .chainId("eip155:1")
                    .channelId(channelId)
                    .createdTimestamp(1L)
                    .endpointManifestVersion(0L)
                    .nextMessageId(3L)
                    .ownershipCommitment(domainBuilder.bytes(32))
                    .receivedMessageId(0L)
                    .status(ClprChannelStatus.ACTIVE)
                    .timestampRange(Range.atLeast(1L));
            domainBuilder.wrap(builder, builder::build).persist();
            persistMessage(channelId, 100L, 1L, ClprMessageType.DATA);
            persistMessage(channelId, 200L, 2L, ClprMessageType.DATA);

            // when
            final var actual = get("timestamp=gte:0&order=asc");

            // then
            assertThat(actual.getBody().getMessages())
                    .extracting(m -> m.getAcknowledged())
                    .containsExactly(true, false);
        }

        @Test
        void orderTypeAndTimestamp() {
            // given
            channelId = domainBuilder.bytes(32);
            persistMessage(channelId, 100L, ClprMessageType.DATA);
            final var data = persistMessage(channelId, 200L, ClprMessageType.DATA);
            persistMessage(channelId, 300L, ClprMessageType.RESPONSE);
            persistMessage(channelId, 400L, ClprMessageType.DATA);

            // when
            final var actual = get("timestamp=gt:0.000000100&timestamp=lt:0.000000400&type=data");

            // then
            assertThat(actual.getBody().getMessages()).isEqualTo(messageMapper.map(asData(data)));
        }

        @Test
        void byConnectorId() {
            // given
            channelId = domainBuilder.bytes(32);
            final var message = persistMessage(channelId, 100L, ClprMessageType.DATA);
            persistMessage(channelId, 200L, ClprMessageType.DATA);

            // when
            final var actual = get("timestamp=gte:0&connector.id=eq:" + hex(message.getConnectorId()));

            // then
            assertThat(actual.getBody().getMessages()).isEqualTo(messageMapper.map(asData(message)));
        }

        @Test
        void limitAndNextLink() {
            // given
            channelId = domainBuilder.bytes(32);
            persistMessage(channelId, 100L, ClprMessageType.DATA);
            final var message2 = persistMessage(channelId, 200L, ClprMessageType.DATA);
            final var message3 = persistMessage(channelId, 300L, ClprMessageType.DATA);

            // when
            final var actual = get("timestamp=gte:0&limit=2");

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getMessages()).isEqualTo(messageMapper.map(asData(message3, message2)));
                assertThat(response.getLinks().getNext()).startsWith("/api/v1/clpr/channels/");
                assertThat(response.getLinks().getNext())
                        .endsWith("/messages?timestamp=gte:0&timestamp=lt:0.000000200&limit=2");
            });
            assertThat(actual.getHeaders().containsHeader(HttpHeaders.LINK)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "timestamp=gte:0&connector.id=0x1234",
                    "timestamp=gte:0&limit=0",
                    "timestamp=gte:0&limit=101",
                    "timestamp=gte:0&order=up",
                    "timestamp=gte:0&type=unknown",
                    "timestamp=gte:0&type=ne:DATA",
                    "timestamp=ne:1",
                    "timestamp=abc",
                    "timestamp=gt:1&timestamp=gt:2",
                    "timestamp=gte:2&timestamp=lte:1",
                    "timestamp=1&timestamp=2&timestamp=3",
                    "timestamp=gte:0&timestamp=lte:2592001", // over the 30d maximum
                })
        void invalidParameters(String query) {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> restClient
                    .get()
                    .uri("/{id}/messages?" + query, hex(domainBuilder.bytes(32)))
                    .retrieve()
                    .toBodilessEntity();

            // then
            org.assertj.core.api.Assertions.assertThatThrownBy(callable)
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0x1234", "eq:0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"})
        void invalidChannelId(String id) {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> restClient
                    .get()
                    .uri("/{id}/messages?timestamp=gte:0", id)
                    .retrieve()
                    .toBodilessEntity();

            // then
            org.assertj.core.api.Assertions.assertThatThrownBy(callable)
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private org.springframework.http.ResponseEntity<ClprMessagesResponse> get(String query) {
            return restClient
                    .get()
                    .uri("/{id}/messages?" + query, hex(channelId))
                    .retrieve()
                    .toEntity(ClprMessagesResponse.class);
        }
    }

    private static List<org.hiero.mirror.restjava.dto.ClprMessageDto> asData(ClprMessage... messages) {
        return Arrays.stream(messages)
                .map(m -> org.hiero.mirror.restjava.dto.ClprMessageDto.builder()
                        .channelId(m.getChannelId())
                        .connectorId(m.getConnectorId())
                        .consensusTimestamp(m.getConsensusTimestamp())
                        .messageData(m.getMessageData())
                        .messageId(m.getMessageId())
                        .messageType(m.getMessageType().name())
                        .replyStatus(
                                m.getReplyStatus() != null ? m.getReplyStatus().name() : null)
                        .replyToMessageId(m.getReplyToMessageId())
                        .runningHash(m.getRunningHash())
                        .sender(m.getSender())
                        .targetApplication(m.getTargetApplication())
                        .build())
                .toList();
    }

    private ClprMessage persistMessage(byte[] channelId, long timestamp, ClprMessageType type) {
        return persistMessage(channelId, timestamp, domainBuilder.number(), type);
    }

    private ClprMessage persistMessage(byte[] channelId, long timestamp, long messageId, ClprMessageType type) {
        return domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channelId)
                        .consensusTimestamp(timestamp)
                        .controlMessage(null)
                        .messageId(messageId)
                        .messageType(type))
                .persist();
    }

    private static String hex(byte[] bytes) {
        return "0x" + HexFormat.of().formatHex(bytes);
    }

    private static List<ClprChannelDto> asData(AbstractClprChannel... channels) {
        return Arrays.stream(channels)
                .map(channel -> ClprChannelDto.builder()
                        .ackedMessageId(channel.getAckedMessageId())
                        .chainId(channel.getChainId())
                        .channelId(channel.getChannelId())
                        .createdTimestamp(channel.getCreatedTimestamp())
                        .endpointManifestVersion(channel.getEndpointManifestVersion())
                        .nextMessageId(channel.getNextMessageId())
                        .ownershipCommitment(channel.getOwnershipCommitment())
                        .receivedMessageId(channel.getReceivedMessageId())
                        .status(channel.getStatus().name())
                        .timestampLower(channel.getTimestampRange().lowerEndpoint())
                        .timestampUpper(
                                channel.getTimestampRange().hasUpperBound()
                                        ? channel.getTimestampRange().upperEndpoint()
                                        : null)
                        .build())
                .toList();
    }

    private ClprChannel persistChannel(long createdTimestamp, ClprChannelStatus status) {
        return persistChannel(domainBuilder.bytes(32), createdTimestamp, status, Range.atLeast(createdTimestamp));
    }

    private ClprChannel persistChannel(
            byte[] channelId, long createdTimestamp, ClprChannelStatus status, Range<Long> timestampRange) {
        final var builder = ClprChannel.builder()
                .ackedMessageId(0L)
                .chainId("eip155:1")
                .channelId(channelId)
                .createdTimestamp(createdTimestamp)
                .endpointManifestVersion(0L)
                .nextMessageId(1L)
                .ownershipCommitment(domainBuilder.bytes(32))
                .receivedMessageId(0L)
                .status(status)
                .timestampRange(timestampRange);
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    private ClprChannelHistory persistHistory(byte[] channelId, ClprChannelStatus status, Range<Long> range) {
        final var builder = ClprChannelHistory.builder()
                .ackedMessageId(0L)
                .chainId("eip155:1")
                .channelId(channelId)
                .createdTimestamp(100L)
                .endpointManifestVersion(0L)
                .nextMessageId(1L)
                .ownershipCommitment(domainBuilder.bytes(32))
                .receivedMessageId(0L)
                .status(status)
                .timestampRange(range);
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    private ClprChannelPendingCommitment persistPending(long createdTimestamp, ClprPendingCommitmentStatus status) {
        final var builder = ClprChannelPendingCommitment.builder()
                .createdTimestamp(createdTimestamp)
                .ownershipCommitment(domainBuilder.bytes(32))
                .status(status)
                .timestampRange(Range.atLeast(createdTimestamp));
        return domainBuilder.wrap(builder, builder::build).persist();
    }
}
