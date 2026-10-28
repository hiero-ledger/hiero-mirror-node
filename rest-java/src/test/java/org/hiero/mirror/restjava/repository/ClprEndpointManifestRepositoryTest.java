// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprEndpointManifestRepositoryTest extends RestJavaIntegrationTest {

    private final ClprEndpointManifestRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByConsensusTimestampBetween(Direction order) {
        // given
        persist(100L); // out of range
        final var row1 = persist(200L);
        final var row2 = persist(300L);
        final var row3 = persist(400L);
        persist(500L); // out of range
        final var expected = order.isAscending() ? List.of(row1, row2, row3) : List.of(row3, row2, row1);

        // when
        final var actual = repository.findByConsensusTimestampBetween(200L, 400L, page(order, 10));

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findByConsensusTimestampBetweenLimit() {
        // given
        persist(100L);
        final var row2 = persist(200L);
        final var row3 = persist(300L);

        // when
        final var actual = repository.findByConsensusTimestampBetween(0L, Long.MAX_VALUE, page(Direction.DESC, 2));

        // then
        assertThat(actual).containsExactly(row3, row2);
    }

    private PageRequest page(Direction order, int limit) {
        return PageRequest.of(0, limit, Sort.by(order, "consensus_timestamp"));
    }

    private ClprEndpointManifest persist(long consensusTimestamp) {
        return domainBuilder
                .clprEndpointManifest()
                .customize(r -> r.consensusTimestamp(consensusTimestamp))
                .persist();
    }
}
