// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class BlockNodePropertiesTest {

    @Test
    void getStatusEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setStatusPort(12345);
        assertThat(properties.getStatusEndpoint()).isEqualTo("localhost:12345");
    }
}
