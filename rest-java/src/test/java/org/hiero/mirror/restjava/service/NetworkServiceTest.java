// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
final class NetworkServiceTest extends RestJavaIntegrationTest {

    private final NetworkService networkService;

    @Test
    void returnsLatestStake() {
        // given
        final var expected = domainBuilder.networkStake().persist();

        // when
        final var actual = networkService.getLatestNetworkStake();

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void throwsIfNoStakePresent() {
        assertThatThrownBy(networkService::getLatestNetworkStake)
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No network stake data found");
    }

    @Test
    void getSupplyFromEntity() {
        // given
        final var balance = 1_000_000_000L;
        final var timestamp = domainBuilder.timestamp();
        domainBuilder
                .entity()
                .customize(e -> e.id(domainBuilder.entityNum(2).getId())
                        .balance(balance)
                        .balanceTimestamp(timestamp))
                .persist();

        // when
        final var result = networkService.getSupply(Bound.EMPTY);

        // then
        assertThat(result).isNotNull();
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp);
        assertThat(result.releasedSupply()).isNotNull();
    }

    @Test
    void getSupplyNotFound() {
        // when, then
        assertThatThrownBy(() -> networkService.getSupply(Bound.EMPTY))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Network supply not found");
    }

    @Test
    void getNetworkNodesWithNoFilters() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of())
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).getNodeId()).isEqualTo(1L);
        assertThat(result.get(1).getNodeId()).isEqualTo(2L);
        assertThat(result.get(2).getNodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithNodeIdEquality() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(new EntityIdRangeParameter(RangeOperator.EQ, 1L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).getNodeId()).isEqualTo(1L);
    }

    @Test
    void getNetworkNodesWithNodeIdRange() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(
                        new EntityIdRangeParameter(RangeOperator.GTE, 1L),
                        new EntityIdRangeParameter(RangeOperator.LTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getNodeId()).isEqualTo(1L);
        assertThat(result.get(1).getNodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesWithCombinedFilters() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(
                        new EntityIdRangeParameter(RangeOperator.EQ, 2L),
                        new EntityIdRangeParameter(RangeOperator.EQ, 3L),
                        new EntityIdRangeParameter(RangeOperator.GTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then - should return nodes matching equality AND range
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getNodeId()).isEqualTo(2L);
        assertThat(result.get(1).getNodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithOrderDesc() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of())
                .limit(25)
                .order(Sort.Direction.DESC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).getNodeId()).isEqualTo(3L);
        assertThat(result.get(1).getNodeId()).isEqualTo(2L);
        assertThat(result.get(2).getNodeId()).isEqualTo(1L);
    }

    @Test
    void getNetworkNodesWithLimit() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of())
                .limit(2)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        // Service queries for limit + 1 to support pagination
        // Controller truncates to limit if needed
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2); // Returns limit
        assertThat(result.get(0).getNodeId()).isEqualTo(1L);
        assertThat(result.get(1).getNodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesEmptyResults() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(new EntityIdRangeParameter(RangeOperator.EQ, 99999L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    private org.hiero.mirror.common.domain.entity.EntityId setupNetworkNodeData() {
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

        return addressBook.getFileId();
    }

    @Test
    void getNetworkNodesWithMultipleLowerBounds() {
        // given - gte:1 AND gte:2 should use the most restrictive lower bound (2), returning only node 2 and 3
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(
                        new EntityIdRangeParameter(RangeOperator.GTE, 1L),
                        new EntityIdRangeParameter(RangeOperator.GTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getNodeId()).isEqualTo(2L);
        assertThat(result.get(1).getNodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithMultipleUpperBounds() {
        // given - lte:3 AND lte:2 should use the most restrictive upper bound (2), returning only nodes 1 and 2
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(
                        new EntityIdRangeParameter(RangeOperator.LTE, 3L),
                        new EntityIdRangeParameter(RangeOperator.LTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getNodeId()).isEqualTo(1L);
        assertThat(result.get(1).getNodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesInvalidEmptyRange() {
        // given - setup data
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeId(List.of(
                        new EntityIdRangeParameter(RangeOperator.GT, 4L),
                        new EntityIdRangeParameter(RangeOperator.LT, 5L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when/then
        assertThatThrownBy(() -> networkService.getNetworkNodes(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range for : node.id");
    }
}
