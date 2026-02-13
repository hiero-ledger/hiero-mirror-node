// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.importer.ImporterProperties;
import org.junit.jupiter.api.Test;

final class BlockPropertiesTest {

    @Test
    void getBucketName() {
        final var blockProperties = new BlockProperties(new ImporterProperties());
        blockProperties.getImporterProperties().setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-recent-block-streams");

        blockProperties.setBucketName("hedera-testnet-alt-block-streams");
        assertThat(blockProperties.getBucketName()).isEqualTo("hedera-testnet-alt-block-streams");
    }
}
