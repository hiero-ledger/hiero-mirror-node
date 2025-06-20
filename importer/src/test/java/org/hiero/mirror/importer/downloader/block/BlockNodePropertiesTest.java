// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockNodePropertiesTest {

    @Test
    void getEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setPort(12345);
        assertThat(properties.getEndpoint()).isEqualTo("localhost:12345");
    }
}
