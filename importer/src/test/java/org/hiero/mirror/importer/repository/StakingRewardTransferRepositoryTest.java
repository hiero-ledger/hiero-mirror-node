// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class StakingRewardTransferRepositoryTest extends ImporterIntegrationTest {

    private final StakingRewardTransferRepository stakingRewardTransferRepository;

    @Test
    void prune() {
        domainBuilder.stakingRewardTransfer().persist();
        var stakingRewardTransfer2 = domainBuilder.stakingRewardTransfer().persist();
        var stakingRewardTransfer3 = domainBuilder.stakingRewardTransfer().persist();

        stakingRewardTransferRepository.prune(stakingRewardTransfer2.getConsensusTimestamp());

        assertThat(stakingRewardTransferRepository.findAll()).containsExactly(stakingRewardTransfer3);
    }

    @Test
    void save() {
        var stakingRewardTransfer = domainBuilder.stakingRewardTransfer().get();
        stakingRewardTransferRepository.save(stakingRewardTransfer);
        assertThat(stakingRewardTransferRepository.findById(stakingRewardTransfer.getId()))
                .get()
                .isEqualTo(stakingRewardTransfer);
    }
}
