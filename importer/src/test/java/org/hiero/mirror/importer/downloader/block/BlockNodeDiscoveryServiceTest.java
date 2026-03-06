// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockNodeDiscoveryServiceTest {

    @Mock
    private RegisteredNodeRepository registeredNodeRepository;

    @Test
    void discoverReturnsEmptyWhenNoNodes() {
        when(registeredNodeRepository.findAllByDeletedFalse()).thenReturn(List.of());
        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
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
                                        .endpointApi(BlockNodeApi.PUBLISH)
                                        .build())
                                .domainName("publish.example.com")
                                .port(40842)
                                .build(),
                        RegisteredServiceEndpoint.builder()
                                .blockNode(BlockNodeEndpoint.builder()
                                        .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                        .build())
                                .ipAddress("192.168.1.10")
                                .port(40841)
                                .build()))
                .build();

        when(registeredNodeRepository.findAllByDeletedFalse()).thenReturn(List.of(registeredNode));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.getPriority()).isZero();
        assertThat(props.getPublishHost()).isEqualTo("publish.example.com");
        assertThat(props.getPublishPort()).isEqualTo(40842);
        assertThat(props.getStatusHost()).isEqualTo("status.example.com");
        assertThat(props.getStatusPort()).isEqualTo(40840);
        assertThat(props.getStreamingHost()).isEqualTo("192.168.1.10");
        assertThat(props.getStreamingPort()).isEqualTo(40841);
    }

    @Test
    void discoverExcludesNodeWithoutPublishApi() {
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

        when(registeredNodeRepository.findAllByDeletedFalse()).thenReturn(List.of(registeredNode));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).isEmpty();
    }
}
