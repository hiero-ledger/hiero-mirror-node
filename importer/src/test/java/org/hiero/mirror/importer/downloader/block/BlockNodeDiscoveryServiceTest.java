// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockNodeDiscoveryServiceTest {

    @Mock
    private RegisteredNodeRepository registeredNodeRepository;

    private static RegisteredNode registeredNode(List<RegisteredServiceEndpoint> endpoints) {
        return RegisteredNode.builder().serviceEndpoints(endpoints).build();
    }

    @Test
    void discoverReturnsEmptyWhenNoNodes() {
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of());
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        assertThat(service.getBlockNodesConfigProperties()).isEmpty();
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

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.getPriority()).isZero();
        assertThat(props.getHost()).isEqualTo("status.example.com");
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

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

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

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

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

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).isEmpty();
    }

    @Test
    void getBlockNodesConfigPropertiesReturnsSortedResult() {
        final var nodeB = new BlockNodeProperties();
        nodeB.setHost("b.example.com");
        nodeB.setPriority(1);
        nodeB.setStatusPort(40840);
        nodeB.setStreamingPort(40841);

        final var nodeA = new BlockNodeProperties();
        nodeA.setHost("a.example.com");
        nodeA.setPriority(0);
        nodeA.setStatusPort(40840);
        nodeA.setStreamingPort(40841);

        final var nodeC = new BlockNodeProperties();
        nodeC.setHost("c.example.com");
        nodeC.setPriority(2);
        nodeC.setStatusPort(40840);
        nodeC.setStreamingPort(40841);

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeB, nodeA, nodeC));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
    }

    @Test
    void getBlockNodesConfigPropertiesSortsByHostWhenPrioritiesEqual() {
        final var nodeB = new BlockNodeProperties();
        nodeB.setHost("b.example.com");
        nodeB.setPriority(0);
        nodeB.setStatusPort(40840);
        nodeB.setStreamingPort(40841);

        final var nodeA = new BlockNodeProperties();
        nodeA.setHost("a.example.com");
        nodeA.setPriority(0);
        nodeA.setStatusPort(40840);
        nodeA.setStreamingPort(40841);

        final var nodeC = new BlockNodeProperties();
        nodeC.setHost("c.example.com");
        nodeC.setPriority(0);
        nodeC.setStatusPort(40840);
        nodeC.setStreamingPort(40841);

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeC, nodeA, nodeB));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
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

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).containsExactly(configNode);
        verify(registeredNodeRepository, never()).findAllByDeletedFalseAndTypeContains(anyShort());
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

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("config.example.com");
        configNode.setStatusPort(40840);
        configNode.setStreamingPort(40842);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodesConfigProperties();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BlockNodeProperties::getStreamingEndpoint)
                .containsExactlyInAnyOrder("config.example.com:40842", "192.168.1.10:40841");
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());
    }

    @Test
    void blockNodeConfigPropertiesAreReplacedWithDiscoveredOnesIfMergeKeyMatches() {
        // given
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.STATUS)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40840)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40841)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.PUBLISH)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40843)
                        .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("blocknode.example.com");
        configNode.setStatusPort(40840);
        configNode.setStreamingPort(40841);
        configNode.setStatusApiRequireTls(false);
        configNode.setStreamingApiRequireTls(false);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        // when
        final var result = service.getBlockNodesConfigProperties();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getHost()).isEqualTo("blocknode.example.com");
        assertThat(result.getFirst().getStreamingEndpoint()).isEqualTo("blocknode.example.com:40841");
        assertThat(result.getFirst().isStatusApiRequireTls()).isFalse();
        assertThat(result.getFirst().isStreamingApiRequireTls()).isFalse();
    }

    @Test
    void blockNodeConfigPropertiesAreNotReplacedWithDiscoveredOnesIfMergeKeyDoesNotMatches() {
        // given
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.STATUS)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40840)
                        .requiresTls(true)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.SUBSCRIBE_STREAM)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40841)
                        .requiresTls(true)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(BlockNodeEndpoint.builder()
                                .endpointApi(BlockNodeApi.PUBLISH)
                                .build())
                        .domainName("blocknode.example.com")
                        .port(40843)
                        .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("blocknode.example.com");
        configNode.setStatusPort(40840);
        configNode.setStreamingPort(40841);
        configNode.setStatusApiRequireTls(false);
        configNode.setStreamingApiRequireTls(false);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        // when
        final var result = service.getBlockNodesConfigProperties();

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BlockNodeProperties::getMergeKey)
                .containsExactlyInAnyOrder(
                        "blocknode.example.com:40840|false|blocknode.example.com:40841|false",
                        "blocknode.example.com:40840|true|blocknode.example.com:40841|true");
    }

    @Test
    void onRegisteredNodeChangedInvalidatesCache() {
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue()))
                .thenReturn(List.of());
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        service.getBlockNodesConfigProperties();
        service.getBlockNodesConfigProperties();
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());

        service.onRegisteredNodeChanged();
        service.getBlockNodesConfigProperties();
        verify(registeredNodeRepository, times(2))
                .findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getValue());
    }
}
