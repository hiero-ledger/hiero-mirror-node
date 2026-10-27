// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprConnectorPendingCommitmentRepositoryTest extends RestJavaIntegrationTest {

    private final ClprConnectorPendingCommitmentRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findOpenByCreatedTimestampBetween(Direction order) {
        // given
        persist(100L, false, null); // out of range
        final var open1 = persist(200L, false, null);
        persist(250L, false, 260L); // completed
        persist(275L, true, null); // deleted
        final var open2 = persist(300L, false, null);
        persist(400L, false, null); // out of range
        final var expected = order.isAscending() ? List.of(open1, open2) : List.of(open2, open1);

        // when
        final var actual = repository.findOpenByCreatedTimestampBetween(200L, 300L, page(order, 10));

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findOpenByCreatedTimestampBetweenLimit() {
        // given
        persist(100L, false, null);
        final var open2 = persist(200L, false, null);
        final var open3 = persist(300L, false, null);

        // when
        final var actual = repository.findOpenByCreatedTimestampBetween(0L, Long.MAX_VALUE, page(Direction.DESC, 2));

        // then
        assertThat(actual).containsExactly(open3, open2);
    }

    private PageRequest page(Direction order, int limit) {
        return PageRequest.of(0, limit, Sort.by(order, "created_timestamp"));
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
