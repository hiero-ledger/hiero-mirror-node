// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprConnector.CLPR_CONNECTOR;

import com.google.common.collect.Range;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprConnectorRepositoryTest extends RestJavaIntegrationTest {

    private final ClprConnectorRepository repository;

    @Test
    void findByChannelIdAndConnectorIdAndTimestamp() {
        // given
        final var current = domainBuilder
                .clprConnector()
                .customize(c -> c.deleted(true).timestampRange(Range.atLeast(200L)))
                .persist();
        domainBuilder
                .clprConnectorHistory()
                .customize(c -> c.channelId(current.getChannelId())
                        .connectorId(current.getConnectorId())
                        .deleted(false)
                        .timestampRange(Range.closedOpen(100L, 200L)))
                .persist();
        final var channelId = current.getChannelId();
        final var connectorId = current.getConnectorId();

        // when / then
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestamp(channelId, connectorId, 150L))
                .extracting(ClprConnector::getDeleted)
                .containsExactly(false);
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestamp(channelId, connectorId, 200L))
                .containsExactly(current);
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestamp(channelId, connectorId, 99L))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByChannelId(Direction order) {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connector1 = persist(100L, c -> c.channelId(channelId));
        final var connector2 = persist(200L, c -> c.channelId(channelId));
        persist(300L); // other channel
        final var expected = order.isAscending() ? List.of(connector1, connector2) : List.of(connector2, connector1);

        // when
        final var actual = repository.findByChannelId(channelId, 0L, Long.MAX_VALUE, null, page(order, 10));

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findByChannelIdTimestampRangeIsInclusive() {
        // given
        final var channelId = domainBuilder.bytes(32);
        persist(100L, c -> c.channelId(channelId));
        final var connector2 = persist(200L, c -> c.channelId(channelId));
        final var connector3 = persist(300L, c -> c.channelId(channelId));
        persist(400L, c -> c.channelId(channelId));

        // when
        final var actual = repository.findByChannelId(channelId, 200L, 300L, null, page(Direction.DESC, 10));

        // then
        assertThat(actual).containsExactly(connector3, connector2);
    }

    @Test
    void findByChannelIdAndConnectorId() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connector = persist(100L, c -> c.channelId(channelId));
        persist(200L, c -> c.channelId(channelId)); // other connector
        persist(300L, c -> c.connectorId(connector.getConnectorId())); // other channel

        // when
        final var actual = repository.findByChannelId(
                channelId, 0L, Long.MAX_VALUE, connector.getConnectorId(), page(Direction.DESC, 10));

        // then
        assertThat(actual).containsExactly(connector);
    }

    @Test
    void findByChannelIdEmpty() {
        assertThat(repository.findByChannelId(new byte[32], 0L, Long.MAX_VALUE, null, page(Direction.DESC, 10)))
                .isEmpty();
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

    private PageRequest page(Direction order, int limit) {
        return PageRequest.of(0, limit, Sort.by(order, CLPR_CONNECTOR.CREATED_TIMESTAMP.getName()));
    }

    private ClprConnector persist(long createdTimestamp) {
        return persist(createdTimestamp, c -> {});
    }

    private ClprConnector persist(
            long createdTimestamp, Consumer<ClprConnector.ClprConnectorBuilder<?, ?>> customizer) {
        return domainBuilder
                .clprConnector()
                .customize(c -> {
                    c.createdTimestamp(createdTimestamp);
                    customizer.accept(c);
                })
                .persist();
    }
}
