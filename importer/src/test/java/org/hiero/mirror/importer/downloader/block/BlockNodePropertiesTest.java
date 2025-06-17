// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlockNodePropertiesTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            127.0.0.1, 20000, false, 127.0.0.1:20000
            localhost, 20000, false, localhost:20000
            in-process:server1, 1, true, server1
            """)
    void getEndpoint(String hostname, int port, boolean isInProcess, String endpoint) {
        var properties = new BlockNodeProperties();
        properties.setHost(hostname);
        properties.setPort(port);
        assertThat(properties.isInProcess()).isEqualTo(isInProcess);
        assertThat(properties.getEndpoint()).isEqualTo(endpoint);
    }
}
