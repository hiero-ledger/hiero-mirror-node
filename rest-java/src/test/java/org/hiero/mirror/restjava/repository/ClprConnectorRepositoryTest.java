// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprConnectorRepositoryTest extends RestJavaIntegrationTest {

    private final ClprConnectorRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findAllOrder(Direction order) {
        // given
        final var connector1 = persist(100L);
        final var connector2 = persist(200L);
        final var connector3 = persist(300L);
        final var expected = order.isAscending()
                ? List.of(connector1, connector2, connector3)
                : List.of(connector3, connector2, connector1);

        // when
        final var actual =
                repository.findAll(ClprConnectorsRequest.builder().order(order).build());

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findAllLimit() {
        // given
        persist(100L);
        final var connector2 = persist(200L);
        final var connector3 = persist(300L);

        // when
        final var actual =
                repository.findAll(ClprConnectorsRequest.builder().limit(2).build());

        // then
        assertThat(actual).containsExactly(connector3, connector2);
    }

    @Test
    void findAllTimestampRangeIsInclusive() {
        // given
        persist(100L);
        final var connector2 = persist(200L);
        final var connector3 = persist(300L);
        persist(400L);
        final var request = ClprConnectorsRequest.builder()
                .lowerBound(200L)
                .upperBound(300L)
                .build();

        // when / then
        assertThat(repository.findAll(request)).containsExactly(connector3, connector2);
    }

    @Test
    void findAllByChannelId() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connector1 = persist(100L, c -> c.channelId(channelId));
        final var connector2 = persist(200L, c -> c.channelId(channelId));
        persist(300L); // other channel
        final var request = ClprConnectorsRequest.builder().channelId(channelId).build();

        // when / then
        assertThat(repository.findAll(request)).containsExactly(connector2, connector1);
    }

    @Test
    void findAllByConnectorId() {
        // given
        final var connector = persist(100L);
        persist(200L);
        final var request = ClprConnectorsRequest.builder()
                .connectorId(connector.getConnectorId())
                .build();

        // when / then
        assertThat(repository.findAll(request)).containsExactly(connector);
    }

    @Test
    void findAllByChannelIdAndConnectorId() {
        // given
        final var connector = persist(100L);
        final var matching = ClprConnectorsRequest.builder()
                .channelId(connector.getChannelId())
                .connectorId(connector.getConnectorId())
                .build();
        final var wrongChannel = ClprConnectorsRequest.builder()
                .channelId(domainBuilder.bytes(32))
                .connectorId(connector.getConnectorId())
                .build();

        // when / then
        assertThat(repository.findAll(matching)).containsExactly(connector);
        assertThat(repository.findAll(wrongChannel)).isEmpty();
    }

    @Test
    void findAllByDeleted() {
        // given
        final var active = persist(100L, c -> c.deleted(false));
        final var neverSet = persist(200L, c -> c.deleted(null));
        final var removed = persist(300L, c -> c.deleted(true));

        // when / then
        assertThat(repository.findAll(
                        ClprConnectorsRequest.builder().deleted(true).build()))
                .containsExactly(removed);
        assertThat(repository.findAll(
                        ClprConnectorsRequest.builder().deleted(false).build()))
                .containsExactly(neverSet, active);
        assertThat(repository.findAll(ClprConnectorsRequest.builder().build()))
                .containsExactly(removed, neverSet, active);
    }

    @Test
    void findAllCombinedFilters() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var target = persist(200L, c -> c.channelId(channelId).deleted(true));
        persist(100L, c -> c.channelId(channelId).deleted(true)); // out of range
        persist(200L, c -> c.channelId(channelId).deleted(false)); // not deleted
        persist(200L, c -> c.deleted(true)); // other channel
        final var request = ClprConnectorsRequest.builder()
                .channelId(channelId)
                .deleted(true)
                .lowerBound(150L)
                .upperBound(250L)
                .build();

        // when / then
        assertThat(repository.findAll(request)).containsExactly(target);
    }

    @Test
    void findAllEmpty() {
        assertThat(repository.findAll(ClprConnectorsRequest.builder().build())).isEmpty();
    }

    @Test
    void findById() {
        // given
        final var connector = persist(100L);
        persist(200L);

        // when / then
        assertThat(repository.findById(
                        new AbstractClprConnector.Id(connector.getChannelId(), connector.getConnectorId())))
                .contains(connector);
    }

    private ClprConnector persist(long createdTimestamp) {
        return persist(createdTimestamp, c -> {});
    }

    private ClprConnector persist(
            long createdTimestamp, java.util.function.Consumer<ClprConnector.ClprConnectorBuilder<?, ?>> customizer) {
        return domainBuilder
                .clprConnector()
                .customize(c -> {
                    c.createdTimestamp(createdTimestamp);
                    customizer.accept(c);
                })
                .persist();
    }
}
