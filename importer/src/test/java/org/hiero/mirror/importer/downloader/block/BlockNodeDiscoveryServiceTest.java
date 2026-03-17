// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNodeServiceEndpoints;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.record.RegisteredNodeChangedEvent;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockNodeDiscoveryServiceTest {

    @Mock
    private RegisteredNodeRepository registeredNodeRepository;

    private static RegisteredNodeServiceEndpoints serviceEndpoints(List<RegisteredServiceEndpoint> endpoints) {
        return () -> endpoints;
    }

    @Test
    void discoverReturnsEmptyWhenNoNodes() {
        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of());
        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        assertThat(service.discover()).isEmpty();
    }

    @Test
    void discoverConvertsRegisteredNodeToBlockNodeProperties() {
        final var endpoints = List.of(
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
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.PUBLISH)
                                .build())
                        .domainName("publish.example.com")
                        .port(40843)
                        .build());

        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(serviceEndpoints(endpoints)));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.getPriority()).isZero();
        assertThat(props.getHost()).isEqualTo("status.example.com");
        assertThat(props.getStatusHost()).isEqualTo("status.example.com");
        assertThat(props.getStatusPort()).isEqualTo(40840);
        assertThat(props.getStreamingHost()).isEqualTo("192.168.1.10");
        assertThat(props.getStreamingPort()).isEqualTo(40841);
        assertThat(props.isStatusApiRequireTls()).isFalse();
        assertThat(props.isStreamingApiRequireTls()).isFalse();
    }

    @Test
    void discoverSetsRequireTlsFromStatusAndStreamingEndpoints() {
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.STATUS)
                                .build())
                        .domainName("status.example.com")
                        .port(40840)
                        .requiresTls(true)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                .build())
                        .ipAddress("192.168.1.10")
                        .port(40841)
                        .requiresTls(true)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.PUBLISH)
                                .build())
                        .domainName("publish.example.com")
                        .port(40843)
                        .build());

        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(serviceEndpoints(endpoints)));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.isStatusApiRequireTls()).isTrue();
        assertThat(props.isStreamingApiRequireTls()).isTrue();
    }

    @Test
    void discoverExcludesNodeWithoutStatusApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                        .build())
                .ipAddress("192.168.1.10")
                .port(40841)
                .build());

        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(serviceEndpoints(endpoints)));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).isEmpty();
    }

    @Test
    void discoverExcludesNodeWithoutStreamApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApi(BlockNodeApi.STATUS)
                        .build())
                .domainName("status.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(serviceEndpoints(endpoints)));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.discover();

        assertThat(result).isEmpty();
    }

    @Test
    void getBlockNodesPropertiesListReturnsConfigWhenAutoDiscoveryDisabled() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("config.example.com");
        configNode.setStatusPort(40840);
        configNode.setStreamingPort(40841);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties(blockProperties);

        assertThat(result).containsExactly(configNode);
        verify(registeredNodeRepository, never()).findServiceEndpointsByDeletedFalseAndTypeContains(anyShort());
    }

    @Test
    void getBlockNodesPropertiesListMergesConfigWithDiscoveredWhenAutoDiscoveryEnabled() {
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.STATUS)
                                .build())
                        .domainName("discovered.example.com")
                        .port(40840)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                .build())
                        .ipAddress("192.168.1.10")
                        .port(40841)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.PUBLISH)
                                .build())
                        .domainName("publish.example.com")
                        .port(40843)
                        .build());

        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(serviceEndpoints(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("config.example.com");
        configNode.setStatusPort(40840);
        configNode.setStreamingPort(40842);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties(blockProperties);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BlockNodeProperties::getStreamingEndpoint)
                .containsExactlyInAnyOrder("config.example.com:40842", "192.168.1.10:40841");
        verify(registeredNodeRepository)
                .findServiceEndpointsByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());
    }

    @Test
    void onRegisteredNodeChangedInvalidatesCache() {
        when(registeredNodeRepository.findServiceEndpointsByDeletedFalseAndTypeContains(
                        RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of());
        final var service = new BlockNodeDiscoveryService(registeredNodeRepository);

        service.discover();
        service.discover();
        verify(registeredNodeRepository)
                .findServiceEndpointsByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());

        service.onRegisteredNodeChanged(new RegisteredNodeChangedEvent(service));
        service.discover();
        verify(registeredNodeRepository, times(2))
                .findServiceEndpointsByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());
    }
}
