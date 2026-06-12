// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleEndpointProperties;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.importer.ImporterProperties;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSortedSet;

final class BlockPropertiesTest {

    @Test
    void getBucketName() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.getImporterProperties().setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-recent-block-streams");

        blockProperties.setBucketName("hedera-testnet-alt-block-streams");
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-alt-block-streams");
    }

    @Test
    void validateBlockNodePropertiesWhenEmpty() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        assertThatCode(blockProperties::validateBlockNodeProperties).doesNotThrowAnyException();
    }

    @Test
    void validateBlockNodePropertiesWhenNotEmpty() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.setNodes(List.of(singleEndpointProperties("a")));
        assertThatCode(blockProperties::validateBlockNodeProperties).doesNotThrowAnyException();
    }

    @Test
    void validateBlockNodePropertiesThrowsMissingRequiredApi() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        final var node1 = singleEndpointProperties("a");
        node1.getEndpoints().first().setApis(ImmutableSortedSet.of(BlockNodeApi.STATUS));
        final var node2 = singleEndpointProperties("b");
        node2.getEndpoints().first().setApis(ImmutableSortedSet.of(BlockNodeApi.SUBSCRIBE_STREAM));
        blockProperties.setNodes(List.of(node1, node2));
        assertThatThrownBy(blockProperties::validateBlockNodeProperties)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Block nodes (1,2) are missing required Status and / or Subscribe Stream APIs");
    }
}
