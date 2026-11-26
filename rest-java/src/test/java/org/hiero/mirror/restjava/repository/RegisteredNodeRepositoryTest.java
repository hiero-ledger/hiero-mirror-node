// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class RegisteredNodeRepositoryTest extends RestJavaIntegrationTest {

    private static final int LIMIT = 2;

    private final RegisteredNodeRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findRegisteredNodesWithoutType(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        persistRegisteredNode(2L, true, RegisteredNodeType.MIRROR_NODE); // deleted
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(4L, false, RegisteredNodeType.RPC_RELAY); // out of range

        final var expected = order.isAscending() ? List.of(node1, node3) : List.of(node3, node1);

        // when
        final var actual = repository.findRegisteredNodes(1L, 3L, null, order.name(), 10);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findRegisteredNodesWithType(Direction order) {
        // given
        persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        final var mirror2 = persistRegisteredNode(2L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(3L, true, RegisteredNodeType.MIRROR_NODE); // deleted
        final var mirror4 = persistRegisteredNode(4L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(5L, false, RegisteredNodeType.RPC_RELAY);

        final var expected = order.isAscending() ? List.of(mirror2, mirror4) : List.of(mirror4, mirror2);

        // when
        final var actual = repository.findRegisteredNodes(
                0L, Long.MAX_VALUE, RegisteredNodeType.MIRROR_NODE.getId(), order.name(), 10);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findRegisteredNodesWithTypeMatchesNodeWithMultipleTypes() {
        // given
        final var node = domainBuilder
                .registeredNode()
                .customize(r -> r.registeredNodeId(1L)
                        .type(List.of(RegisteredNodeType.BLOCK_NODE.getId(), RegisteredNodeType.MIRROR_NODE.getId())))
                .persist();
        persistRegisteredNode(2L, false, RegisteredNodeType.BLOCK_NODE);

        // when
        final var actual =
                repository.findRegisteredNodes(0L, Long.MAX_VALUE, RegisteredNodeType.MIRROR_NODE.getId(), "ASC", 10);

        // then
        assertThat(actual).containsExactly(node);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findRegisteredNodesRespectsOrderAndLimit(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        final var node2 = persistRegisteredNode(2L, false, RegisteredNodeType.BLOCK_NODE);
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.BLOCK_NODE);
        final var node4 = persistRegisteredNode(4L, false, RegisteredNodeType.BLOCK_NODE);

        final var expected = order.isAscending() ? List.of(node1, node2) : List.of(node4, node3);

        // when
        final var actual = repository.findRegisteredNodes(0L, Long.MAX_VALUE, null, order.name(), LIMIT);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void findRegisteredNodesMapsNullColumns() {
        // given
        final var node = domainBuilder
                .registeredNode()
                .customize(r -> r.adminKey(null)
                        .createdTimestamp(null)
                        .description(null)
                        .registeredNodeId(1L)
                        .serviceEndpoints(null))
                .persist();

        // when
        final var actual = repository.findRegisteredNodes(0L, Long.MAX_VALUE, null, "ASC", 10);

        // then
        assertThat(actual).containsExactly(node);
        final var actualNode = actual.getFirst();
        assertThat(actualNode.getAdminKey()).isNull();
        assertThat(actualNode.getCreatedTimestamp()).isNull();
        assertThat(actualNode.getDescription()).isNull();
        assertThat(actualNode.getServiceEndpoints()).isNull();
    }

    private RegisteredNode persistRegisteredNode(long registeredNodeId, boolean deleted, RegisteredNodeType type) {
        return domainBuilder
                .registeredNode()
                .customize(r ->
                        r.registeredNodeId(registeredNodeId).deleted(deleted).type(List.of(type.getId())))
                .persist();
    }
}
