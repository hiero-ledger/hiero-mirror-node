// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.Web3Properties.CALL;
import static org.hiero.mirror.web3.Web3Properties.OPCODES;

import java.time.Duration;
import org.hiero.mirror.web3.Web3Properties.ApiProperties;
import org.hiero.mirror.web3.Web3Properties.RequestProperties;
import org.junit.jupiter.api.Test;

class Web3PropertiesTest {

    @Test
    void getRequestTimeoutFallsBackToDefault() {
        var properties = new Web3Properties();

        assertThat(properties.getRequestTimeout(CALL)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout((String) null)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout("unknown")).isEqualTo(Duration.ofSeconds(4L));
    }

    @Test
    void getRequestTimeoutUsesOpcodesDefault() {
        var properties = new Web3Properties();

        assertThat(properties.getRequestTimeout(OPCODES)).isEqualTo(Duration.ofSeconds(10L));
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
        properties.getApi().get(OPCODES).setRequest(opcodesRequest);

        assertThat(properties.getRequestTimeout(CALL)).isEqualTo(Duration.ofSeconds(6L));
        assertThat(properties.getRequestTimeout(OPCODES)).isEqualTo(Duration.ofSeconds(20L));
    }
}
