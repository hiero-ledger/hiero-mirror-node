// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprChannel.CLPR_CHANNEL;

import com.google.common.collect.Range;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprChannelRepositoryTest extends RestJavaIntegrationTest {

    private final ClprChannelRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByChannelIdAndCreatedTimestampBetween(Direction order) {
        // given
        final var channel = persist(200L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.ACTIVE); // other channel

        // when / then
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(
                        channel.getChannelId(), 0L, Long.MAX_VALUE, page(order, 10)))
                .containsExactly(channel);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(
                        channel.getChannelId(), 200L, 200L, page(order, 10)))
                .containsExactly(channel);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(
                        channel.getChannelId(), 201L, Long.MAX_VALUE, page(order, 10)))
                .isEmpty();
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(
                        channel.getChannelId(), 0L, 199L, page(order, 10)))
                .isEmpty();
    }

    @Test
    void findByChannelIdAndCreatedTimestampBetweenNotFound() {
        persist(200L, ClprChannelStatus.ACTIVE);

        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(
                        domainBuilder.bytes(32), 0L, Long.MAX_VALUE, page(Direction.ASC, 10)))
                .isEmpty();
    }

    @Test
    void findByChannelIdAndCreatedTimestampBetweenAndStatus() {
        // given
        final var channel = persist(200L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.ACTIVE); // other channel, same status

        // when / then
        assertThat(repository.findByChannelIdAndCreatedTimestampBetweenAndStatus(
                        channel.getChannelId(), 0L, Long.MAX_VALUE, "ACTIVE", page(Direction.ASC, 10)))
                .containsExactly(channel);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetweenAndStatus(
                        channel.getChannelId(), 0L, Long.MAX_VALUE, "CLOSED", page(Direction.ASC, 10)))
                .isEmpty();
        assertThat(repository.findByChannelIdAndCreatedTimestampBetweenAndStatus(
                        channel.getChannelId(), 201L, Long.MAX_VALUE, "ACTIVE", page(Direction.ASC, 10)))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByCreatedTimestampBetween(Direction order) {
        // given
        persist(100L, ClprChannelStatus.ACTIVE); // out of range
        final var channel1 = persist(200L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(300L, ClprChannelStatus.CLOSED);
        final var channel3 = persist(400L, ClprChannelStatus.PAUSED);
        persist(500L, ClprChannelStatus.ACTIVE); // out of range
        final var expected =
                order.isAscending() ? List.of(channel1, channel2, channel3) : List.of(channel3, channel2, channel1);

        // when
        final var actual = repository.findByCreatedTimestampBetween(200L, 400L, page(order, 10));

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findByCreatedTimestampBetweenInclusiveBounds() {
        // given
        final var channel = persist(200L, ClprChannelStatus.ACTIVE);

        // when / then
        assertThat(repository.findByCreatedTimestampBetween(200L, 200L, page(Direction.ASC, 10)))
                .containsExactly(channel);
        assertThat(repository.findByCreatedTimestampBetween(201L, 300L, page(Direction.ASC, 10)))
                .isEmpty();
    }

    @Test
    void findByCreatedTimestampBetweenLimit() {
        // given
        persist(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(200L, ClprChannelStatus.ACTIVE);
        final var channel3 = persist(300L, ClprChannelStatus.ACTIVE);

        // when
        final var actual = repository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, page(Direction.DESC, 2));

        // then
        assertThat(actual).containsExactly(channel3, channel2);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByCreatedTimestampBetweenAndStatus(Direction order) {
        // given
        final var active1 = persist(100L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.CLOSED);
        final var active2 = persist(300L, ClprChannelStatus.ACTIVE);
        persist(400L, ClprChannelStatus.DRAINED);
        persist(500L, ClprChannelStatus.ACTIVE); // out of range
        final var expected = order.isAscending() ? List.of(active1, active2) : List.of(active2, active1);

        // when
        final var actual = repository.findByCreatedTimestampBetweenAndStatus(0L, 400L, "ACTIVE", page(order, 10));

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findByChannelIdAndTimestamp() {
        // given
        final var current = domainBuilder
                .clprChannel()
                .customize(c -> c.status(ClprChannelStatus.CLOSED).timestampRange(Range.atLeast(200L)))
                .persist();
        domainBuilder
                .clprChannelHistory()
                .customize(c -> c.channelId(current.getChannelId())
                        .status(ClprChannelStatus.ACTIVE)
                        .timestampRange(Range.closedOpen(100L, 200L)))
                .persist();
        persist(150L, ClprChannelStatus.ACTIVE); // other channel

        // when / then
        final var id = current.getChannelId();
        assertThat(repository.findByChannelIdAndTimestamp(id, 150L, null))
                .extracting(ClprChannel::getStatus)
                .containsExactly(ClprChannelStatus.ACTIVE);
        assertThat(repository.findByChannelIdAndTimestamp(id, 200L, "CLOSED")).containsExactly(current);
        assertThat(repository.findByChannelIdAndTimestamp(id, 200L, "ACTIVE")).isEmpty();
        assertThat(repository.findByChannelIdAndTimestamp(id, 99L, null)).isEmpty();
    }

    @Test
    void findById() {
        // given
        final var channel = persist(100L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.ACTIVE);

        // when / then
        assertThat(repository.findById(channel.getChannelId())).contains(channel);
    }

    private PageRequest page(Direction order, int limit) {
        return PageRequest.of(0, limit, Sort.by(order, CLPR_CHANNEL.CREATED_TIMESTAMP.getName()));
    }

    private ClprChannel persist(long createdTimestamp, ClprChannelStatus status) {
        return domainBuilder
                .clprChannel()
                .customize(c -> c.createdTimestamp(createdTimestamp).status(status))
                .persist();
    }
}
