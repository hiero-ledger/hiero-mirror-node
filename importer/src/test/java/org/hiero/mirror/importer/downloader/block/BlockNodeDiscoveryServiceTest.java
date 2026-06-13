// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.fullServiceEndpoint;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleEndpointProperties;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleServiceEndpoint;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeEndpoint;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.MirrorNodeEndpoint;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockNodeDiscoveryServiceTest {

    @Mock
    private RegisteredNodeRepository registeredNodeRepository;

    private BlockProperties blockProperties;
    private BlockNodeDiscoveryService service;

    private static BlockNodeEndpoint blockNodeEndpoint(final List<BlockNodeApi> apis) {
        return BlockNodeEndpoint.builder().endpointApis(apis).build();
    }

    private static RegisteredNode registeredNode(List<RegisteredServiceEndpoint> endpoints) {
        return RegisteredNode.builder().serviceEndpoints(endpoints).build();
    }

    @BeforeEach
    void setup() {
        blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setAutoDiscoveryEnabled(true);
        blockProperties.setNodes(List.of());
        service = new BlockNodeDiscoveryService(blockProperties, registeredNodeRepository);
    }

    @Test
    void discoverReturnsEmptyWhenNoNodes() {
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of());
        assertThat(service.getBlockNodes()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            40840, false
            40900, true
            """)
    void discoverConvertsRegisteredNodeToBlockNodeProperties(final int port, final boolean requiresTls) {
        // given
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.STATUS)))
                        .domainName("blocknode.example.com")
                        .port(50000)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.SUBSCRIBE_STREAM)))
                        .domainName("blocknode.example.com")
                        .port(50001)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(
                                List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM)))
                        .domainName("blocknode.example.com")
                        .port(port)
                        .requiresTls(requiresTls)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .domainName("mirrornode.example.com")
                        .mirrorNode(MirrorNodeEndpoint.INSTANCE)
                        .port(8080)
                        .build());
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        // when
        final var result = service.getBlockNodes();

        // then
        final var expectedEndpoint = fullServiceEndpoint("blocknode.example.com", port);
        expectedEndpoint.setRequiresTls(requiresTls);
        assertThat(result)
                .hasSize(1)
                .first()
                .returns(0, BlockNodeProperties::getPriority)
                .extracting(BlockNodeProperties::getEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.SET)
                .containsExactly(expectedEndpoint);
    }

    @Test
    void discoverWhenRegisteredNodeHasMultipleBlockNodeEndpoint() {
        // given
        final var endpoints = List.of(
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.STATUS)))
                        .domainName("blocknode.example.com")
                        .port(5000)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.SUBSCRIBE_STREAM)))
                        .domainName("blocknode.example.com")
                        .port(5001)
                        .requiresTls(false)
                        .build(),
                RegisteredServiceEndpoint.builder()
                        .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.PUBLISH)))
                        .domainName("blocknode.example.com")
                        .port(5002)
                        .requiresTls(false)
                        .build());
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        // when
        final var result = service.getBlockNodes();

        // then
        final var expectedStatusEndpoint = singleServiceEndpoint(BlockNodeApi.STATUS, "blocknode.example.com", 5000);
        final var expectedSubscribeStreamEndpoint =
                singleServiceEndpoint(BlockNodeApi.SUBSCRIBE_STREAM, "blocknode.example.com", 5001);
        assertThat(result)
                .hasSize(1)
                .first()
                .returns(0, BlockNodeProperties::getPriority)
                .extracting(BlockNodeProperties::getEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.SET)
                .containsExactly(expectedStatusEndpoint, expectedSubscribeStreamEndpoint);
    }

    @ParameterizedTest
    @EnumSource(
            names = {"PUBLISH", "STATUS", "SUBSCRIBE_STREAM"},
            value = BlockNodeApi.class)
    void discoverExcludesNodeMissingRequiredApi(final BlockNodeApi missingApi) {
        // given
        final var apis =
                new ArrayList<>(List.of(BlockNodeApi.PUBLISH, BlockNodeApi.STATUS, BlockNodeApi.SUBSCRIBE_STREAM));
        apis.remove(missingApi);
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(blockNodeEndpoint(apis))
                .domainName("blocknode.example.com")
                .port(40840)
                .build());
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        // when, then
        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void discoverExcludesNodeMissingPublishApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.STATUS, BlockNodeApi.SUBSCRIBE_STREAM)))
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void discoverExcludesNodeMissingSubscribeStreamApi() {
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(blockNodeEndpoint(List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH)))
                .domainName("blocknode.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        assertThat(service.getBlockNodes()).isEmpty();
    }

    @Test
    void getBlockNodesReturnsSortedResult() {
        // given
        final var nodeB = singleEndpointProperties("b.example.com");
        nodeB.setPriority(1);

        final var nodeA = singleEndpointProperties("a.example.com");
        nodeA.setPriority(0);

        final var nodeC = singleEndpointProperties("c.example.com");
        nodeC.setPriority(2);

        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeB, nodeA, nodeC));

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
    }

    @Test
    void getBlockNodesSortsByEndpointsWhenPrioritiesEqual() {
        // given
        final var nodeB = singleEndpointProperties("b.example.com");
        final var nodeA = singleEndpointProperties("a.example.com");
        final var nodeC = singleEndpointProperties("c.example.com");
        blockProperties.setAutoDiscoveryEnabled(false);
        blockProperties.setNodes(List.of(nodeC, nodeA, nodeB));

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result).containsExactly(nodeA, nodeB, nodeC);
        verifyNoInteractions(registeredNodeRepository);
    }

    @Test
    void getBlockNodesPropertiesListReturnsConfigWhenAutoDiscoveryDisabled() {
        // given
        blockProperties.setAutoDiscoveryEnabled(false);
        final var configNode = new BlockNodeProperties();
        blockProperties.setNodes(List.of(configNode));

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result).containsExactly(configNode);
        verifyNoInteractions(registeredNodeRepository);
    }

    @Test
    void getBlockNodesPropertiesListMergesConfigWithDiscoveredWhenAutoDiscoveryEnabled() {
        // given
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(blockNodeEndpoint(
                        List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM)))
                .domainName("discovered.example.com")
                .port(40840)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var configNode = BlockNodeTestUtils.singleEndpointProperties("config.example.com");
        blockProperties.setNodes(List.of(configNode));

        // when
        final var result = service.getBlockNodes();

        // then
        final var configNodeEndpoint = configNode.getEndpoints().first();
        final var discoveredEndpoint = fullServiceEndpoint("discovered.example.com", 40840);
        assertThat(result)
                .flatExtracting(BlockNodeProperties::getEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(configNodeEndpoint, discoveredEndpoint);
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());
    }

    @Test
    void blockNodeConfigPropertiesAreReplacedWithDiscoveredOnesIfMergeKeyMatches() {
        // given
        final var endpoints = List.of(RegisteredServiceEndpoint.builder()
                .blockNode(blockNodeEndpoint(
                        List.of(BlockNodeApi.STATUS, BlockNodeApi.PUBLISH, BlockNodeApi.SUBSCRIBE_STREAM)))
                .domainName("blocknode.example.com")
                .port(40840)
                .requiresTls(false)
                .build());

        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of(registeredNode(endpoints)));

        final var configNode = singleEndpointProperties("blocknode.example.com");
        configNode.setPriority(10);
        blockProperties.setNodes(List.of(configNode));

        // when
        final var result = service.getBlockNodes();

        // then
        assertThat(result)
                .hasSize(1)
                .first()
                .returns(0, BlockNodeProperties::getPriority)
                .extracting(BlockNodeProperties::getEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.SET)
                .containsExactly(configNode.getEndpoints().first());
    }

    @Test
    void onRegisteredNodeChangedInvalidatesCache() {
        when(registeredNodeRepository.findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId()))
                .thenReturn(List.of());

        service.getBlockNodes();
        service.getBlockNodes();
        verify(registeredNodeRepository).findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());

        service.onRegisteredNodeChanged();
        service.getBlockNodes();
        verify(registeredNodeRepository, times(2))
                .findAllByDeletedFalseAndTypeContains(RegisteredNodeType.BLOCK_NODE.getId());
    }
}
