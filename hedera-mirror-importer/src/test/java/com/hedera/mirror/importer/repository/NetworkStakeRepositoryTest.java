// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.util.Utility;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NetworkStakeRepositoryTest extends ImporterIntegrationTest {

    private final NetworkStakeRepository networkStakeRepository;

    @Test
    void prune() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.networkStake().customize(n -> n.epochDay(epochDay - 367)).persist();
        var networkStake2 = domainBuilder
                .networkStake()
                .customize(n -> n.epochDay(epochDay - 1))
                .persist();
        var networkStake3 = domainBuilder
                .networkStake()
                .customize(n -> n.epochDay(epochDay))
                .persist();

        networkStakeRepository.prune(networkStake2.getConsensusTimestamp());

        assertThat(networkStakeRepository.findAll()).containsExactlyInAnyOrder(networkStake2, networkStake3);
    }

    @Test
    void save() {
        var networkStake = domainBuilder.networkStake().get();
        networkStakeRepository.save(networkStake);
        assertThat(networkStakeRepository.findById(networkStake.getId())).get().isEqualTo(networkStake);
    }
}
