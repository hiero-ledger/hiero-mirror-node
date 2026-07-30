// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.OPCODES;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.PRESTATE;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import org.hiero.mirror.web3.ApiProperties.RequestProperties;
import org.junit.jupiter.api.Test;

class Web3PropertiesTest {

    @Test
    void getRequestTimeoutFallsBackToDefault() {
        var properties = new Web3Properties();

        assertThat(properties.getRequestTimeout(CALL)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout(OPCODES)).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getRequestTimeout(PRESTATE)).isEqualTo(Duration.ofSeconds(4L));
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
        assertThat(properties.getRequestTimeout(PRESTATE)).isEqualTo(Duration.ofSeconds(4L));
    }

    @Test
    void getResponseHeadersReturnsEmptyWhenUnset() {
        var properties = new Web3Properties();

        assertThat(properties.getResponseHeaders(CALL)).isEmpty();
        assertThat(properties.getResponseHeaders(OPCODES)).isEmpty();
        assertThat(properties.getResponseHeaders(null)).isEmpty();
    }

    @Test
    void getResponseHeadersUsesConfiguredOverride() {
        var properties = new Web3Properties();

        var callHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        callHeaders.put("Cache-Control", "public, max-age=1");
        callHeaders.put("Access-Control-Allow-Origin", "*");
        var callRequest = new RequestProperties();
        callRequest.setHeaders(callHeaders);
        var callApi = new ApiProperties();
        callApi.setRequest(callRequest);
        properties.getApi().put(CALL, callApi);

        var opcodesHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        opcodesHeaders.put("Cache-Control", "public, max-age=10");
        var opcodesRequest = new RequestProperties();
        opcodesRequest.setHeaders(opcodesHeaders);
        var opcodesApi = new ApiProperties();
        opcodesApi.setRequest(opcodesRequest);
        properties.getApi().put(OPCODES, opcodesApi);

        assertThat(properties.getResponseHeaders(CALL))
                .isEqualTo(Map.of(
                        "Cache-Control", "public, max-age=1",
                        "Access-Control-Allow-Origin", "*"));
        assertThat(properties.getResponseHeaders(OPCODES)).isEqualTo(Map.of("Cache-Control", "public, max-age=10"));
        assertThat(properties.getResponseHeaders(PRESTATE)).isEmpty();
    }

    @Test
    void fromPathResolvesKnownEndpoints() {
        assertThat(CALL.getPath()).isEqualTo("/api/v1/contracts/call");
        assertThat(OPCODES.getPath()).isEqualTo("/api/v1/contracts/results/{transactionIdOrHash}/opcodes");
        assertThat(PRESTATE.getPath()).isEqualTo("/api/v1/contracts/results/{transactionIdOrHash}/prestate");

        assertThat(Web3Properties.ApiEndpointName.fromPath(CALL.getPath())).isEqualTo(CALL);
        assertThat(Web3Properties.ApiEndpointName.fromPath(OPCODES.getPath())).isEqualTo(OPCODES);
        assertThat(Web3Properties.ApiEndpointName.fromPath(PRESTATE.getPath())).isEqualTo(PRESTATE);
        assertThat(Web3Properties.ApiEndpointName.fromPath("/unknown")).isNull();
        assertThat(Web3Properties.ApiEndpointName.fromPath(null)).isNull();
    }
}
