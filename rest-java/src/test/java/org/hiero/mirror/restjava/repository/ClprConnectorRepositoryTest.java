// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprConnectorRepositoryTest extends RestJavaIntegrationTest {

    private static final String CREATED_TIMESTAMP = "created_timestamp";

    private final ClprConnectorRepository repository;

    @Test
    void findByChannelIdAndConnectorId() {
        // given
        final var connector = persistConnector(domainBuilder.bytes(32), 100L, ClprConnectorStatus.ACTIVE);
        persistConnector(connector.getChannelId(), 200L, ClprConnectorStatus.ACTIVE);

        // when
        final var actual =
                repository.findByChannelIdAndConnectorId(connector.getChannelId(), connector.getConnectorId());

        // then
        assertThat(actual).isEqualTo(connector);
    }

    @Test
    void findByChannelIdAndConnectorIdNotFound() {
        // given
        final var connector = persistConnector(domainBuilder.bytes(32), 100L, ClprConnectorStatus.ACTIVE);

        // when / then
        assertThat(repository.findByChannelIdAndConnectorId(domainBuilder.bytes(32), connector.getConnectorId()))
                .isNull();
    }

    @Test
    void findByChannelIdAndConnectorIdAndTimestampBetweenSelectsVersionInEffect() {
        // given: ACTIVE in [100, 200), then DELETED from 200
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);
        final var commitment = domainBuilder.bytes(32);
        persistHistory(channelId, commitment, connectorId, ClprConnectorStatus.ACTIVE, Range.closedOpen(100L, 200L));
        persistConnector(channelId, commitment, connectorId, 100L, ClprConnectorStatus.DELETED, Range.atLeast(200L));

        // when / then
        assertStatus(channelId, connectorId, 150L, ClprConnectorStatus.ACTIVE);
        assertStatus(channelId, connectorId, 200L, ClprConnectorStatus.DELETED);
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        channelId, connectorId, 150L, 150L, "DELETED"))
                .isEmpty();
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestampBetween(channelId, connectorId, 0L, 99L, null))
                .isEmpty();
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        channelId, connectorId, 0L, Long.MAX_VALUE, null))
                .extracting(ClprConnector::getStatus)
                .containsExactly(ClprConnectorStatus.DELETED);
    }

    @Test
    void findByChannelIdAndCreatedTimestampBetween() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var active = persistConnector(channelId, 100L, ClprConnectorStatus.ACTIVE);
        final var pending = persistPending(channelId, 200L);
        final var deleted = persistConnector(channelId, 300L, ClprConnectorStatus.DELETED);
        persistConnector(domainBuilder.bytes(32), 150L, ClprConnectorStatus.ACTIVE); // another channel
        final var desc = PageRequest.of(0, 25, Sort.by(Direction.DESC, CREATED_TIMESTAMP));
        final var asc = PageRequest.of(0, 2, Sort.by(Direction.ASC, CREATED_TIMESTAMP));

        // when / then
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(channelId, 0L, Long.MAX_VALUE, null, desc))
                .containsExactly(deleted, pending, active);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(channelId, 0L, Long.MAX_VALUE, null, asc))
                .containsExactly(active, pending);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(channelId, 150L, 250L, null, desc))
                .containsExactly(pending);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(channelId, 0L, Long.MAX_VALUE, "PENDING", desc))
                .containsExactly(pending);
        assertThat(repository.findByChannelIdAndCreatedTimestampBetween(channelId, 0L, Long.MAX_VALUE, "ACTIVE", desc))
                .containsExactly(active);
    }

    private void assertStatus(byte[] channelId, byte[] connectorId, long timestamp, ClprConnectorStatus expected) {
        final List<ClprConnector> actual = repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                channelId, connectorId, timestamp, timestamp, null);
        assertThat(actual).extracting(ClprConnector::getStatus).containsExactly(expected);
    }

    private ClprConnector persistConnector(byte[] channelId, long createdTimestamp, ClprConnectorStatus status) {
        return persistConnector(
                channelId,
                domainBuilder.bytes(32),
                domainBuilder.bytes(32),
                createdTimestamp,
                status,
                Range.atLeast(createdTimestamp));
    }

    private ClprConnector persistConnector(
            byte[] channelId,
            byte[] commitment,
            byte[] connectorId,
            long createdTimestamp,
            ClprConnectorStatus status,
            Range<Long> timestampRange) {
        final var builder = ClprConnector.builder()
                .channelId(channelId)
                .commitment(commitment)
                .connectorId(connectorId)
                .createdTimestamp(createdTimestamp)
                .inFlightMessageCount(0L)
                .lockedStake(100L)
                .slashCount(0L)
                .status(status)
                .timestampRange(timestampRange);
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    private ClprConnector persistPending(byte[] channelId, long createdTimestamp) {
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

    private void persistHistory(
            byte[] channelId,
            byte[] commitment,
            byte[] connectorId,
            ClprConnectorStatus status,
            Range<Long> timestampRange) {
        final var builder = ClprConnectorHistory.builder()
                .channelId(channelId)
                .commitment(commitment)
                .connectorId(connectorId)
                .createdTimestamp(100L)
                .inFlightMessageCount(0L)
                .lockedStake(100L)
                .slashCount(0L)
                .status(status)
                .timestampRange(timestampRange);
        domainBuilder.wrap(builder, builder::build).persist();
    }
}
