// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NodeStakeRepositoryTest extends GrpcIntegrationTest {

    private final NodeStakeRepository nodeStakeRepository;

    @Test
    void emptyTableTest() {
        assertThat(nodeStakeRepository.findLatestTimestamp()).isEmpty();
        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(0L)).isEmpty();
        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(0L)).isEmpty();
    }

    @Test
    void findLatestTimeStamp() {
        nodeStake(1L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimestamp()).contains(1L);

        nodeStake(2L, 0L, 0L);
        assertThat(nodeStakeRepository.findLatestTimestamp()).contains(2L);
    }

    @Test
    void findAllByConsensusTimestamp() {
        long consensusTimestamp = 0L;
        var nodeStakeZeroZero = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStakeZeroOne = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsExactly(nodeStakeZeroZero, nodeStakeZeroOne);

        // Load the next day's node stake info. This repository method is not cached.
        consensusTimestamp++;
        var nodeStakeOneZero = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStakeOneOne = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsExactly(nodeStakeOneZero, nodeStakeOneOne);
    }

    @Test
    void findAllStakeByConsensusTimestamp() {
        long consensusTimestamp = 0L;
        var nodeStakeZeroZero = nodeStake(consensusTimestamp, 0L, 0L);
        var nodeStakeZeroOne = nodeStake(consensusTimestamp, 1L, 1L);

        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 0 stakes")
                .containsAllEntriesOf(Map.of(
                        nodeStakeZeroZero.getNodeId(),
                        nodeStakeZeroZero.getStake(),
                        nodeStakeZeroOne.getNodeId(),
                        nodeStakeZeroOne.getStake()));

        // Clear cache and load the next day's node stake info
        reset();

        consensusTimestamp++;
        var nodeStakeOneZero = nodeStake(consensusTimestamp, 0L, 10L);
        var nodeStakeOneOne = nodeStake(consensusTimestamp, 1L, 11L);

        assertThat(nodeStakeRepository.findAllStakeByConsensusTimestamp(consensusTimestamp))
                .as("Latest timestamp 1 stakes")
                .containsAllEntriesOf(Map.of(
                        nodeStakeOneZero.getNodeId(),
                        nodeStakeOneZero.getStake(),
                        nodeStakeOneOne.getNodeId(),
                        nodeStakeOneOne.getStake()));
    }

    private NodeStake nodeStake(long consensusTimestamp, long nodeId, long stake) {
        return domainBuilder
                .nodeStake()
                .customize(e ->
                        e.consensusTimestamp(consensusTimestamp).nodeId(nodeId).stake(stake))
                .persist();
    }
}
