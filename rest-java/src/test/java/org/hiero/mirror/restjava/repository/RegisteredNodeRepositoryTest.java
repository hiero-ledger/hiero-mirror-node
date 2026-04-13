// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.jooq.domain.tables.RegisteredNode.REGISTERED_NODE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class RegisteredNodeRepositoryTest extends RestJavaIntegrationTest {

    private static final int LIMIT = 2;

    private final RegisteredNodeRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByRegisteredNodeIdBetweenAndDeletedIsFalse(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        persistRegisteredNode(2L, true, RegisteredNodeType.MIRROR_NODE); // deleted
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(4L, false, RegisteredNodeType.RPC_RELAY); // out of range

        final var sort = Sort.by(order, REGISTERED_NODE.REGISTERED_NODE_ID.getName());
        final var pageable = PageRequest.of(0, 10, sort);

        final var expected = order.isAscending() ? List.of(node1, node3) : List.of(node3, node1);

        // when
        final var actual = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalse(1L, 3L, pageable);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(Direction order) {
        // given
        persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        final var mirror2 = persistRegisteredNode(2L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(3L, true, RegisteredNodeType.MIRROR_NODE); // deleted
        final var mirror4 = persistRegisteredNode(4L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(5L, false, RegisteredNodeType.RPC_RELAY);

        final var sort = Sort.by(order, REGISTERED_NODE.REGISTERED_NODE_ID.getName());
        final var pageable = PageRequest.of(0, 10, sort);

        final var expected = order.isAscending() ? List.of(mirror2, mirror4) : List.of(mirror4, mirror2);

        // when
        final var actual = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                0L, Long.MAX_VALUE, RegisteredNodeType.MIRROR_NODE.getId(), pageable);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByRegisteredNodeIdBetweenAndDeletedIsFalseRespectsOrderLimitAndPagination(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        final var node2 = persistRegisteredNode(2L, false, RegisteredNodeType.BLOCK_NODE);
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.BLOCK_NODE);
        final var node4 = persistRegisteredNode(4L, false, RegisteredNodeType.BLOCK_NODE);

        final var sort = Sort.by(order, REGISTERED_NODE.REGISTERED_NODE_ID.getName());
        final var all = order.isAscending() ? List.of(node1, node2, node3, node4) : List.of(node4, node3, node2, node1);

        final var expectedPage0 = all.subList(0, LIMIT);
        final var expectedPage1 = all.subList(LIMIT, all.size());

        // when
        final var page0 = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalse(
                0L, Long.MAX_VALUE, PageRequest.of(0, LIMIT, sort));
        final var page1 = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalse(
                0L, Long.MAX_VALUE, PageRequest.of(1, LIMIT, sort));

        // then
        assertThat(page0).hasSize(expectedPage0.size()).containsExactlyElementsOf(expectedPage0);
        assertThat(page1).hasSize(expectedPage1.size()).containsExactlyElementsOf(expectedPage1);
    }

    private RegisteredNode persistRegisteredNode(long registeredNodeId, boolean deleted, RegisteredNodeType type) {
        return domainBuilder
                .registeredNode()
                .customize(r ->
                        r.registeredNodeId(registeredNodeId).deleted(deleted).type(List.of(type.getId())))
                .persist();
    }
}
