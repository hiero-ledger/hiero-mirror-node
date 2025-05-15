// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NodeStakeRepositoryTest extends ImporterIntegrationTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void prune() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 367)).persist();
        var nodeStake2 = domainBuilder
                .nodeStake()
                .customize(n -> n.epochDay(epochDay - 1))
                .persist();
        var nodeStake3 =
                domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist();

        nodeStakeRepository.prune(nodeStake2.getConsensusTimestamp());

        assertThat(nodeStakeRepository.findAll()).containsExactlyInAnyOrder(nodeStake2, nodeStake3);
    }

    @Test
    void save() {
        var nodeStake = domainBuilder.nodeStake().get();
        nodeStakeRepository.save(nodeStake);
        assertThat(nodeStakeRepository.findById(nodeStake.getId())).get().isEqualTo(nodeStake);
    }

    @Test
    void findLatest() {
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 2)).persist();
        domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 1)).persist();
        var latestNodeStake =
                domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist();

        assertThat(nodeStakeRepository.findLatest()).containsOnly(latestNodeStake);
    }
}
