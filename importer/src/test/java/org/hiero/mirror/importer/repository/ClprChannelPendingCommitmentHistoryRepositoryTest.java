// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprChannelPendingCommitmentHistoryRepositoryTest extends ImporterIntegrationTest {

    private final ClprChannelPendingCommitmentHistoryRepository clprChannelPendingCommitmentHistoryRepository;

    @Test
    void prune() {
        domainBuilder.clprChannelPendingCommitmentHistory().persist();
        var clprChannelPendingCommitmentHistory2 =
                domainBuilder.clprChannelPendingCommitmentHistory().persist();
        var clprChannelPendingCommitmentHistory3 =
                domainBuilder.clprChannelPendingCommitmentHistory().persist();

        clprChannelPendingCommitmentHistoryRepository.prune(clprChannelPendingCommitmentHistory2.getTimestampUpper());

        assertThat(clprChannelPendingCommitmentHistoryRepository.findAll())
                .containsExactly(clprChannelPendingCommitmentHistory3);
    }

    @Test
    void save() {
        var clprChannelPendingCommitmentHistory =
                domainBuilder.clprChannelPendingCommitmentHistory().persist();
        assertThat(clprChannelPendingCommitmentHistoryRepository.findById(
                        clprChannelPendingCommitmentHistory.getOwnershipCommitment()))
                .get()
                .isEqualTo(clprChannelPendingCommitmentHistory);
    }
}
