// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.BLOCK_NODE;
import static org.hiero.mirror.common.domain.node.RegisteredNodeType.MIRROR_NODE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class RegisteredNodeRepositoryTest extends ImporterIntegrationTest {

    private final RegisteredNodeRepository registeredNodeRepository;

    @Test
    void findRegisteredNodesByBlockNodeType() {
        final var blockNodeEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(RegisteredServiceEndpoint.BlockNodeApi.STATUS))
                        .build())
                .ipAddress("192.168.1.1")
                .port(50211)
                .requiresTls(true)
                .build();

        final var registeredNode = domainBuilder
                .registeredNode()
                .customize(b ->
                        b.deleted(false).type(List.of(BLOCK_NODE.getId())).serviceEndpoints(List.of(blockNodeEndpoint)))
                .persist();

        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        assertThat(result).containsExactly(registeredNode);
        assertThat(result).hasSize(1).first().satisfies(endpoints -> {
            assertThat(endpoints.getServiceEndpoints()).hasSize(1);
            final var endpoint = endpoints.getServiceEndpoints().get(0);
            assertThat(endpoint.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(endpoint.getPort()).isEqualTo(50211);
            assertThat(endpoint.isRequiresTls()).isTrue();
            assertThat(endpoint.getBlockNode()).isNotNull();
            assertThat(endpoint.getBlockNode().getEndpointApis())
                    .containsExactly(RegisteredServiceEndpoint.BlockNodeApi.STATUS);
        });
    }

    @Test
    void findRegisteredNodesReturnsOnlyNonDeletedBlockNodes() {
        // given
        final var registeredNode1 = domainBuilder.registeredNode().persist();
        final var registeredNode2 = domainBuilder.registeredNode().persist();
        domainBuilder.registeredNode().customize(r -> r.deleted(true)).persist();

        // when
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        // then
        assertThat(result).containsExactlyInAnyOrder(registeredNode1, registeredNode2);

        for (final var endpoints : result) {
            for (final var endpoint : endpoints.getServiceEndpoints()) {
                assertThat(endpoint.getType()).isEqualTo(RegisteredNodeType.BLOCK_NODE);
            }
        }
    }

    @Test
    void findRegisteredNodesNoMatchingNodes() {
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findRegisteredNodesExcludesDeletedNodes() {
        // given: only deleted BLOCK_NODE nodes
        domainBuilder.registeredNode().customize(r -> r.deleted(true)).persist();
        domainBuilder.registeredNode().customize(r -> r.deleted(true)).persist();

        // when
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findRegisteredNodesReturnsNodesWithMultipleTypes() {
        // given
        final var blockNodeEndpoint = RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(
                                RegisteredServiceEndpoint.BlockNodeApi.STATUS,
                                RegisteredServiceEndpoint.BlockNodeApi.PUBLISH))
                        .build())
                .domainName("block.example.com")
                .port(50211)
                .requiresTls(true)
                .build();
        final var mirrorNodeEndpoint = RegisteredServiceEndpoint.builder()
                .ipAddress("10.0.0.1")
                .mirrorNode(RegisteredServiceEndpoint.MirrorNodeEndpoint.INSTANCE)
                .port(8080)
                .requiresTls(false)
                .build();
        final var registeredNode = domainBuilder
                .registeredNode()
                .customize(r -> r.serviceEndpoints(List.of(blockNodeEndpoint, mirrorNodeEndpoint))
                        .type(List.of(BLOCK_NODE.getId(), MIRROR_NODE.getId())))
                .persist();

        // when
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        // then
        assertThat(result).containsExactly(registeredNode);
    }

    @Test
    void findRegisteredNodesByNonPersistedType() {
        // given
        domainBuilder
                .registeredNode()
                .customize(r -> r.type(List.of(MIRROR_NODE.getId())))
                .persist();

        // when
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(BLOCK_NODE.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findRegisteredNodesByMirrorNodeType() {
        // given
        final var registeredNode = domainBuilder
                .registeredNode()
                .customize(r -> r.type(List.of(MIRROR_NODE.getId()))
                        .serviceEndpoints(List.of(RegisteredServiceEndpoint.builder()
                                .mirrorNode(RegisteredServiceEndpoint.MirrorNodeEndpoint.INSTANCE)
                                .ipAddress("127.0.0.1")
                                .port(8080)
                                .requiresTls(false)
                                .build())))
                .persist();

        // when
        final var result = registeredNodeRepository.findAllByDeletedFalseAndTypeContains(MIRROR_NODE.getId());

        // then
        assertThat(result).containsExactly(registeredNode);
        assertThat(result.getFirst().getServiceEndpoints().getFirst().getType()).isEqualTo(MIRROR_NODE);
    }
}
