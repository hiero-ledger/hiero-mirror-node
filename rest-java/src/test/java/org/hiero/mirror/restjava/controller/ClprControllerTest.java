// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ClprChannelsResponse;
import org.hiero.mirror.rest.model.ClprConnectorsResponse;
import org.hiero.mirror.restjava.mapper.ClprChannelMapper;
import org.hiero.mirror.restjava.mapper.ClprConnectorMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
final class ClprControllerTest extends ControllerTest {

    private final ClprConnectorMapper connectorMapper;
    private final ClprChannelMapper mapper;

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
        void channelIdAllowsRangeWiderThanMax() {
            final var channel = persist(2_000_000_000L, ClprChannelStatus.ACTIVE);
            final var id = HexFormat.of().formatHex(channel.getChannelId());

            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&timestamp=gte:1&timestamp=lte:99999999", id)
                    .retrieve()
                    .body(ClprChannelsResponse.class);

            assertThat(actual.getChannels()).containsExactlyElementsOf(mapper.map(List.of(channel)));
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

        private Map<String, List<String>> queryParams(String link) {
            return UriComponentsBuilder.fromUriString(link).build().getQueryParams();
        }

        private ClprChannel persist(long createdTimestamp, ClprChannelStatus status) {
            return domainBuilder
                    .clprChannel()
                    .customize(c -> c.createdTimestamp(createdTimestamp).status(status))
                    .persist();
        }
    }

    @DisplayName("GET /api/v1/clpr/connectors")
    @Nested
    final class ConnectorsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "clpr/connectors";
        }

        @Test
        void empty() {
            final var actual = restClient.get().uri("").retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).isEmpty();
            assertThat(actual.getLinks().getNext()).isNull();
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
        @ValueSource(strings = {"%s", "0x%s", "eq:%s", "eq:0x%s"})
        void channelIdFilter(String format) {
            final var connector = persist(100L);
            persist(200L);
            final var id = format.formatted(HexFormat.of().formatHex(connector.getChannelId()));

            final var actual =
                    restClient.get().uri("?channel.id={id}", id).retrieve().body(ClprConnectorsResponse.class);

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

        @ParameterizedTest
        @ValueSource(strings = {"true", "TRUE"})
        void deletedTrue(String value) {
            persist(100L);
            final var removed = persistDeleted(200L, true);

            final var actual =
                    restClient.get().uri("?deleted={value}", value).retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(removed)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "FALSE"})
        void deletedFalse(String value) {
            final var active = persist(100L);
            persistDeleted(200L, true);

            final var actual =
                    restClient.get().uri("?deleted={value}", value).retrieve().body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(active)));
        }

        @Test
        void deletedOmittedReturnsBoth() {
            persist(100L);
            persistDeleted(200L, true);

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
                    .isEqualTo("/api/v1/clpr/connectors?limit=2&timestamp=lt:%s"
                            .formatted(DomainUtils.toTimestamp(connector2.getCreatedTimestamp())));
        }

        @Test
        void nextLinkKeepsFilters() {
            final var connector = persist(1_000_000_000L);
            final var channelId = "0x" + HexFormat.of().formatHex(connector.getChannelId());

            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&deleted=false&limit=1", channelId)
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).hasSize(1);
            assertThat(UriComponentsBuilder.fromUriString(actual.getLinks().getNext())
                            .build()
                            .getQueryParams())
                    .containsEntry("channel.id", List.of(channelId))
                    .containsEntry("deleted", List.of("false"))
                    .containsEntry(
                            "timestamp", List.of("lt:" + DomainUtils.toTimestamp(connector.getCreatedTimestamp())));
        }

        @Test
        void channelIdAllowsRangeWiderThanMax() {
            final var connector = persist(2_000_000_000L);
            final var id = HexFormat.of().formatHex(connector.getChannelId());

            final var actual = restClient
                    .get()
                    .uri("?channel.id={id}&timestamp=gte:1&timestamp=lte:99999999", id)
                    .retrieve()
                    .body(ClprConnectorsResponse.class);

            assertThat(actual.getConnectors()).containsExactlyElementsOf(connectorMapper.map(List.of(connector)));
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

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "?limit=0",
                    "?limit=101",
                    "?order=invalid",
                    "?channel.id=abc",
                    "?channel.id=gt:0x00",
                    "?connector.id=abc",
                    "?connector.id=lt:0x00",
                    "?deleted=maybe",
                    "?deleted=eq:true",
                    "?deleted=ne:true",
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

        private ClprConnector persist(long createdTimestamp) {
            return persistDeleted(createdTimestamp, false);
        }

        private ClprConnector persistDeleted(long createdTimestamp, boolean deleted) {
            return domainBuilder
                    .clprConnector()
                    .customize(c -> c.createdTimestamp(createdTimestamp).deleted(deleted))
                    .persist();
        }
    }
}
