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
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of());
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void discoverConvertsRegisteredNodeToBlockNodeProperties() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).hasSize(1);
        final var props = result.getFirst();
        assertThat(props.getPriority()).isZero();
        assertThat(props.getHost()).isEqualTo("blocknode.example.com");
        assertThat(props.getPort()).isEqualTo(40840);
        assertThat(props.isRequiresTls()).isFalse();
    }

    @Test
    void discoverSetsRequiresTlsFromEndpoint() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .requiresTls(true)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().isRequiresTls()).isTrue();
    }

    @Test
    void discoverExcludesNodeMissingStatusApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void discoverExcludesNodeMissingPublishApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void discoverExcludesNodeMissingSubscribeStreamApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void getBlockNodesReturnsSortedResult() {
        final var nodeB = new BlockNodeProperties();
        nodeB.setHost("b.example.com");
        nodeB.setPriority(1);
        nodeB.setPort(40840);

        final var nodeA = new BlockNodeProperties();
        nodeA.setHost("a.example.com");
        nodeA.setPriority(0);
        nodeA.setPort(40840);

        final var nodeC = new BlockNodeProperties();
        nodeC.setHost("c.example.com");
        nodeC.setPriority(2);
        nodeC.setPort(40840);

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeB, nodeA, nodeC));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
    }

    @Test
    void getBlockNodesSortsByHostWhenPrioritiesEqual() {
        final var nodeB = new BlockNodeProperties();
        nodeB.setHost("b.example.com");
        nodeB.setPriority(0);
        nodeB.setPort(40840);

        final var nodeA = new BlockNodeProperties();
        nodeA.setHost("a.example.com");
        nodeA.setPriority(0);
        nodeA.setPort(40840);

        final var nodeC = new BlockNodeProperties();
        nodeC.setHost("c.example.com");
        nodeC.setPriority(0);
        nodeC.setPort(40840);

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeC, nodeA, nodeB));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
    }

    @Test
    void getBlockNodesPropertiesListReturnsConfigWhenAutoDiscoveryDisabled() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(false);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("config.example.com");
        configNode.setPort(40840);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).containsExactly(configNode);
        verify(registeredNodeRepository, never()).findAllByDeletedFalseAndTypeContains(anyShort());
    }

    @Test
    void getBlockNodesPropertiesListMergesConfigWithDiscoveredWhenAutoDiscoveryEnabled() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("discovered.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("config.example.com");
        configNode.setPort(40840);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
        final var result = service.getBlockNodes();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BlockNodeProperties::getEndpoint)
                .containsExactlyInAnyOrder("config.example.com:40840", "discovered.example.com:40840");
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());
    }

    @Test
    void blockNodeConfigPropertiesAreReplacedWithDiscoveredOnesIfMergeKeyMatches() {
        // given
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .requiresTls(false)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("blocknode.example.com");
        configNode.setPort(40840);
        configNode.setRequiresTls(false);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getHost()).isEqualTo("blocknode.example.com");
        assertThat(result.getFirst().getPort()).isEqualTo(40840);
        assertThat(result.getFirst().isRequiresTls()).isFalse();
    }

    @Test
    void blockNodeConfigPropertiesAreNotReplacedWithDiscoveredOnesIfMergeKeyDoesNotMatch() {
        // given: discovered node has requiresTls=true, config node has requiresTls=false → different merge keys
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(BlockNodeEndpoint.builder()
                        .endpointApis(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM))
                        .build())
                .domainName("blocknode.example.com")
                .port(40840)
                .requiresTls(true)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        final var configNode = new BlockNodeProperties();
        configNode.setHost("blocknode.example.com");
        configNode.setPort(40840);
        configNode.setRequiresTls(false);
        blockProperties.setNodes(List.of(configNode));

        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BlockNodeProperties::getMergeKey)
                .containsExactlyInAnyOrder("blocknode.example.com:40840|false", "blocknode.example.com:40840|true");
    }

    @Test
    void onRegisteredNodeChangedInvalidatesCache() {
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of());
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        final var service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);

        service.getBlockNodes();
        service.getBlockNodes();
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());

        service.onRegisteredNodeChanged();
        service.getBlockNodes();
        verify(registeredNodeRepository, times(2))
                .findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());
    }
}
