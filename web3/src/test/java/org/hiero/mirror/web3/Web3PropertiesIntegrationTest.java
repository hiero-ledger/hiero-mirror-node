// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class Web3PropertiesIntegrationTest extends Web3IntegrationTest {

    private final Web3Properties properties;

    @ParameterizedTest
    @CsvSource({"CALL, 4", "OPCODES, 10"})
    void loadsRequestTimeoutFromApplicationYml(ApiEndpointName name, long expectedSeconds) {
        assertThat(properties.getApi(name).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }
}
