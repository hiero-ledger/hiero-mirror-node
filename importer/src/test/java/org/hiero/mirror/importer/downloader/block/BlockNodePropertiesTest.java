// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockNodePropertiesTest {

    @Test
    void getStatusEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setStatusPort(12345);
        assertThat(properties.getStatusEndpoint()).isEqualTo("localhost:12345");
    }

    @Test
    void getStreamingEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setStreamingPort(12346);
        assertThat(properties.getStreamingEndpoint()).isEqualTo("localhost:12346");
    }

    @Test
    void differentPorts() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setStatusPort(40840);
        properties.setStreamingPort(40841);
        assertThat(properties.getStatusEndpoint()).isEqualTo("localhost:40840");
        assertThat(properties.getStreamingEndpoint()).isEqualTo("localhost:40841");
        assertThat(properties.getStatusPort()).isNotEqualTo(properties.getStreamingPort());
    }
}
