// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkNodeRepositoryTest extends RestJavaIntegrationTest {

    private final NetworkNodeRepository networkNodeRepository;
    private final SystemEntity systemEntity;

    private long fileId102;

    @BeforeEach
    void setup() {
        fileId102 = systemEntity.addressBookFile102().getId();
    }

    @Test
    void findNetworkNodesWithNoFilters() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[] {1L}, 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
    }

    @Test
    void findNetworkNodesWithMultipleNodeIdEquality() {
        // given
        setupNetworkNodeData();

        // when
        var results =
                networkNodeRepository.findNetworkNodes(fileId102, new Long[] {1L, 3L}, 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 2L, 3L, "ASC", 25);

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
        var results =
                networkNodeRepository.findNetworkNodes(fileId102, new Long[] {1L, 3L}, 2L, Long.MAX_VALUE, "ASC", 25);

        // then - should return nodes matching equality AND range (node 3 matches both conditions)
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(3L);
    }

    @Test
    void findNetworkNodesOrderAsc() {
        // given
        setupNetworkNodeData();

        // when
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "DESC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 2);

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
        var results =
                networkNodeRepository.findNetworkNodes(fileId102, new Long[] {99999L}, 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        var result = results.get(0);
        assertThat(result.serviceEndpointsJson()).isNotNull();
    }

    @Test
    void findNetworkNodesAllFieldsPopulated() throws Exception {
        // given
        final long nodeId = 1L;
        final long timestamp = domainBuilder.timestamp();
        final var addressBook = domainBuilder
                .addressBook()
                .customize(ab -> ab.startConsensusTimestamp(timestamp))
                .persist();
        // Same port and IP prefix, so the endpoint order doesn't depend on the database collation
        final var endpoint1 = serviceEndpoint(timestamp, nodeId, "10.0.0.1", "");
        final var endpoint2 = serviceEndpoint(timestamp, nodeId, "10.0.0.2", "node.example.com");
        final var entry = domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(timestamp)
                        .nodeCertHash("01ab".getBytes(StandardCharsets.UTF_8))
                        .nodeId(nodeId)
                        .publicKey("302a300506032b6570")
                        .serviceEndpoints(Set.of(endpoint1, endpoint2)))
                .persist();
        final var nodeStake =
                domainBuilder.nodeStake().customize(ns -> ns.nodeId(nodeId)).persist();
        // A null IP address checks that the query returns it as an empty string
        final var node = domainBuilder
                .node()
                .customize(n -> n.grpcProxyEndpoint(new ServiceEndpoint("grpc.example.com", null, 8443))
                        .nodeId(nodeId))
                .persist();

        final var expected = new NetworkNodeDto(
                node.getAdminKey(),
                node.getAssociatedRegisteredNodes().toArray(Long[]::new),
                node.getDeclineReward(),
                entry.getDescription(),
                addressBook.getEndConsensusTimestamp(),
                addressBook.getFileId().getId(),
                null,
                nodeStake.getMaxStake(),
                entry.getMemo(),
                nodeStake.getMinStake(),
                node.getAccountId().getId(),
                "0x01ab",
                nodeId,
                "0x302a300506032b6570",
                nodeStake.getRewardRate(),
                null,
                nodeStake.getStake(),
                nodeStake.getStakeNotRewarded(),
                nodeStake.getStakeRewarded(),
                nodeStake.getStakingPeriod(),
                timestamp);

        // when
        final var results =
                networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

        // then
        assertThat(results).hasSize(1);
        final var result = results.getFirst();
        assertThat(result)
                .usingRecursiveComparison()
                .ignoringFields("grpcProxyEndpointJson", "serviceEndpointsJson")
                .isEqualTo(expected);
        assertThat(readTree(result.grpcProxyEndpointJson())).isEqualTo(readTree("""
                {"domain_name": "grpc.example.com", "ip_address_v4": "", "port": 8443}
                """));
        assertThat(readTree(result.serviceEndpointsJson())).isEqualTo(readTree("""
                [
                  {"domain_name": "", "ip_address_v4": "10.0.0.1", "port": 50211},
                  {"domain_name": "node.example.com", "ip_address_v4": "10.0.0.2", "port": 50211}
                ]
                """));
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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, Long.MAX_VALUE, "ASC", 25);

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
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 1L, Long.MAX_VALUE, "ASC", 25);

        // then - should include the min value
        assertThat(results).isNotNull().hasSize(3);
        assertThat(results.get(0).nodeId()).isEqualTo(1L);
    }

    @Test
    void findNetworkNodesEdgeCaseMaxNodeId() {
        // given
        setupNetworkNodeData();

        // when - max range equals a node id
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 0L, 2L, "ASC", 25);

        // then - should include the max value
        assertThat(results).isNotNull().hasSize(2);
        assertThat(results.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void findNetworkNodesEdgeCaseSingleNodeInRange() {
        // given
        setupNetworkNodeData();

        // when - range that includes only one node
        var results = networkNodeRepository.findNetworkNodes(fileId102, new Long[0], 2L, 2L, "ASC", 25);

        // then
        assertThat(results).isNotNull().hasSize(1);
        assertThat(results.get(0).nodeId()).isEqualTo(2L);
    }

    private static JsonNode readTree(String json) throws JsonProcessingException {
        return ObjectToStringSerializer.OBJECT_MAPPER.readTree(json);
    }

    private AddressBookServiceEndpoint serviceEndpoint(
            long timestamp, long nodeId, String ipAddressV4, String domainName) {
        return domainBuilder
                .addressBookServiceEndpoint()
                .customize(a -> a.consensusTimestamp(timestamp)
                        .domainName(domainName)
                        .ipAddressV4(ipAddressV4)
                        .nodeId(nodeId))
                .get();
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
