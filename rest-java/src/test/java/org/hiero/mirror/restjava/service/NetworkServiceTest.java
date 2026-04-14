// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.restjava.jooq.domain.tables.RegisteredNode.REGISTERED_NODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@RequiredArgsConstructor
final class NetworkServiceTest extends RestJavaIntegrationTest {

    private final NetworkService networkService;

    @MockitoBean
    private RegisteredNodeRepository repository;

    @BeforeEach
    void resetRegisteredNodeRepositoryMock() {
        Mockito.reset(repository);
    }

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
                .nodeIds(List.of())
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).nodeId()).isEqualTo(1L);
        assertThat(result.get(1).nodeId()).isEqualTo(2L);
        assertThat(result.get(2).nodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithNodeIdEquality() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(new NumberRangeParameter(RangeOperator.EQ, 1L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).nodeId()).isEqualTo(1L);
    }

    @Test
    void getNetworkNodesWithNodeIdRange() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.GTE, 1L),
                        new NumberRangeParameter(RangeOperator.LTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).nodeId()).isEqualTo(1L);
        assertThat(result.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesWithCombinedFilters() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.EQ, 2L),
                        new NumberRangeParameter(RangeOperator.EQ, 3L),
                        new NumberRangeParameter(RangeOperator.GTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then - should return nodes matching equality AND range
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).nodeId()).isEqualTo(2L);
        assertThat(result.get(1).nodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithOrderDesc() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of())
                .limit(25)
                .order(Sort.Direction.DESC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get(0).nodeId()).isEqualTo(3L);
        assertThat(result.get(1).nodeId()).isEqualTo(2L);
        assertThat(result.get(2).nodeId()).isEqualTo(1L);
    }

    @Test
    void getNetworkNodesWithLimit() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of())
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
        assertThat(result.get(0).nodeId()).isEqualTo(1L);
        assertThat(result.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesEmptyResults() {
        // given
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(new NumberRangeParameter(RangeOperator.EQ, 99999L)))
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
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.GTE, 1L),
                        new NumberRangeParameter(RangeOperator.GTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).nodeId()).isEqualTo(2L);
        assertThat(result.get(1).nodeId()).isEqualTo(3L);
    }

    @Test
    void getNetworkNodesWithMultipleUpperBounds() {
        // given - lte:3 AND lte:2 should use the most restrictive upper bound (2), returning only nodes 1 and 2
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.LTE, 3L),
                        new NumberRangeParameter(RangeOperator.LTE, 2L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).nodeId()).isEqualTo(1L);
        assertThat(result.get(1).nodeId()).isEqualTo(2L);
    }

    @Test
    void getNetworkNodesInvalidEmptyRange() {
        // given - setup data
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.GT, 4L), new NumberRangeParameter(RangeOperator.LT, 5L)))
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when/then
        assertThatThrownBy(() -> networkService.getNetworkNodes(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range provided for node.id");
    }

    @Test
    void getNetworkNodesNoOverlapBetweenEqualityAndRange() {
        // given - setup data with nodes 1, 2, 3
        var fileId = setupNetworkNodeData();
        var request = NetworkNodeRequest.builder()
                .fileId(new EntityIdRangeParameter(RangeOperator.EQ, fileId.getId()))
                .nodeIds(List.of(
                        new NumberRangeParameter(RangeOperator.EQ, 1L), // Node 1 exists
                        new NumberRangeParameter(RangeOperator.EQ, 2L), // Node 2 exists
                        new NumberRangeParameter(RangeOperator.GT, 10L))) // Range gt:10 excludes nodes 1, 2
                .limit(25)
                .order(Sort.Direction.ASC)
                .build();

        // when
        var result = networkService.getNetworkNodes(request);

        // then - should return empty list because no equality IDs fall within the range
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    void getRegisteredNodesWithoutTypeCallsFindBetween() {
        // given
        final var node = new RegisteredNode();
        final var request = RegisteredNodesRequest.builder()
                .lowerBound(1L)
                .upperBound(10L)
                .limit(25)
                .order(Sort.Direction.DESC)
                .type(null)
                .build();

        when(repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(eq(1L), eq(10L), isNull(), any()))
                .thenReturn(List.of(node));

        // when
        final var result = networkService.getRegisteredNodes(request);

        // then
        Assertions.assertThat(result).containsExactly(node);
        verify(repository)
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                        eq(1L),
                        eq(10L),
                        isNull(),
                        argThat(pageable -> pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 25
                                && pageable.getSort()
                                        .equals(Sort.by(
                                                Sort.Direction.DESC, REGISTERED_NODE.REGISTERED_NODE_ID.getName()))));
        verify(repository, never())
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(anyLong(), anyLong(), anyShort(), any());
    }

    @Test
    void getRegisteredNodesWithTypeCallsFindBetweenAndTypeIn() {
        // given
        final var node = new RegisteredNode();
        final var request = RegisteredNodesRequest.builder()
                .lowerBound(0L)
                .upperBound(Long.MAX_VALUE)
                .limit(10)
                .order(Sort.Direction.ASC)
                .type(RegisteredNodeType.MIRROR_NODE)
                .build();

        when(repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                        eq(0L), eq(Long.MAX_VALUE), eq(RegisteredNodeType.MIRROR_NODE.getId()), any()))
                .thenReturn(List.of(node));

        // when
        final var result = networkService.getRegisteredNodes(request);

        // then
        Assertions.assertThat(result).containsExactly(node);
        verify(repository)
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                        eq(0L),
                        eq(Long.MAX_VALUE),
                        eq(RegisteredNodeType.MIRROR_NODE.getId()),
                        argThat(pageable -> pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 10
                                && pageable.getSort()
                                        .equals(Sort.by(
                                                Sort.Direction.ASC, REGISTERED_NODE.REGISTERED_NODE_ID.getName()))));
        verify(repository, never())
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(anyLong(), anyLong(), isNull(), any());
    }
}
