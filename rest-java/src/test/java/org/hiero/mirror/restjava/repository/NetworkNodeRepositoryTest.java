// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkNodeRepositoryTest extends RestJavaIntegrationTest {

    private final NetworkNodeRepository networkNodeRepository;

    @Test
    void findNetworkNodesWithNoFilters() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(3);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
        assertThat(results.get(2).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesWithFileIdFilter() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(
                addressBook.getFileId().getId(), new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
        assertThat(results.get(0).fileId()).isEqualTo(addressBook.getFileId().getId());
    }

    @Test
    void findNetworkNodesWithInvalidFileId() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(99999L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    void findNetworkNodesWithNodeIdEqualityFilter() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[] {1L}, 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
    }

    @Test
    void findNetworkNodesWithMultipleNodeIdEquality() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[] {1L, 3L}, 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(2);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
        assertThat(results.get(1).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesWithNodeIdRangeFilter() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 2L, 3L, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(2);
        assertThat(results.get(0).nodeId()).isEqualTo(2L);
        assertThat(results.get(1).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesWithNodeIdCombinedFilters() {
        // given
        setupNetworkNodeData();

        // when - combining equality (1L, 3L) and range (>=2L)
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[] {1L, 3L}, 2L, Long.MAX_VALUE, "ASC", 25);

        // then - should return nodes matching equality AND range (node 3 matches both conditions)
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesOrderAsc() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(3);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
        assertThat(results.get(2).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesOrderDesc() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "DESC", 25);

        // then
        assertThat(results).isNotNull().hasSize(3);
        assertThat(results.get(0).nodeId()).isEqualTo(3L);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
        assertThat(results.get(2).nodeId()).isEqualTo(1L);
    }

    @Test
    void findNetworkNodesWithLimit() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 2);

        // then
        assertThat(results).isNotNull().hasSize(2);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void findNetworkNodesEmptyResults() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[] {99999L}, 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    void findNetworkNodesWithStakeData() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();
        domainBuilder.nodeStake().customize(ns -> ns.nodeId(1L)).persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.nodeId()).isEqualTo(1L);
        assertThat(result.maxStake()).isNotNull();
        assertThat(result.minStake()).isNotNull();
        assertThat(result.stake()).isNotNull();
        assertThat(result.rewardRateStart()).isNotNull();
    }

    @Test
    void findNetworkNodesWithNodeData() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();
        domainBuilder.node().customize(n -> n.nodeId(1L)).persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.nodeId()).isEqualTo(1L);
        assertThat(result.adminKey()).isNotNull();
        assertThat(result.declineReward()).isNotNull();
    }

    @Test
    void findNetworkNodesWithServiceEndpoints() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.serviceEndpointsJson()).isNotNull();
    }

    @Test
    void findNetworkNodesAllFieldsPopulated() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();
        domainBuilder.nodeStake().customize(ns -> ns.nodeId(1L)).persist();
        domainBuilder.node().customize(n -> n.nodeId(1L)).persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.nodeId()).isNotNull();
        assertThat(result.nodeAccountId()).isNotNull();
        assertThat(result.description()).isNotNull();
        assertThat(result.memo()).isNotNull();
        assertThat(result.publicKey()).isNotNull();
        assertThat(result.fileId()).isNotNull();
        assertThat(result.startConsensusTimestamp()).isNotNull();
        assertThat(result.endConsensusTimestamp()).isNotNull();
        assertThat(result.adminKey()).isNotNull();
        assertThat(result.declineReward()).isNotNull();
        assertThat(result.maxStake()).isNotNull();
        assertThat(result.minStake()).isNotNull();
        assertThat(result.stake()).isNotNull();
        assertThat(result.rewardRateStart()).isNotNull();
        assertThat(result.serviceEndpointsJson()).isNotNull();
    }

    @Test
    void findNetworkNodesWithoutOptionalData() {
        // given - only address book entry, no node or node stake
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.nodeId()).isEqualTo(1L);
        assertThat(result.nodeAccountId()).isNotNull();
        assertThat(result.fileId()).isNotNull();
        // Optional fields should be null
        assertThat(result.adminKey()).isNull();
        assertThat(result.declineReward()).isNull();
        assertThat(result.maxStake()).isNull();
        assertThat(result.minStake()).isNull();
    }

    @Test
    void findNetworkNodesLatestAddressBook() {
        // given - create two address books at different timestamps
        var timestamp1 = domainBuilder.timestamp();
        var timestamp2 = domainBuilder.timestamp();

        var addressBook1 = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp1))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp1).nodeId(1L))
                .persist();

        var addressBook2 = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp2))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp2).nodeId(2L))
                .persist();

        // when - no fileId filter, should return latest address book
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then - should only return nodes from latest address book
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(2L);
        assertThat(results.get(0).startConsensusTimestamp()).isEqualTo(timestamp2);
    }

    @Test
    void findNetworkNodesLatestNodeStake() {
        // given - create node stakes at different timestamps
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();

        var stakeTimestamp1 = domainBuilder.timestamp();
        var stakeTimestamp2 = domainBuilder.timestamp();

        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(1L).consensusTimestamp(stakeTimestamp1))
                .persist();
        var latestStake = domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(1L).consensusTimestamp(stakeTimestamp2))
                .persist();

        // when
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then - should use latest node stake
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.stake()).isEqualTo(latestStake.getStake());
        assertThat(result.rewardRateStart()).isEqualTo(latestStake.getRewardRate());
    }

    @Test
    void findNetworkNodesWithAllParameters() {
        // given
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(2L))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(3L))
                .persist();

        // when - combine all filters (equality AND range logic)
        var results = networkNodeRepository.findNetworkNodes(
                addressBook.getFileId().getId(), new Long[] {2L, 3L}, 1L, 3L, "ASC", 2);

        // then - should return nodes in equality set AND range, with limit applied
        assertThat(results).isNotNull().hasSize(2);
        // With AND logic: nodeId IN (2,3) AND nodeId in [1,3] = nodes 2,3
        // With ASC order and limit=2, returns nodes 2,3
        assertThat(results.get(0).nodeId()).isEqualTo(2L);
        assertThat(results.get(1).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesEdgeCaseMinNodeId() {
        // given
        setupNetworkNodeData();

        // when - min range equals a node id
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 1L, Long.MAX_VALUE, "ASC", 25);

        // then - should include the min value
        assertThat(results).isNotNull().hasSize(3);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
    }

    @Test
    void findNetworkNodesEdgeCaseMaxNodeId() {
        // given
        setupNetworkNodeData();

        // when - max range equals a node id
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 0L, 2L, "ASC", 25);

        // then - should include the max value
        assertThat(results).isNotNull().hasSize(2);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void findNetworkNodesEdgeCaseSingleNodeInRange() {
        // given
        setupNetworkNodeData();

        // when - range that includes only one node
        var results = networkNodeRepository.findNetworkNodes(102L, new Long[0], 2L, 2L, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(2L);
    }

    private void setupNetworkNodeData() {
        var timestamp = domainBuilder.timestamp();
        var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();

        // Create 3 network nodes with different node IDs
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(2L))
                .persist();
        domainBuilder
                .addressBookEntry(2)
                .customize(e -> e.consensusTimestamp(timestamp).nodeId(3L))
                .persist();

        // Add corresponding node stake data
        domainBuilder.nodeStake().customize(ns -> ns.nodeId(1L)).persist();
        domainBuilder.nodeStake().customize(ns -> ns.nodeId(2L)).persist();
        domainBuilder.nodeStake().customize(ns -> ns.nodeId(3L)).persist();

        // Add corresponding node data
        domainBuilder.node().customize(n -> n.nodeId(1L)).persist();
        domainBuilder.node().customize(n -> n.nodeId(2L)).persist();
        domainBuilder.node().customize(n -> n.nodeId(3L)).persist();
    }
}
