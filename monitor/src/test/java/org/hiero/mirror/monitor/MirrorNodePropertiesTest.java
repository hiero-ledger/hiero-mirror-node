// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.monitor.MirrorNodeProperties.RestProperties;
import org.hiero.mirror.monitor.MirrorNodeProperties.RestProperties.TlsMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MirrorNodePropertiesTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            80, AUTO, http://localhost:80/api/v1
            443, AUTO, https://localhost:443/api/v1
            8443, AUTO, http://localhost:8443/api/v1
            80, ENABLED, https://localhost:80/api/v1
            8443, ENABLED, https://localhost:8443/api/v1
            80, DISABLED, http://localhost:80/api/v1
            443, DISABLED, http://localhost:443/api/v1
            """)
    void getBaseUrl(int port, TlsMode tls, String expected) {
        final var restProperties = new RestProperties();
        restProperties.setHost("localhost");
        restProperties.setPort(port);
        restProperties.setTls(tls);

        assertThat(restProperties.getBaseUrl()).isEqualTo(expected);
        assertThat(restProperties.isSecure()).isEqualTo(expected.startsWith("https://"));
    }

    @Test
    void getBaseUrlDefault() {
        final var restProperties = new RestProperties();
        restProperties.setHost("localhost");

        assertThat(restProperties.getBaseUrl()).isEqualTo("https://localhost:443/api/v1");
    }
}
