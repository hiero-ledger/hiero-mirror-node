// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprPendingChannelRepositoryTest extends RestJavaIntegrationTest {

    private static final String CREATED_TIMESTAMP = "created_timestamp";
    private static final int LIMIT = 2;

    private final ClprPendingChannelRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findOpenByCreatedTimestampBetween(Direction order) {
        // given
        persist(100L, ClprPendingCommitmentStatus.OPEN); // out of range
        final var pending1 = persist(200L, ClprPendingCommitmentStatus.OPEN);
        final var pending2 = persist(300L, ClprPendingCommitmentStatus.OPEN);
        final var pending3 = persist(400L, ClprPendingCommitmentStatus.OPEN);
        persist(500L, ClprPendingCommitmentStatus.OPEN); // out of range

        final var expected =
                order.isAscending() ? List.of(pending1, pending2, pending3) : List.of(pending3, pending2, pending1);
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findOpenByCreatedTimestampBetween(200L, 400L, pageable);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findOpenByCreatedTimestampBetweenExcludesCompletedAndDeleted(Direction order) {
        // given
        final var open1 = persist(100L, ClprPendingCommitmentStatus.OPEN);
        persist(200L, ClprPendingCommitmentStatus.COMPLETED);
        persist(300L, ClprPendingCommitmentStatus.DELETED);
        final var open2 = persist(400L, ClprPendingCommitmentStatus.OPEN);

        final var expected = order.isAscending() ? List.of(open1, open2) : List.of(open2, open1);
        final var pageable = PageRequest.of(0, 10, Sort.by(order, CREATED_TIMESTAMP));

        // when
        final var actual = repository.findOpenByCreatedTimestampBetween(0L, Long.MAX_VALUE, pageable);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findOpenByCreatedTimestampBetweenRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var pending1 = persist(100L, ClprPendingCommitmentStatus.OPEN);
        final var pending2 = persist(200L, ClprPendingCommitmentStatus.OPEN);
        final var pending3 = persist(300L, ClprPendingCommitmentStatus.OPEN);
        final var pending4 = persist(400L, ClprPendingCommitmentStatus.OPEN);

        final var sort = Sort.by(order, CREATED_TIMESTAMP);
        final var all = order.isAscending()
                ? List.of(pending1, pending2, pending3, pending4)
                : List.of(pending4, pending3, pending2, pending1);

        // when
        final var page0 =
                repository.findOpenByCreatedTimestampBetween(0L, Long.MAX_VALUE, PageRequest.of(0, LIMIT, sort));
        final var page1 =
                repository.findOpenByCreatedTimestampBetween(0L, Long.MAX_VALUE, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0).containsExactlyElementsOf(all.subList(0, LIMIT));
        assertThat(page1).containsExactlyElementsOf(all.subList(LIMIT, all.size()));
    }

    private ClprChannelPendingCommitment persist(long createdTimestamp, ClprPendingCommitmentStatus status) {
        final var builder = ClprChannelPendingCommitment.builder()
                .createdTimestamp(createdTimestamp)
                .ownershipCommitment(domainBuilder.bytes(32))
                .status(status)
                .timestampRange(Range.atLeast(createdTimestamp));
        return domainBuilder.wrap(builder, builder::build).persist();
    }
}
