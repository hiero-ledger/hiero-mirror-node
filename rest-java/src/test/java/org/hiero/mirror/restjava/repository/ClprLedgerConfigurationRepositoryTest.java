// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class ClprLedgerConfigurationRepositoryTest extends RestJavaIntegrationTest {

    private final ClprLedgerConfigurationRepository repository;

    @Test
    void findFirstByConsensusTimestampLessThanEqual() {
        // given
        persist(100L);
        final var row2 = persist(200L);
        final var row3 = persist(300L);

        // when / then
        assertThat(repository.findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(Long.MAX_VALUE))
                .contains(row3);
        assertThat(repository.findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(300L))
                .contains(row3);
        assertThat(repository.findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(299L))
                .contains(row2);
    }

    @Test
    void findFirstByConsensusTimestampLessThanEqualNoneAtOrBefore() {
        // given
        persist(100L);

        // when / then
        assertThat(repository.findFirstByConsensusTimestampLessThanEqualOrderByConsensusTimestampDesc(99L))
                .isEmpty();
    }

    private ClprLedgerConfiguration persist(long consensusTimestamp) {
        return domainBuilder
                .clprLedgerConfiguration()
                .customize(r -> r.consensusTimestamp(consensusTimestamp))
                .persist();
    }
}
