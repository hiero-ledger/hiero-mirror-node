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

    @Test
    void differentHostsForStatusAndStreaming() {
        final var properties = new BlockNodeProperties();
        properties.setHost("status.example.com");
        properties.setStatusHost("status.example.com");
        properties.setStatusPort(40840);
        properties.setStreamingHost("stream.example.com");
        properties.setStreamingPort(40841);
        assertThat(properties.getStatusEndpoint()).isEqualTo("status.example.com:40840");
        assertThat(properties.getStreamingEndpoint()).isEqualTo("stream.example.com:40841");
    }

    @Test
    void getPublishEndpoint() {
        var properties = new BlockNodeProperties();
        properties.setHost("localhost");
        properties.setPublishPort(40842);
        assertThat(properties.getPublishEndpoint()).isEqualTo("localhost:40842");
    }

    @Test
    void getEffectivePublishHostUsesPublishHostWhenSet() {
        final var properties = new BlockNodeProperties();
        properties.setHost("default.example.com");
        properties.setPublishHost("publish.example.com");
        assertThat(properties.getEffectivePublishHost()).isEqualTo("publish.example.com");
    }

    @Test
    void getEffectivePublishHostFallsBackToHostWhenPublishHostNotSet() {
        final var properties = new BlockNodeProperties();
        properties.setHost("default.example.com");
        assertThat(properties.getEffectivePublishHost()).isEqualTo("default.example.com");
    }
}
