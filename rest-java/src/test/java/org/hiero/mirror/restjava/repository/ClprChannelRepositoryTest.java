// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprChannel.AbstractClprChannelBuilder;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelData;
import org.hiero.mirror.common.domain.clpr.ClprChannelHistory;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprChannelRepositoryTest extends RestJavaIntegrationTest {

    private static final String CREATED_TIMESTAMP = "created_timestamp";
    private static final int LIMIT = 2;

    private final ClprChannelRepository repository;

    @Test
    void findByChannelIdAndTimestampBetweenCurrentOnly() {
        // given
        final var channel = persistChannel(100L, ClprChannelStatus.ACTIVE);
        persistChannel(100L, ClprChannelStatus.ACTIVE); // another channel with the same timestamps

        // when
        final var actual = repository.findByChannelIdAndTimestampBetween(channel.getChannelId(), 100L, 100L, null);

        // then
        assertThat(ids(actual)).containsExactly(channel.getChannelId());
    }

    @Test
    void findByChannelIdAndTimestampBetweenSelectsVersionInEffect() {
        // given: ACTIVE in [100, 200), PAUSED in [200, 300), CLOSING from 300
        final var channelId = domainBuilder.bytes(32);
        persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
        persistHistory(channelId, ClprChannelStatus.PAUSED, Range.closedOpen(200L, 300L));
        persistChannel(channelId, 100L, ClprChannelStatus.CLOSING, Range.atLeast(300L));

        // when / then
        assertStatuses(channelId, 150L, 150L, null, ClprChannelStatus.ACTIVE);
        assertStatuses(channelId, 250L, 250L, null, ClprChannelStatus.PAUSED);
        assertStatuses(channelId, 350L, 350L, null, ClprChannelStatus.CLOSING);
    }

    @Test
    void findByChannelIdAndTimestampBetweenBoundaries() {
        // given: the range upper bound is exclusive and the lower bound is inclusive
        final var channelId = domainBuilder.bytes(32);
        persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
        persistChannel(channelId, 100L, ClprChannelStatus.PAUSED, Range.atLeast(200L));

        // when / then
        assertStatuses(channelId, 100L, 100L, null, ClprChannelStatus.ACTIVE);
        assertStatuses(channelId, 199L, 199L, null, ClprChannelStatus.ACTIVE);
        assertStatuses(channelId, 200L, 200L, null, ClprChannelStatus.PAUSED);
        assertStatuses(channelId, 0L, 99L, null); // before the channel existed
    }

    @Test
    void findByChannelIdAndTimestampBetweenReturnsLatestWhenBoundsSpanVersions() {
        // given
        final var channelId = domainBuilder.bytes(32);
        persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
        persistChannel(channelId, 100L, ClprChannelStatus.PAUSED, Range.atLeast(200L));

        // when / then
        assertStatuses(channelId, 0L, Long.MAX_VALUE, null, ClprChannelStatus.PAUSED);
        assertStatuses(channelId, 150L, 250L, null, ClprChannelStatus.PAUSED);
    }

    @Test
    void findByChannelIdAndTimestampBetweenFiltersByStatusAfterPickingVersion() {
        // given
        final var channelId = domainBuilder.bytes(32);
        persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
        persistChannel(channelId, 100L, ClprChannelStatus.PAUSED, Range.atLeast(200L));

        // when / then
        assertStatuses(channelId, 150L, 150L, ClprChannelStatus.ACTIVE, ClprChannelStatus.ACTIVE);
        assertStatuses(channelId, 150L, 150L, ClprChannelStatus.PAUSED);
        assertStatuses(channelId, 250L, 250L, ClprChannelStatus.PAUSED, ClprChannelStatus.PAUSED);
        // the latest version in the bounds is PAUSED, so ACTIVE isn't returned even though an older version matches
        assertStatuses(channelId, 0L, Long.MAX_VALUE, ClprChannelStatus.ACTIVE);
    }

    @Test
    void findByChannelIdAndTimestampBetweenUnknownChannel() {
        // given
        persistChannel(100L, ClprChannelStatus.ACTIVE);

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(domainBuilder.bytes(32), 0L, Long.MAX_VALUE, null);

        // then
        assertThat(actual).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByCreatedTimestampBetween(Direction order) {
        // given
        persistChannel(100L, ClprChannelStatus.ACTIVE); // out of range
        final var channel1 = persistChannel(200L, ClprChannelStatus.ACTIVE);
        final var channel2 = persistChannel(300L, ClprChannelStatus.PAUSED);
        final var channel3 = persistChannel(400L, ClprChannelStatus.CLOSED);
        persistChannel(500L, ClprChannelStatus.ACTIVE); // out of range

        final var expected =
                order.isAscending() ? List.of(channel1, channel2, channel3) : List.of(channel3, channel2, channel1);
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findByCreatedTimestampBetween(200L, 400L, null, pageable);

        // then
        assertThat(ids(actual)).containsExactlyElementsOf(channelIds(expected));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findAllChannelsByCreatedTimestampBetween(Direction order) {
        // given
        persistChannel(100L, ClprChannelStatus.ACTIVE); // out of range
        final var channel1 = persistChannel(200L, ClprChannelStatus.ACTIVE);
        final var pending = domainBuilder
                .clprChannelPendingCommitment()
                .customize(p -> p.createdTimestamp(300L).status(ClprPendingCommitmentStatus.OPEN))
                .persist();
        domainBuilder
                .clprChannelPendingCommitment()
                .customize(p -> p.createdTimestamp(350L).status(ClprPendingCommitmentStatus.COMPLETED))
                .persist(); // already a channel
        final var channel2 = persistChannel(400L, ClprChannelStatus.CLOSED);
        persistChannel(500L, ClprChannelStatus.ACTIVE); // out of range

        final var expected = order.isAscending()
                ? List.of(channel1.getChannelId(), pending.getOwnershipCommitment(), channel2.getChannelId())
                : List.of(channel2.getChannelId(), pending.getOwnershipCommitment(), channel1.getChannelId());
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findAllChannelsByCreatedTimestampBetween(200L, 400L, pageable);

        // then
        assertThat(actual)
                .extracting(i -> i.getChannelId() != null ? i.getChannelId() : i.getOwnershipCommitment())
                .containsExactlyElementsOf(expected);
        assertThat(actual)
                .filteredOn(i -> i.getChannelId() == null)
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.getStatus()).isEqualTo("PENDING");
                    assertThat(i.getChainId()).isNull();
                    assertThat(i.getEndpointManifest()).isNull();
                });
        assertThat(actual)
                .filteredOn(i -> i.getChannelId() != null)
                .allSatisfy(i -> assertThat(i.getChainId()).isNotNull());
    }

    @Test
    void findAllChannelsByCreatedTimestampBetweenLimits() {
        // given
        persistChannel(200L, ClprChannelStatus.ACTIVE);
        domainBuilder
                .clprChannelPendingCommitment()
                .customize(p -> p.createdTimestamp(300L).status(ClprPendingCommitmentStatus.OPEN))
                .persist();
        persistChannel(400L, ClprChannelStatus.ACTIVE);
        final var pageable = PageRequest.of(0, 2, Sort.by(Direction.ASC, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findAllChannelsByCreatedTimestampBetween(0L, Long.MAX_VALUE, pageable);

        // then
        assertThat(actual).extracting(i -> i.getCreatedTimestamp()).containsExactly(200L, 300L);
    }

    @Test
    void findByCreatedTimestampBetweenFiltersByStatus() {
        // given
        persistChannel(100L, ClprChannelStatus.ACTIVE);
        final var paused = persistChannel(200L, ClprChannelStatus.PAUSED);
        persistChannel(300L, ClprChannelStatus.CLOSED);

        final var pageable = PageRequest.of(0, 10, Sort.by(Direction.ASC, CREATED_TIMESTAMP));

        // when
        final var actual =
                repository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, ClprChannelStatus.PAUSED.name(), pageable);

        // then
        assertThat(ids(actual)).containsExactly(paused.getChannelId());
    }

    @Test
    void findByCreatedTimestampBetweenIgnoresHistory() {
        // given: a channel that changed status has a history row with the same created_timestamp
        final var channelId = domainBuilder.bytes(32);
        persistHistory(channelId, ClprChannelStatus.ACTIVE, Range.closedOpen(100L, 200L));
        final var current = persistChannel(channelId, 100L, ClprChannelStatus.PAUSED, Range.atLeast(200L));

        final var pageable = PageRequest.of(0, 10, Sort.by(Direction.ASC, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, null, pageable);

        // then
        assertThat(ids(actual)).containsExactly(current.getChannelId());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByCreatedTimestampBetweenRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var channel1 = persistChannel(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persistChannel(200L, ClprChannelStatus.ACTIVE);
        final var channel3 = persistChannel(300L, ClprChannelStatus.ACTIVE);
        final var channel4 = persistChannel(400L, ClprChannelStatus.ACTIVE);

        final var sort = Sort.by(order, CREATED_TIMESTAMP);
        final var all = order.isAscending()
                ? List.of(channel1, channel2, channel3, channel4)
                : List.of(channel4, channel3, channel2, channel1);

        // when
        final var page0 =
                repository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, null, PageRequest.of(0, LIMIT, sort));
        final var page1 =
                repository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, null, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(ids(page0)).containsExactlyElementsOf(channelIds(all.subList(0, LIMIT)));
        assertThat(ids(page1)).containsExactlyElementsOf(channelIds(all.subList(LIMIT, all.size())));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findPendingByCreatedTimestampBetween(Direction order) {
        // given
        persistPending(100L, ClprPendingCommitmentStatus.OPEN); // out of range
        final var pending1 = persistPending(200L, ClprPendingCommitmentStatus.OPEN);
        final var pending2 = persistPending(300L, ClprPendingCommitmentStatus.OPEN);
        final var pending3 = persistPending(400L, ClprPendingCommitmentStatus.OPEN);
        persistPending(500L, ClprPendingCommitmentStatus.OPEN); // out of range

        final var expected =
                order.isAscending() ? List.of(pending1, pending2, pending3) : List.of(pending3, pending2, pending1);
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findPendingByCreatedTimestampBetween(200L, 400L, pageable);

        // then
        assertThat(actual)
                .extracting(ClprChannelData::getOwnershipCommitment)
                .containsExactlyElementsOf(expected.stream()
                        .map(ClprChannelPendingCommitment::getOwnershipCommitment)
                        .toList());
        assertThat(actual).allSatisfy(data -> {
            assertThat(data.getStatus()).isEqualTo("PENDING");
            assertThat(data.getChannelId()).isNull();
            assertThat(data.getChainId()).isNull();
        });
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findPendingByCreatedTimestampBetweenExcludesCompletedAndDeleted(Direction order) {
        // given
        final var open1 = persistPending(100L, ClprPendingCommitmentStatus.OPEN);
        persistPending(200L, ClprPendingCommitmentStatus.COMPLETED);
        persistPending(300L, ClprPendingCommitmentStatus.DELETED);
        final var open2 = persistPending(400L, ClprPendingCommitmentStatus.OPEN);

        final var expected = order.isAscending() ? List.of(open1, open2) : List.of(open2, open1);
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findPendingByCreatedTimestampBetween(0L, Long.MAX_VALUE, pageable);

        // then
        assertThat(actual)
                .extracting(ClprChannelData::getOwnershipCommitment)
                .containsExactlyElementsOf(expected.stream()
                        .map(ClprChannelPendingCommitment::getOwnershipCommitment)
                        .toList());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findPendingByCreatedTimestampBetweenRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var pending1 = persistPending(100L, ClprPendingCommitmentStatus.OPEN);
        final var pending2 = persistPending(200L, ClprPendingCommitmentStatus.OPEN);
        final var pending3 = persistPending(300L, ClprPendingCommitmentStatus.OPEN);
        final var pending4 = persistPending(400L, ClprPendingCommitmentStatus.OPEN);

        final var sort = Sort.by(order, CREATED_TIMESTAMP);
        final var all = order.isAscending()
                ? List.of(pending1, pending2, pending3, pending4)
                : List.of(pending4, pending3, pending2, pending1);
        final var commitments = all.stream()
                .map(ClprChannelPendingCommitment::getOwnershipCommitment)
                .toList();

        // when
        final var page0 =
                repository.findPendingByCreatedTimestampBetween(0L, Long.MAX_VALUE, PageRequest.of(0, LIMIT, sort));
        final var page1 =
                repository.findPendingByCreatedTimestampBetween(0L, Long.MAX_VALUE, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0)
                .extracting(ClprChannelData::getOwnershipCommitment)
                .containsExactlyElementsOf(commitments.subList(0, LIMIT));
        assertThat(page1)
                .extracting(ClprChannelData::getOwnershipCommitment)
                .containsExactlyElementsOf(commitments.subList(LIMIT, commitments.size()));
    }

    private static List<byte[]> channelIds(List<ClprChannel> channels) {
        return channels.stream().map(ClprChannel::getChannelId).toList();
    }

    private static List<byte[]> ids(List<ClprChannelData> data) {
        return data.stream().map(ClprChannelData::getChannelId).toList();
    }

    private void assertStatuses(
            byte[] channelId,
            long lowerBound,
            long upperBound,
            @Nullable ClprChannelStatus statusFilter,
            ClprChannelStatus... expected) {
        final var status = statusFilter != null ? statusFilter.name() : null;
        final var actual = repository.findByChannelIdAndTimestampBetween(channelId, lowerBound, upperBound, status);
        assertThat(actual)
                .extracting(ClprChannelData::getStatus)
                .containsExactly(Arrays.stream(expected).map(Enum::name).toArray(String[]::new));
    }

    /** Persists a current channel created at {@code createdTimestamp} and valid from then on. */
    private ClprChannelPendingCommitment persistPending(long createdTimestamp, ClprPendingCommitmentStatus status) {
        final var builder = ClprChannelPendingCommitment.builder()
                .createdTimestamp(createdTimestamp)
                .ownershipCommitment(domainBuilder.bytes(32))
                .status(status)
                .timestampRange(Range.atLeast(createdTimestamp));
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    private ClprChannel persistChannel(long createdTimestamp, ClprChannelStatus status) {
        return persistChannel(domainBuilder.bytes(32), createdTimestamp, status, Range.atLeast(createdTimestamp));
    }

    private ClprChannel persistChannel(
            byte[] channelId, long createdTimestamp, ClprChannelStatus status, Range<Long> timestampRange) {
        final var builder = ClprChannel.builder();
        populate(builder, channelId, createdTimestamp, status, timestampRange);
        return domainBuilder.wrap(builder, builder::build).persist();
    }

    /** Persists a history row for a channel created at the lower bound of the range. */
    private void persistHistory(byte[] channelId, ClprChannelStatus status, Range<Long> timestampRange) {
        final var builder = ClprChannelHistory.builder();
        populate(builder, channelId, 100L, status, timestampRange);
        domainBuilder.wrap(builder, builder::build).persist();
    }

    private void populate(
            AbstractClprChannelBuilder<?, ?> builder,
            byte[] channelId,
            long createdTimestamp,
            ClprChannelStatus status,
            Range<Long> timestampRange) {
        builder.ackedMessageId(0L)
                .chainId("eip155:1")
                .channelId(channelId)
                .createdTimestamp(createdTimestamp)
                .endpointManifestVersion(0L)
                .nextMessageId(1L)
                .ownershipCommitment(domainBuilder.bytes(32))
                .receivedMessageId(0L)
                .status(status)
                .timestampRange(timestampRange);
    }
}
