// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.OPCODES;

import java.time.Duration;
import org.hiero.mirror.web3.ApiProperties.RequestProperties;
import org.junit.jupiter.api.Test;

class Web3PropertiesTest {

    @Test
    void getRequestTimeoutFallsBackToDefault() {
        var properties = new Web3Properties();

        assertThat(properties.getRequestTimeout(CALL)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout(OPCODES)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout(null)).isEqualTo(Duration.ofSeconds(4L));
    }

    @Test
    void getRequestTimeoutUsesConfiguredOverride() {
        var properties = new Web3Properties();
        var callRequest = new RequestProperties();
        callRequest.setTimeout(Duration.ofSeconds(6L));
        var callApi = new ApiProperties();
        callApi.setRequest(callRequest);
        properties.getApi().put(CALL, callApi);

        var opcodesRequest = new RequestProperties();
        opcodesRequest.setTimeout(Duration.ofSeconds(20L));
        var opcodesApi = new ApiProperties();
        opcodesApi.setRequest(opcodesRequest);
        properties.getApi().put(OPCODES, opcodesApi);

        assertThat(properties.getRequestTimeout(CALL)).isEqualTo(Duration.ofSeconds(6L));
        assertThat(properties.getRequestTimeout(OPCODES)).isEqualTo(Duration.ofSeconds(20L));
    }
}
