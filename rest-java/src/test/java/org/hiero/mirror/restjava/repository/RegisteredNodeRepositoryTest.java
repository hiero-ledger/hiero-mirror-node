// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class RegisteredNodeRepositoryTest extends RestJavaIntegrationTest {

    private static final int LIMIT = 2;
    /** Fetch enough rows for assertions (repository query has no offset — pagination uses limit only). */
    private static final int FETCH_LIMIT = 100;

    private final RegisteredNodeRepository repository;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIsNull(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        persistRegisteredNode(2L, true, RegisteredNodeType.MIRROR_NODE); // deleted
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.MIRROR_NODE);
        persistRegisteredNode(4L, false, RegisteredNodeType.RPC_RELAY); // out of range

        final var sortSign = order.isAscending() ? 1 : -1;

        final var expected = order.isAscending() ? List.of(node1, node3) : List.of(node3, node1);

        // when
        final var actual =
                repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(1L, 3L, null, sortSign, FETCH_LIMIT);

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

        final var sortSign = order.isAscending() ? 1 : -1;

        final var expected = order.isAscending() ? List.of(mirror2, mirror4) : List.of(mirror4, mirror2);

        // when
        final var actual = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                0L, Long.MAX_VALUE, RegisteredNodeType.MIRROR_NODE.getId(), sortSign, FETCH_LIMIT);

        // then
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findByRegisteredNodeIdBetweenAndDeletedIsFalseRespectsLimit(Direction order) {
        // given
        final var node1 = persistRegisteredNode(1L, false, RegisteredNodeType.BLOCK_NODE);
        final var node2 = persistRegisteredNode(2L, false, RegisteredNodeType.BLOCK_NODE);
        final var node3 = persistRegisteredNode(3L, false, RegisteredNodeType.BLOCK_NODE);
        final var node4 = persistRegisteredNode(4L, false, RegisteredNodeType.BLOCK_NODE);

        final var sortSign = order.isAscending() ? 1 : -1;
        final var all = order.isAscending() ? List.of(node1, node2, node3, node4) : List.of(node4, node3, node2, node1);

        final var expectedLimited = all.subList(0, LIMIT);

        // when
        final var actual = repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
                0L, Long.MAX_VALUE, null, sortSign, LIMIT);

        // then
        assertThat(actual).hasSize(LIMIT).containsExactlyElementsOf(expectedLimited);
    }

    private RegisteredNode persistRegisteredNode(long registeredNodeId, boolean deleted, RegisteredNodeType type) {
        return domainBuilder
                .registeredNode()
                .customize(r ->
                        r.registeredNodeId(registeredNodeId).deleted(deleted).type(List.of(type.getId())))
                .persist();
    }
}
