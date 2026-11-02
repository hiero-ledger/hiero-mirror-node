// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.Range;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprConnectorsResponse;
import org.hiero.mirror.rest.model.ClprEndpointManifestEntry;
import org.hiero.mirror.rest.model.ClprLedgerConfiguration;
import org.hiero.mirror.rest.model.ClprMessagesResponse;
import org.hiero.mirror.rest.model.ClprPendingConnectorsResponse;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprConnectorMapper;
import org.hiero.mirror.restjava.mapper.ClprEndpointManifestMapper;
import org.hiero.mirror.restjava.mapper.ClprLedgerConfigurationMapper;
import org.hiero.mirror.restjava.mapper.ClprPendingChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprPendingConnectorMapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
final class ClprControllerTest extends ControllerTest {

    private final ClprConnectorMapper connectorMapper;
    private final ClprChannelMapper mapper;
    private final ClprEndpointManifestMapper endpointManifestMapper;
    private final ClprLedgerConfigurationMapper ledgerConfigurationMapper;
    private final ClprPendingChannelMapper pendingChannelMapper;
    private final ClprPendingConnectorMapper pendingConnectorMapper;

    @DisplayName("GET /api/v1/clpr/channels")
    @Nested
    final class ChannelsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "clpr/channels";
        }

        @Test
        void empty() {
            final var actual = restClient.get().uri("").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).isEmpty();
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void cacheControl() {
            final var response = restClient.get().uri("").retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
        }

        @Test
        void defaultOrderIsDescending() {
            final var channel1 = persist(100L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(200L, ClprChannelStatus.CLOSED);

            final var actual = restClient.get().uri("").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel2, channel1)));
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void ascendingOrder() {
            final var channel1 = persist(100L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(200L, ClprChannelStatus.CLOSED);

            final var actual = restClient.get().uri("?order=asc").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel1, channel2)));
        }

        @Test
        void statusFilter() {
            final var active = persist(100L, ClprChannelStatus.ACTIVE);
            persist(200L, ClprChannelStatus.CLOSED);

            final var actual = restClient.get().uri("?status=ACTIVE").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(active)));
        }

        @Test
        void timestampRange() {
            persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);
            persist(3_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gte:2.000000000&timestamp=lt:3")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel2)));
        }

        @Test
        void timestampEq() {
            persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual =
                    restClient.get().uri("?timestamp=eq:2.000000000").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel2)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"%s", "0x%s", "eq:%s", "eq:0x%s"})
        void channelIdFilter(String format) {
            final var channel = persist(100L, ClprChannelStatus.ACTIVE);
            persist(200L, ClprChannelStatus.ACTIVE);
            final var id = format.formatted(HexFormat.of().formatHex(channel.getChannelId()));

            final var actual =
                    restClient.get().uri("?channel.id={id}", id).retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel)));
        }

        @Test
        void channelIdWithStatus() {
            final var channel = persist(100L, ClprChannelStatus.ACTIVE);
            final var id = HexFormat.of().formatHex(channel.getChannelId());

            assertThat(restClient
                            .get()
                            .uri("?channel.id={id}&status=ACTIVE", id)
                            .retrieve()
                            .body(ClprChannelsResponse.class)
                            .getChannels())
                    .hasSize(1);
            assertThat(restClient
                            .get()
                            .uri("?channel.id={id}&status=CLOSED", id)
                            .retrieve()
                            .body(ClprChannelsResponse.class)
                            .getChannels())
                    .isEmpty();
        }

        @Test
        void limitAndNextLink() {
            persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel3 = persist(3_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual = restClient.get().uri("?limit=2").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel3, channel2)));
            assertThat(actual.getLinks().getNext())
                    .isEqualTo("/api/v1/clpr/channels?limit=2&timestamp=lt:%s"
                            .formatted(DomainUtils.toTimestamp(channel2.getCreatedTimestamp())));
        }

        @Test
        void timestampGtAndLtAreExclusive() {
            persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);
            persist(3_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gt:1.000000000&timestamp=lt:3.000000000")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel2)));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 100})
        void limitBoundaries(int limit) {
            persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            persist(2_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual =
                    restClient.get().uri("?limit={limit}", limit).retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).hasSize(Math.min(limit, 2));
        }

        @Test
        void nextLinkAscending() {
            final var channel1 = persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            final var channel2 = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);
            persist(3_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual =
                    restClient.get().uri("?limit=2&order=asc").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel1, channel2)));
            assertThat(queryParams(actual.getLinks().getNext()))
                    .containsEntry("limit", List.of("2"))
                    .containsEntry("order", List.of("asc"))
                    .containsEntry(
                            "timestamp", List.of("gt:" + DomainUtils.toTimestamp(channel2.getCreatedTimestamp())));
        }

        @Test
        void nextLinkKeepsFilters() {
            final var channel = persist(1_000_000_000L, ClprChannelStatus.ACTIVE);
            persist(2_000_000_000L, ClprChannelStatus.CLOSED);
            final var id = "0x" + HexFormat.of().formatHex(channel.getChannelId());

            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&status=ACTIVE&limit=1", id)
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).hasSize(1);
            assertThat(queryParams(actual.getLinks().getNext()))
                    .containsEntry("channel.id", List.of(id))
                    .containsEntry("status", List.of("ACTIVE"))
                    .containsEntry(
                            "timestamp", List.of("lt:" + DomainUtils.toTimestamp(channel.getCreatedTimestamp())));
        }

        @Test
        void channelIdWithTimestampReturnsHistoricalState() {
            final var current = domainBuilder
                    .clprChannel()
                    .customize(c -> c.status(ClprChannelStatus.CLOSED).timestampRange(Range.atLeast(200L)))
                    .persist();
            final var history = domainBuilder
                    .clprChannelHistory()
                    .customize(c -> c.channelId(current.getChannelId())
                            .status(ClprChannelStatus.ACTIVE)
                            .timestampRange(Range.closedOpen(100L, 200L)))
                    .persist();
            final var id = HexFormat.of().formatHex(current.getChannelId());

            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&timestamp=eq:0.000000150", id)
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels())
                    .singleElement()
                    .extracting(c -> c.getStatus())
                    .isEqualTo(org.hiero.mirror.rest.model.ClprChannelStatus.ACTIVE);
            assertThat(history.getStatus()).isEqualTo(ClprChannelStatus.ACTIVE);
        }

        @Test
        void channelIdWithTimestampRequiresEq() {
            final var id = HexFormat.of().formatHex(new byte[32]);
            validateError(
                    () -> restClient
                            .get()
                            .uri("?channel.id={id}&timestamp=gte:1", id)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp must use the eq operator when filtering by channel.id");
        }

        @Test
        void timestampRangeExceedsMax() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=gte:1&timestamp=lte:2592002")
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "Timestamp range by the lower and upper bounds must not exceed PT720H");
        }

        @Test
        void timestampRangeAtMax() {
            final var channel = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gte:1&timestamp=lte:2592001")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"?timestamp=eq:5&timestamp=eq:6", "?timestamp=eq:5&timestamp=gt:5"})
        void contradictoryTimestamps(String query) {
            validateError(
                    () -> restClient.get().uri(query).retrieve().toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid range provided for timestamp");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?limit=0",
                    "?limit=101",
                    "?order=invalid",
                    "?status=INVALID",
                    "?channel.id=abc",
                    "?channel.id=gt:0x00",
                    "?timestamp=ne:1",
                    "?timestamp=abc",
                    "?timestamp=gte:5&timestamp=lte:4",
                    "?timestamp=gte:1&timestamp=lte:2&timestamp=lt:3",
                    "?timestamp=gte:1&timestamp=lte:9999999999"
                })
        void invalidParameters(String query) {
            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @Test
        void pendingStatus() {
            final var open1 = persistPending(100L, false, null);
            persistPending(150L, false, 160L); // completed
            persistPending(175L, true, null); // deleted
            final var open2 = persistPending(200L, false, null);
            persist(300L, ClprChannelStatus.ACTIVE); // not pending

            final var actual =
                    restClient.get().uri("?status=PENDING").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels())
                    .containsExactlyElementsOf(pendingChannelMapper.map(List.of(open2, open1)))
                    .allSatisfy(c -> {
                        assertThat(c.getStatus()).isEqualTo(org.hiero.mirror.rest.model.ClprChannelStatus.PENDING);
                        assertThat(c.getChannelId()).isNull();
                        assertThat(c.getOwnershipCommitment()).isNotNull();
                    });
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void pendingStatusAscendingOrder() {
            final var open1 = persistPending(100L, false, null);
            final var open2 = persistPending(200L, false, null);

            final var actual =
                    restClient.get().uri("?status=PENDING&order=asc").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(pendingChannelMapper.map(List.of(open1, open2)));
        }

        @Test
        void pendingStatusTimestampRange() {
            persistPending(1_000_000_000L, false, null);
            final var open2 = persistPending(2_000_000_000L, false, null);
            persistPending(3_000_000_000L, false, null);

            final var actual = restClient
                    .get()
                    .uri("?status=PENDING&timestamp=gte:2.000000000&timestamp=lt:3")
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(pendingChannelMapper.map(List.of(open2)));
        }

        @Test
        void pendingStatusNextLink() {
            persistPending(1_000_000_000L, false, null);
            final var open2 = persistPending(2_000_000_000L, false, null);

            final var actual =
                    restClient.get().uri("?status=PENDING&limit=1").retrieve().body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).hasSize(1);
            assertThat(queryParams(actual.getLinks().getNext()))
                    .containsEntry("status", List.of("PENDING"))
                    .containsEntry("timestamp", List.of("lt:" + DomainUtils.toTimestamp(open2.getCreatedTimestamp())));
        }

        @Test
        void pendingStatusWithChannelIdIsRejected() {
            final var id = HexFormat.of().formatHex(new byte[32]);
            validateError(
                    () -> restClient
                            .get()
                            .uri("?status=PENDING&channel.id={id}", id)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "channel.id can't be combined with status PENDING");
        }

        private Map<String, List<String>> queryParams(String link) {
            return UriComponentsBuilder.fromUriString(link).build().getQueryParams();
        }

        private ClprChannelPendingCommitment persistPending(
                long createdTimestamp, boolean deleted, @Nullable Long completedTimestamp) {
            return domainBuilder
                    .clprChannelPendingCommitment()
                    .customize(c -> c.completedTimestamp(completedTimestamp)
                            .createdTimestamp(createdTimestamp)
                            .deleted(deleted))
                    .persist();
        }

        private ClprChannel persist(long createdTimestamp, ClprChannelStatus status) {
            return domainBuilder
                    .clprChannel()
                    .customize(c -> c.createdTimestamp(createdTimestamp).status(status))
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/channels/{channelId}/connectors")
    @Nested
    final class ConnectorsEndpointTest extends EndpointTest {

        private final byte[] channelId = domainBuilder.bytes(32);
        private final String channelIdHex = HexFormat.of().formatHex(channelId);

        @Override
        protected String getUrl() {
            return "clpr/channels/%s/connectors".formatted(channelIdHex);
        }

        // The inherited test looks up the configured headers by the literal url, which can't match the path template
        // key, so the configured cache control is verified in cacheControl() instead
        @Override
        @Test
        void headers() {}

        @Test
        void empty() {
            final var actual = restClient.get().uri("").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).isEmpty();
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void cacheControl() {
            final var response = restClient.get().uri("").retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
        }

        @Test
        void defaultOrderIsDescending() {
            final var connector1 = persist(100L);
            final var connector2 = persist(200L);

            final var actual = restClient.get().uri("").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors())
                    .containsExactlyElementsOf(connectorMapper.map(List.of(connector2, connector1)));
        }

        @Test
        void ascendingOrder() {
            final var connector1 = persist(100L);
            final var connector2 = persist(200L);

            final var actual = restClient.get().uri("?order=asc").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors())
                    .containsExactlyElementsOf(connectorMapper.map(List.of(connector1, connector2)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"%s", "0x%s", "0X%s"})
        void channelIdPathFormats(String format) {
            final var connector = persist(100L);
            persist(200L, domainBuilder.bytes(32)); // other channel

            final var actual = restClient
                    .get()
                    .uri(b -> b.replacePath("/api/v1/clpr/channels/{id}/connectors")
                            .build(format.formatted(channelIdHex)))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(connector)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"%s", "0x%s", "eq:%s", "eq:0x%s"})
        void connectorIdFilter(String format) {
            final var connector = persist(100L);
            persist(200L);
            final var id = format.formatted(HexFormat.of().formatHex(connector.getConnectorId()));

            final var actual =
                    restClient.get().uri("?connector.id={id}", id).retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(connector)));
        }

        @Test
        void includesDeleted() {
            persist(100L, c -> c.deleted(false));
            persist(200L, c -> c.deleted(true));

            final var actual = restClient.get().uri("").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).hasSize(2);
        }

        @Test
        void timestampRange() {
            persist(1_000_000_000L);
            final var connector2 = persist(2_000_000_000L);
            persist(3_000_000_000L);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gt:1.000000000&timestamp=lt:3.000000000")
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(connector2)));
        }

        @Test
        void connectorIdWithTimestampReturnsHistoricalState() {
            final var current = domainBuilder
                    .clprConnector()
                    .customize(c -> c.channelId(channelId).slashCount(2L).timestampRange(Range.atLeast(200L)))
                    .persist();
            domainBuilder
                    .clprConnectorHistory()
                    .customize(c -> c.channelId(channelId)
                            .connectorId(current.getConnectorId())
                            .slashCount(1L)
                            .timestampRange(Range.closedOpen(100L, 200L)))
                    .persist();

            final var actual = restClient
                    .get()
                    .uri(
                            "?connector.id={k}&timestamp=eq:0.000000150",
                            HexFormat.of().formatHex(current.getConnectorId()))
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors())
                    .singleElement()
                    .satisfies(c -> assertThat(c.getSlashCount()).isEqualTo(1L));
        }

        @Test
        void connectorIdWithTimestampRequiresEq() {
            final var id = HexFormat.of().formatHex(new byte[32]);
            validateError(
                    () -> restClient
                            .get()
                            .uri("?connector.id={id}&timestamp=gte:1", id)
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp must use the eq operator when filtering by connector.id");
        }

        @Test
        void timestampEq() {
            persist(1_000_000_000L);
            final var connector2 = persist(2_000_000_000L);

            final var actual =
                    restClient.get().uri("?timestamp=eq:2.000000000").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(connector2)));
        }

        @Test
        void limitAndNextLink() {
            persist(1_000_000_000L);
            final var connector2 = persist(2_000_000_000L);
            final var connector3 = persist(3_000_000_000L);

            final var actual = restClient.get().uri("?limit=2").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors())
                    .containsExactlyElementsOf(connectorMapper.map(List.of(connector3, connector2)));
            assertThat(actual.getLinks().getNext())
                    .isEqualTo("/api/v1/clpr/channels/%s/connectors?limit=2&timestamp=lt:%s"
                            .formatted(channelIdHex, DomainUtils.toTimestamp(connector2.getCreatedTimestamp())));
        }

        @Test
        void nextLinkKeepsFilters() {
            final var connector = persist(1_000_000_000L);
            final var connectorId = "0x" + HexFormat.of().formatHex(connector.getConnectorId());

            final var actual = restClient
                    .get()
                    .uri("?connector.id={id}&limit=1", connectorId)
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).hasSize(1);
            assertThat(UriComponentsBuilder.fromUriString(actual.getLinks().getNext())
                            .build()
                            .getQueryParams())
                    .containsEntry("connector.id", List.of(connectorId))
                    .containsEntry(
                            "timestamp", List.of("lt:" + DomainUtils.toTimestamp(connector.getCreatedTimestamp())));
        }

        @Test
        void timestampRangeExceedsMax() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=gte:1&timestamp=lte:99999999")
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "Timestamp range by the lower and upper bounds must not exceed PT720H");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?limit=0",
                    "?limit=101",
                    "?order=invalid",
                    "?connector.id=abc",
                    "?connector.id=lt:0x00",
                    "?timestamp=ne:1",
                    "?timestamp=abc",
                    "?timestamp=gte:5&timestamp=lte:4",
                    "?timestamp=eq:5&timestamp=eq:6",
                    "?timestamp=gte:1&timestamp=lte:2&timestamp=lt:3",
                    "?timestamp=gte:1&timestamp=lte:9999999999"
                })
        void invalidParameters(String query) {
            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "0x00", "gt:0x00"})
        void invalidChannelId(String id) {
            assertThatThrownBy(() -> restClient
                            .get()
                            .uri(b -> b.replacePath("/api/v1/clpr/channels/{id}/connectors")
                                    .build(id))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private ClprConnector persist(long createdTimestamp) {
            return persist(createdTimestamp, channelId);
        }

        private ClprConnector persist(long createdTimestamp, byte[] channel) {
            return domainBuilder
                    .clprConnector()
                    .customize(c -> c.channelId(channel).createdTimestamp(createdTimestamp))
                    .persist();
        }

        private ClprConnector persist(
                long createdTimestamp, Consumer<ClprConnector.ClprConnectorBuilder<?, ?>> customizer) {
            return domainBuilder
                    .clprConnector()
                    .customize(c -> {
                        c.channelId(channelId).createdTimestamp(createdTimestamp);
                        customizer.accept(c);
                    })
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/connectors/pending")
    @Nested
    final class PendingConnectorsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "clpr/connectors/pending";
        }

        @Test
        void empty() {
            final var actual = restClient.get().uri("").retrieve().body(ClprPendingConnectorsResponse.class);

            assertThat(actual.getPendingConnectors()).isEmpty();
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void cacheControl() {
            final var response = restClient.get().uri("").retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=5");
        }

        @Test
        void onlyOpenCommitments() {
            final var open1 = persist(100L, false, null);
            persist(150L, false, 160L); // completed
            persist(175L, true, null); // deleted
            final var open2 = persist(200L, false, null);

            final var actual = restClient.get().uri("").retrieve().body(ClprPendingConnectorsResponse.class);

            assertThat(actual.getPendingConnectors())
                    .containsExactlyElementsOf(pendingConnectorMapper.map(List.of(open2, open1)));
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void ascendingOrder() {
            final var open1 = persist(100L, false, null);
            final var open2 = persist(200L, false, null);

            final var actual = restClient.get().uri("?order=asc").retrieve().body(ClprPendingConnectorsResponse.class);

            assertThat(actual.getPendingConnectors())
                    .containsExactlyElementsOf(pendingConnectorMapper.map(List.of(open1, open2)));
        }

        @Test
        void timestampRange() {
            persist(1_000_000_000L, false, null);
            final var open2 = persist(2_000_000_000L, false, null);
            persist(3_000_000_000L, false, null);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gte:2.000000000&timestamp=lt:3")
                    .retrieve()
                    .body(ClprPendingConnectorsResponse.class);

            assertThat(actual.getPendingConnectors())
                    .containsExactlyElementsOf(pendingConnectorMapper.map(List.of(open2)));
        }

        @Test
        void nextLink() {
            persist(1_000_000_000L, false, null);
            final var open2 = persist(2_000_000_000L, false, null);

            final var actual = restClient.get().uri("?limit=1").retrieve().body(ClprPendingConnectorsResponse.class);

            assertThat(actual.getPendingConnectors()).hasSize(1);
            assertThat(queryParams(actual.getLinks().getNext()))
                    .containsEntry("timestamp", List.of("lt:" + DomainUtils.toTimestamp(open2.getCreatedTimestamp())));
        }

        @ParameterizedTest
        @ValueSource(strings = {"?limit=0", "?limit=101", "?order=foo", "?timestamp=abc"})
        void invalidParameter(String query) {
            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private Map<String, List<String>> queryParams(String link) {
            return UriComponentsBuilder.fromUriString(link).build().getQueryParams();
        }

        private ClprConnectorPendingCommitment persist(
                long createdTimestamp, boolean deleted, @Nullable Long completedTimestamp) {
            return domainBuilder
                    .clprConnectorPendingCommitment()
                    .customize(c -> c.completedTimestamp(completedTimestamp)
                            .createdTimestamp(createdTimestamp)
                            .deleted(deleted))
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/configuration")
    @Nested
    final class LedgerConfigurationEndpointTest extends EndpointTest {

        // The inherited tests need a row to exist since the endpoint returns 404 otherwise
        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persist(100L);
            return uriSpec.uri("");
        }

        @Override
        protected String getUrl() {
            return "clpr/configuration";
        }

        @Test
        void cacheControl() {
            persist(100L);

            final var response = restClient.get().uri("").retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=60");
        }

        @Test
        void latest() {
            persist(100L);
            final var latest = persist(200L);

            final var actual = restClient.get().uri("").retrieve().body(ClprLedgerConfiguration.class);

            assertThat(actual).isEqualTo(ledgerConfigurationMapper.map(latest));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.000000200", "eq:0.000000200", "lte:0.000000200", "lte:0.000000250"})
        void asOfTimestamp(String timestamp) {
            persist(100L);
            final var expected = persist(200L);
            persist(300L);

            final var actual = restClient
                    .get()
                    .uri("?timestamp={ts}", timestamp)
                    .retrieve()
                    .body(ClprLedgerConfiguration.class);

            assertThat(actual).isEqualTo(ledgerConfigurationMapper.map(expected));
        }

        @Test
        void notFound() {
            persist(100L);

            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=lte:0.000000099")
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.NotFound.class,
                    "No CLPR ledger configuration found");
        }

        @Test
        void notFoundWhenEmpty() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toBodilessEntity(),
                    HttpClientErrorException.NotFound.class,
                    "No CLPR ledger configuration found");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?timestamp=abc",
                    "?timestamp=gt:1",
                    "?timestamp=gte:1",
                    "?timestamp=lt:1",
                    "?timestamp=ne:1",
                    "?timestamp=lte:1&timestamp=lte:2"
                })
        void invalidParameter(String query) {
            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration persist(long consensusTimestamp) {
            return domainBuilder
                    .clprLedgerConfiguration()
                    .customize(r -> r.consensusTimestamp(consensusTimestamp))
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/endpoint-manifest")
    @Nested
    final class EndpointManifestEndpointTest extends EndpointTest {

        // The inherited tests need a row to exist since the endpoint returns 404 otherwise
        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persist(100L);
            return uriSpec.uri("");
        }

        @Override
        protected String getUrl() {
            return "clpr/endpoint-manifest";
        }

        @Test
        void cacheControl() {
            persist(100L);

            final var response = restClient.get().uri("").retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=60");
        }

        @Test
        void latest() {
            persist(100L);
            final var latest = persist(200L);

            final var actual = restClient.get().uri("").retrieve().body(ClprEndpointManifestEntry.class);

            assertThat(actual).isEqualTo(endpointManifestMapper.map(latest));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.000000200", "eq:0.000000200", "lte:0.000000200", "lte:0.000000250"})
        void asOfTimestamp(String timestamp) {
            persist(100L);
            final var expected = persist(200L);
            persist(300L);

            final var actual = restClient
                    .get()
                    .uri("?timestamp={ts}", timestamp)
                    .retrieve()
                    .body(ClprEndpointManifestEntry.class);

            assertThat(actual).isEqualTo(endpointManifestMapper.map(expected));
        }

        @Test
        void notFound() {
            persist(100L);

            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=lte:0.000000099")
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.NotFound.class,
                    "No CLPR endpoint manifest found");
        }

        @Test
        void notFoundWhenEmpty() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toBodilessEntity(),
                    HttpClientErrorException.NotFound.class,
                    "No CLPR endpoint manifest found");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?timestamp=abc",
                    "?timestamp=gt:1",
                    "?timestamp=gte:1",
                    "?timestamp=lt:1",
                    "?timestamp=ne:1",
                    "?timestamp=lte:1&timestamp=lte:2"
                })
        void invalidParameter(String query) {
            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private org.hiero.mirror.common.domain.clpr.ClprEndpointManifest persist(long consensusTimestamp) {
            return domainBuilder
                    .clprEndpointManifest()
                    .customize(r -> r.consensusTimestamp(consensusTimestamp))
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/channels/{channelId}/messages")
    @Nested
    final class MessagesEndpointTest extends EndpointTest {

        // consensus timestamps in these tests are small, so use a lower bound of one nanosecond
        private static final String TIMESTAMP_FILTER = "timestamp=gte:0.000000001";

        private final byte[] channelId = new byte[32];

        @Override
        protected String getUrl() {
            return "clpr/channels/%s/messages".formatted("00".repeat(32));
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            persistChannel(0L);
            return uriSpec.uri("?" + TIMESTAMP_FILTER);
        }

        @Test
        void empty() {
            persistChannel(0L);

            final var actual =
                    restClient.get().uri("?" + TIMESTAMP_FILTER).retrieve().body(ClprMessagesResponse.class);

            assertThat(actual.getMessages()).isEmpty();
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void cacheControl() {
            persistChannel(0L);
            final var response =
                    restClient.get().uri("?" + TIMESTAMP_FILTER).retrieve().toBodilessEntity();

            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
        }

        @Test
        void unknownChannelReturnsEmpty() {
            persist(100L, 1L);

            final var actual = restClient
                    .get()
                    .uri(b -> b.replacePath("/api/v1/clpr/channels/{id}/messages")
                            .query(TIMESTAMP_FILTER)
                            .build("cd".repeat(32)))
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(actual.getMessages()).isEmpty();
        }

        @Test
        void defaultOrderIsDescending() {
            persistChannel(0L);
            final var message1 = persist(100L, 1L);
            final var message2 = persist(150L, 2L);
            final var message3 = persist(200L, 3L);

            final var actual =
                    restClient.get().uri("?" + TIMESTAMP_FILTER).retrieve().body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages()))
                    .containsExactly(message3.getMessageId(), message2.getMessageId(), message1.getMessageId());
        }

        @Test
        void ascendingOrder() {
            persistChannel(0L);
            final var message1 = persist(100L, 1L);
            final var message2 = persist(200L, 2L);

            final var actual = restClient
                    .get()
                    .uri("?order=asc&" + TIMESTAMP_FILTER)
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages())).containsExactly(message1.getMessageId(), message2.getMessageId());
        }

        @Test
        void acknowledged() {
            persistChannel(5L);
            persist(100L, 4L);
            persist(200L, 5L);
            persist(300L, 6L);

            final var actual = restClient
                    .get()
                    .uri("?order=asc&" + TIMESTAMP_FILTER)
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(actual.getMessages())
                    .extracting(m -> m.getMessageId(), m -> m.getAcknowledged())
                    .containsExactly(tuple(4L, true), tuple(5L, true), tuple(6L, false));
        }

        @Test
        void messageDataIsNotPadded() {
            persistChannel(0L);
            final var message = domainBuilder
                    .clprMessage()
                    .customize(m -> m.channelId(channelId).messageData(new byte[] {(byte) 0xde, (byte) 0xad}))
                    .persist();

            final var actual =
                    restClient.get().uri("?" + TIMESTAMP_FILTER).retrieve().body(ClprMessagesResponse.class);

            assertThat(actual.getMessages()).singleElement().satisfies(m -> {
                assertThat(m.getMessageId()).isEqualTo(message.getMessageId());
                assertThat(m.getMessageData()).isEqualTo("0xdead");
            });
        }

        @Test
        void otherChannelsAreExcluded() {
            persistChannel(0L);
            final var message = persist(100L, 1L);
            persistFor(domainBuilder.bytes(32), 200L, 2L);

            final var actual =
                    restClient.get().uri("?" + TIMESTAMP_FILTER).retrieve().body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages())).containsExactly(message.getMessageId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"%s", "0x%s", "eq:%s", "eq:0x%s"})
        void connectorIdFilter(String format) {
            persistChannel(0L);
            final var connectorId = domainBuilder.bytes(32);
            final var message = persistFor(channelId, connectorId, 100L, 1L, ClprMessageType.DATA);
            persist(200L, 2L); // other connector
            final var id = format.formatted(HexFormat.of().formatHex(connectorId));

            final var actual = restClient
                    .get()
                    .uri("?connector.id={id}&" + TIMESTAMP_FILTER, id)
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages())).containsExactly(message.getMessageId());
        }

        @Test
        void typeFilterWithTimestamp() {
            persistChannel(0L);
            persist(1_000_000_000L, 1L); // DATA
            final var control = persistFor(channelId, null, 2_000_000_000L, 2L, ClprMessageType.CONTROL);

            final var actual = restClient
                    .get()
                    .uri("?type=CONTROL&timestamp=gte:1")
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages())).containsExactly(control.getMessageId());
        }

        @Test
        void timestampRange() {
            persistChannel(0L);
            persist(1_000_000_000L, 1L);
            final var message2 = persist(2_000_000_000L, 2L);
            persist(3_000_000_000L, 3L);

            final var actual = restClient
                    .get()
                    .uri("?timestamp=gt:1.000000000&timestamp=lt:3.000000000")
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(ids(actual.getMessages())).containsExactly(message2.getMessageId());
        }

        @Test
        void limitAndNextLink() {
            persistChannel(0L);
            persist(1_000_000_000L, 1L);
            final var message2 = persist(2_000_000_000L, 2L);
            persist(3_000_000_000L, 3L);

            final var actual = restClient
                    .get()
                    .uri("?limit=2&" + TIMESTAMP_FILTER)
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            assertThat(actual.getMessages()).hasSize(2);
            final var params = queryParams(actual.getLinks().getNext());
            assertThat(params).containsEntry("limit", List.of("2"));
            assertThat(params.get("timestamp"))
                    .contains("lt:" + DomainUtils.toTimestamp(message2.getConsensusTimestamp()));
            assertThat(params).doesNotContainKey("message.id");
        }

        @Test
        void nextLinkAscending() {
            persistChannel(0L);
            persist(1_000_000_000L, 1L);
            final var message2 = persist(2_000_000_000L, 2L);
            persist(3_000_000_000L, 3L);

            final var actual = restClient
                    .get()
                    .uri("?limit=2&order=asc&" + TIMESTAMP_FILTER)
                    .retrieve()
                    .body(ClprMessagesResponse.class);

            final var params = queryParams(actual.getLinks().getNext());
            assertThat(params).containsEntry("order", List.of("asc"));
            assertThat(params.get("timestamp"))
                    .contains("gt:" + DomainUtils.toTimestamp(message2.getConsensusTimestamp()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "?type=DATA", "?limit=5&order=asc"})
        void timestampRequired(String query) {
            persistChannel(0L);

            validateError(
                    () -> restClient.get().uri(query).retrieve().toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp is required");
        }

        @Test
        void connectorIdRequiresTimestamp() {
            persistChannel(0L);

            validateError(
                    () -> restClient
                            .get()
                            .uri("?connector.id={id}", "ab".repeat(32))
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp is required");
        }

        @Test
        void timestampRangeExceedsMax() {
            persistChannel(0L);

            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=gte:1&timestamp=lte:2592002")
                            .retrieve()
                            .toBodilessEntity(),
                    HttpClientErrorException.BadRequest.class,
                    "Timestamp range by the lower and upper bounds must not exceed PT720H");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?limit=0",
                    "?limit=101",
                    "?order=invalid",
                    "?type=INVALID&timestamp=gte:1",
                    "?connector.id=abc",
                    "?connector.id=gt:0x00",
                    "?timestamp=ne:1",
                    "?timestamp=abc",
                    "?timestamp=gte:5&timestamp=lte:4",
                    "?timestamp=gte:1&timestamp=lte:2&timestamp=lt:3"
                })
        void invalidParameters(String query) {
            persistChannel(0L);

            assertThatThrownBy(() -> restClient.get().uri(query).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "0x00", "eq:00000000000000000000000000000000000000000000000000000000000000ab"})
        void invalidChannelId(String id) {
            assertThatThrownBy(() -> restClient
                            .get()
                            .uri(b -> b.replacePath("/api/v1/clpr/channels/{id}/messages")
                                    .build(id))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        private List<Long> ids(List<org.hiero.mirror.rest.model.ClprMessage> messages) {
            return messages.stream()
                    .map(org.hiero.mirror.rest.model.ClprMessage::getMessageId)
                    .toList();
        }

        private ClprMessage persist(long consensusTimestamp, long messageId) {
            return persistFor(channelId, consensusTimestamp, messageId);
        }

        private void persistChannel(long ackedMessageId) {
            domainBuilder
                    .clprChannel()
                    .customize(c -> c.ackedMessageId(ackedMessageId).channelId(channelId))
                    .persist();
        }

        private ClprMessage persistFor(byte[] channel, long consensusTimestamp, long messageId) {
            return persistFor(channel, domainBuilder.bytes(32), consensusTimestamp, messageId, ClprMessageType.DATA);
        }

        private ClprMessage persistFor(
                byte[] channel,
                byte @Nullable [] connectorId,
                long consensusTimestamp,
                long messageId,
                ClprMessageType type) {
            return domainBuilder
                    .clprMessage()
                    .customize(m -> m.channelId(channel)
                            .connectorId(connectorId)
                            .consensusTimestamp(consensusTimestamp)
                            .messageId(messageId)
                            .messageType(type))
                    .persist();
        }

        private Map<String, List<String>> queryParams(String link) {
            return UriComponentsBuilder.fromUriString(link).build().getQueryParams();
        }
    }
}
