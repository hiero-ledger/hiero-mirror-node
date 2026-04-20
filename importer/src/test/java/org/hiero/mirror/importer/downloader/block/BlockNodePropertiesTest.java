// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class BlockNodePropertiesTest {

    @Test
    void getEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setPort(12345);
        assertThat(properties.getEndpoint()).isEqualTo("localhost:12345");
    }

    @Test
    void getMergeKeyIncludesEndpointAndTls() {
        var properties = new BlockNodeProperties();
        properties.setHost("blocknode.example.com");
        properties.setPort(40840);
        properties.setRequiresTls(true);
        assertThat(properties.getMergeKey()).isEqualTo("blocknode.example.com:40840|true");
    }

    @Test
    void getMergeKeyWithoutTls() {
        var properties = new BlockNodeProperties();
        properties.setHost("blocknode.example.com");
        properties.setPort(40840);
        properties.setRequiresTls(false);
        assertThat(properties.getMergeKey()).isEqualTo("blocknode.example.com:40840|false");
    }
}
