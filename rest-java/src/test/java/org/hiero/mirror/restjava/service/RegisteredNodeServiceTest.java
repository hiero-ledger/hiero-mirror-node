// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;
import org.hiero.mirror.restjava.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

final class RegisteredNodeServiceTest {

    private RegisteredNodeRepository repository;
    private RegisteredNodeService service;

    @BeforeEach
    void setup() {
        repository = mock(RegisteredNodeRepository.class);
        service = new RegisteredNodeServiceImpl(repository);
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

        when(repository.findByRegisteredNodeIdBetweenAndDeletedIsFalse(eq(1L), eq(10L), any()))
                .thenReturn(List.of(node));

        // when
        final var result = service.getRegisteredNodes(request);

        // then
        assertThat(result).containsExactly(node);
        verify(repository)
                .findByRegisteredNodeIdBetweenAndDeletedIsFalse(
                        eq(1L),
                        eq(10L),
                        argThat(pageable -> pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 25
                                && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "registered_node_id"))));
        verify(repository, never())
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIn(anyLong(), anyLong(), anyShort(), any());
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

        when(repository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIn(
                        eq(0L), eq(Long.MAX_VALUE), eq(RegisteredNodeType.MIRROR_NODE.getId()), any()))
                .thenReturn(List.of(node));

        // when
        final var result = service.getRegisteredNodes(request);

        // then
        assertThat(result).containsExactly(node);
        verify(repository)
                .findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIn(
                        eq(0L),
                        eq(Long.MAX_VALUE),
                        eq(RegisteredNodeType.MIRROR_NODE.getId()),
                        argThat(pageable -> pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 10
                                && pageable.getSort().equals(Sort.by(Sort.Direction.ASC, "registered_node_id"))));
        verify(repository, never()).findByRegisteredNodeIdBetweenAndDeletedIsFalse(anyLong(), anyLong(), any());
    }
}
