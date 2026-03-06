// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockNodeDiscoveryServiceTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private RegisteredNodeRepository registeredNodeRepository;

    @Test
    void discoverReturnsEmptyWhenNoNodes() {
        when(nodeRepository.findAllAssociatedRegisteredNodeIds()).thenReturn(List.of());
        final var service = new BlockNodeDiscoveryService(nodeRepository, registeredNodeRepository);
        assertThat(service.discover()).isEmpty();
    }

    @Test
    void discoverConvertsRegisteredNodeToBlockNodeProperties() {
        final var registeredNode = RegisteredNode.builder()
                .registeredNodeId(100L)
                .deleted(false)
                .serviceEndpoints(List.of(
                        RegisteredServiceEndpoint.builder()
                                .blockNode(BlockNodeEndpoint.builder()
                                        .endpointApi(BlockNodeApi.STATUS)
                                        .build())
                                .domainName("status.example.com")
                                .port(40840)
                                .build(),
                        RegisteredServiceEndpoint.builder()
                                .blockNode(BlockNodeEndpoint.builder()
                                        .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                        .build())
                                .ipAddress("192.168.1.10")
                                .port(40841)
                                .build()))
                .build();

        when(nodeRepository.findAllAssociatedRegisteredNodeIds()).thenReturn(List.of(100L));
        when(registeredNodeRepository.findAllByRegisteredNodeIdInAndDeletedFalse(anyCollection()))
                .thenReturn(List.of(registeredNode));

        final var service = new BlockNodeDiscoveryService(nodeRepository, registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.getPriority()).isZero();
        assertThat(props.getStatusHost()).isEqualTo("status.example.com");
        assertThat(props.getStatusPort()).isEqualTo(40840);
        assertThat(props.getStreamingHost()).isEqualTo("192.168.1.10");
        assertThat(props.getStreamingPort()).isEqualTo(40841);
    }
}
