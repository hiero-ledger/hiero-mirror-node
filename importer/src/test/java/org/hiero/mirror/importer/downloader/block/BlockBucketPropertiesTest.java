// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.importer.ImporterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class BlockBucketPropertiesTest {

    private BlockBucketProperties blockBucketProperties;
    private ImporterProperties importerProperties;

    @BeforeEach
    void setup() {
        importerProperties = new ImporterProperties();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.DEMO);
        blockBucketProperties = new BlockBucketProperties(importerProperties);
    }

    @Test
    void getBucketName() {
        assertThat(blockBucketProperties.getBucketName()).isEqualTo("hedera-demo-recent-block-streams");
    }

    @Test
    void getBucketNameOverridden() {
        blockBucketProperties.setBucketName("alt-recent-block-streams");
        assertThat(blockBucketProperties.getBucketName()).isEqualTo("alt-recent-block-streams");
    }

    @Test
    void isResettable() {
        assertThat(blockBucketProperties.isResettable()).isFalse();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        assertThat(blockBucketProperties.isResettable()).isTrue();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
        assertThat(blockBucketProperties.isResettable()).isFalse();
    }

    @Test
    void isResettableOverridden() {
        blockBucketProperties.setResettable(true);
        assertThat(blockBucketProperties.isResettable()).isTrue();
    }
}
