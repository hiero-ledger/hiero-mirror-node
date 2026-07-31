// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3Properties.ApiEndpointName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class Web3PropertiesIntegrationTest extends Web3IntegrationTest {

    private final Web3Properties properties;

    @ParameterizedTest
    @CsvSource({"CALL, 60", "OPCODES, 10"})
    void loadsRequestTimeoutFromApplicationYml(ApiEndpointName name, long expectedSeconds) {
        assertThat(properties.getRequestTimeout(name)).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }
}
