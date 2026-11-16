// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class ClprEndpointManifestRepositoryTest extends RestJavaIntegrationTest {

    private final ClprEndpointManifestRepository repository;

    @Test
    void findFirstBoundsAreInclusive() {
        // given
        final var entity = domainBuilder
                .clprEndpointManifest()
                .customize(e -> e.consensusTimestamp(200L))
                .persist();

        // when / then
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(200L, 200L))
                .isEqualTo(entity);
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(201L, 300L))
                .isNull();
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(0L, 199L))
                .isNull();
    }

    @Test
    void findFirstReturnsLatestWithinBounds() {
        // given
        domainBuilder
                .clprEndpointManifest()
                .customize(e -> e.consensusTimestamp(100L))
                .persist();
        final var middle = domainBuilder
                .clprEndpointManifest()
                .customize(e -> e.consensusTimestamp(200L))
                .persist();
        final var latest = domainBuilder
                .clprEndpointManifest()
                .customize(e -> e.consensusTimestamp(300L))
                .persist();

        // when / then
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(0L, Long.MAX_VALUE))
                .isEqualTo(latest);
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(0L, 299L))
                .isEqualTo(middle);
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(300L, 300L))
                .isEqualTo(latest);
    }

    @Test
    void findFirstReturnsNullWhenEmpty() {
        assertThat(repository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(0L, Long.MAX_VALUE))
                .isNull();
    }
}
