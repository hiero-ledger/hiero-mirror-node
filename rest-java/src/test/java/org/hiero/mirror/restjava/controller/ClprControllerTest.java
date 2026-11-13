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
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprConnectorsResponse;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprConnectorMapper;
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
    private final ClprConnectorMapper connectorMapper;

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

    @DisplayName("/api/v1/clpr/channels/{channelId}/connectors")
    @Nested
    final class ConnectorsEndpointTest extends EndpointTest {

        private final byte[] channelId = domainBuilder.bytes(32);

        @Override
        protected String getUrl() {
            return "clpr/channels/{channelId}/connectors";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            return uriSpec.uri("", hex(channelId));
        }

        @Test
        void list() {
            // given
            final var connector1 = persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            final var connector2 = persistConnector(channelId, 200L, ClprConnectorStatus.PENDING);
            persistConnector(domainBuilder.bytes(32), 300L, ClprConnectorStatus.ACTIVE); // another channel

            // when
            final var actual =
                    restClient.get().uri("", hex(channelId)).retrieve().toEntity(ClprConnectorsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getConnectors()).isEqualTo(connectorMapper.map(List.of(connector2, connector1)));
                assertThat(response.getLinks().getNext()).isNull();
            });
            // Based on application.yml response headers configuration
            assertThat(actual.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
        }

        @Test
        void empty() {
            // when
            final var actual =
                    restClient.get().uri("", hex(channelId)).retrieve().body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors()).isEmpty();
        }

        @Test
        void orderAndStatus() {
            // given
            final var active1 = persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            persistConnector(channelId, 200L, ClprConnectorStatus.DELETED);
            final var active2 = persistConnector(channelId, 300L, ClprConnectorStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?status=eq:ACTIVE&order=asc", hex(channelId))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors()).isEqualTo(connectorMapper.map(List.of(active1, active2)));
        }

        @Test
        void pending() {
            // given
            persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            final var pending = persistPendingConnector(channelId, 200L);

            // when
            final var actual = restClient
                    .get()
                    .uri("?status=PENDING", hex(channelId))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors()).isEqualTo(connectorMapper.map(List.of(pending)));
            assertThat(actual.getConnectors())
                    .singleElement()
                    .satisfies(
                            connector -> assertThat(connector.getConnectorId()).isNull());
        }

        @Test
        void timestampBounds() {
            // given
            persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            final var inRange = persistConnector(channelId, 200L, ClprConnectorStatus.ACTIVE);
            persistConnector(channelId, 300L, ClprConnectorStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?timestamp=gte:0.000000200&timestamp=lt:0.000000300", hex(channelId))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors()).isEqualTo(connectorMapper.map(List.of(inRange)));
        }

        @Test
        void limitAndNextLink() {
            // given
            persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            final var connector2 = persistConnector(channelId, 200L, ClprConnectorStatus.ACTIVE);
            final var connector3 = persistConnector(channelId, 300L, ClprConnectorStatus.ACTIVE);

            // when
            final var actual =
                    restClient.get().uri("?limit=2", hex(channelId)).retrieve().toEntity(ClprConnectorsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getConnectors()).isEqualTo(connectorMapper.map(List.of(connector3, connector2)));
                assertThat(response.getLinks().getNext())
                        .isEqualTo("/api/v1/clpr/channels/" + hex(channelId)
                                + "/connectors?limit=2&timestamp=lt:0.000000200");
            });
        }

        @Test
        void byConnectorId() {
            // given
            final var connector = persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
            persistConnector(channelId, 200L, ClprConnectorStatus.ACTIVE);

            // when
            final var actual = restClient
                    .get()
                    .uri("?connector.id={id}", hex(channelId), "eq:" + hex(connector.getConnectorId()))
                    .retrieve()
                    .toEntity(ClprConnectorsResponse.class);

            // then
            assertThat(actual.getBody()).isNotNull().satisfies(response -> {
                assertThat(response.getConnectors()).isEqualTo(connectorMapper.map(List.of(connector)));
                assertThat(response.getLinks().getNext()).isNull();
            });
        }

        @Test
        void byConnectorIdNotFound() {
            // when
            final var actual = restClient
                    .get()
                    .uri("?connector.id={id}", hex(channelId), hex(domainBuilder.bytes(32)))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors()).isEmpty();
        }

        @Test
        void byConnectorIdAsOfTimestamp() {
            // given
            final var connectorId = domainBuilder.bytes(32);
            final var commitment = domainBuilder.bytes(32);
            final var historyBuilder = ClprConnectorHistory.builder()
                    .channelId(channelId)
                    .commitment(commitment)
                    .connectorId(connectorId)
                    .createdTimestamp(100L)
                    .inFlightMessageCount(0L)
                    .lockedStake(100L)
                    .slashCount(0L)
                    .status(ClprConnectorStatus.ACTIVE)
                    .timestampRange(Range.closedOpen(100L, 200L));
            domainBuilder.wrap(historyBuilder, historyBuilder::build).persist();
            final var currentBuilder = ClprConnector.builder()
                    .channelId(channelId)
                    .commitment(commitment)
                    .connectorId(connectorId)
                    .createdTimestamp(100L)
                    .inFlightMessageCount(0L)
                    .lockedStake(100L)
                    .slashCount(0L)
                    .status(ClprConnectorStatus.DELETED)
                    .timestampRange(Range.atLeast(200L));
            domainBuilder.wrap(currentBuilder, currentBuilder::build).persist();

            // when
            final var actual = restClient
                    .get()
                    .uri("?connector.id={id}&timestamp=eq:0.000000150", hex(channelId), hex(connectorId))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getConnectors())
                    .singleElement()
                    .satisfies(connector ->
                            assertThat(connector.getStatus().getValue()).isEqualTo("ACTIVE"));
        }

        @Test
        void pendingWithConnectorId() {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> restClient
                    .get()
                    .uri("?status=PENDING&connector.id={id}", hex(channelId), hex(domainBuilder.bytes(32)))
                    .retrieve()
                    .toBodilessEntity();

            // then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "connector.id can't be combined with status PENDING");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "connector.id=0x1234",
                    "connector.id=gt:0x1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809",
                    "status=eq:UNKNOWN",
                    "status=ACTIVE&status=PENDING",
                    "status=ne:ACTIVE",
                    "limit=0",
                    "limit=101",
                    "order=up",
                    "timestamp=ne:1",
                    "timestamp=abc",
                    "timestamp=gt:1&timestamp=gt:2",
                    "timestamp=gte:2&timestamp=lte:1",
                    "timestamp=gte:0&timestamp=lte:2592001", // over the 30d maximum without connector.id
                })
        void invalidParameters(String query) {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () ->
                    restClient.get().uri("?" + query, hex(channelId)).retrieve().toBodilessEntity();

            // then
            org.assertj.core.api.Assertions.assertThatThrownBy(callable)
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0x1234", "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"})
        void invalidChannelId(String invalidChannelId) {
            // when
            final var callable = (org.assertj.core.api.ThrowableAssert.ThrowingCallable)
                    () -> restClient.get().uri("", invalidChannelId).retrieve().toBodilessEntity();

            // then
            org.assertj.core.api.Assertions.assertThatThrownBy(callable)
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
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

    private ClprConnector persistConnector(byte[] channelId, long createdTimestamp, ClprConnectorStatus status) {
        final var builder = ClprConnector.builder()
                .channelId(channelId)
                .commitment(domainBuilder.bytes(32))
                .connectorId(domainBuilder.bytes(32))
                .createdTimestamp(createdTimestamp)
                .inFlightMessageCount(0L)
                .lockedStake(100L)
                .slashCount(0L)
                .status(status)
                .timestampRange(Range.atLeast(createdTimestamp));
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    private ClprConnector persistPendingConnector(byte[] channelId, long createdTimestamp) {
        final var builder = ClprConnector.builder()
                .channelId(channelId)
                .commitment(domainBuilder.bytes(32))
                .createdTimestamp(createdTimestamp)
                .inFlightMessageCount(0L)
                .lockedStake(0L)
                .slashCount(0L)
                .status(ClprConnectorStatus.PENDING)
                .timestampRange(Range.atLeast(createdTimestamp));
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
