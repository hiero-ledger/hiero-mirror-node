// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class Web3PropertiesIntegrationTest extends Web3IntegrationTest {

    private final Web3Properties properties;

    @Test
    void loadsActionsEnabledFromApplicationYml() {
        assertThat(properties.getApi(ApiEndpointName.ACTIONS).isEnabled()).isFalse();
    }

    @Test
    void loadsActionsRequestThrottleFromApplicationYml() {
        assertThat(properties.getApi(ApiEndpointName.ACTIONS).getRequest().getThrottle())
                .isEqualTo(100L);
    }

    @ParameterizedTest
    @CsvSource({"ACTIONS, 4", "CALL, 4", "OPCODES, 10", "PRESTATE, 4"})
    void loadsRequestTimeoutFromApplicationYml(ApiEndpointName name, long expectedSeconds) {
        assertThat(properties.getApi(name).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }
}
