// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.ApiEndpointName.DEFAULT;
import static org.hiero.mirror.web3.ApiEndpointName.OPCODES;
import static org.hiero.mirror.web3.ApiEndpointName.PRESTATE;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import org.hiero.mirror.web3.ApiProperties.ResponseProperties;
import org.junit.jupiter.api.Test;

class Web3PropertiesTest {

    @Test
    void getApiReturnsDefaultWhenNotConfigured() {
        var properties = new Web3Properties();

        assertThat(properties.getApi(CALL).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getApi(OPCODES).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getApi(PRESTATE).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(4L));
    }

    @Test
    void getApiUsesConfiguredOverride() {
        var properties = new Web3Properties();
        var callRequest = new ResponseProperties();
        callRequest.setTimeout(Duration.ofSeconds(6L));
        var callApi = new ApiProperties();
        callApi.setResponse(callRequest);
        properties.getApi().put(CALL, callApi);

        var opcodesRequest = new ResponseProperties();
        opcodesRequest.setTimeout(Duration.ofSeconds(20L));
        var opcodesApi = new ApiProperties();
        opcodesApi.setResponse(opcodesRequest);
        properties.getApi().put(OPCODES, opcodesApi);

        assertThat(properties.getApi(CALL).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(6L));
        assertThat(properties.getApi(OPCODES).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(20L));
        assertThat(properties.getApi(PRESTATE).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(4L));
    }

    @Test
    void getApiResponseHeadersReturnsEmptyWhenUnset() {
        var properties = new Web3Properties();

        assertThat(properties.getApi(CALL).getResponse().getHeaders()).isEmpty();
        assertThat(properties.getApi(OPCODES).getResponse().getHeaders()).isEmpty();
    }

    @Test
    void getApiResponseHeadersUsesConfiguredOverride() {
        var properties = new Web3Properties();

        var callHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        callHeaders.put("Cache-Control", "public, max-age=1");
        callHeaders.put("Access-Control-Allow-Origin", "*");
        var callRequest = new ResponseProperties();
        callRequest.setHeaders(callHeaders);
        var callApi = new ApiProperties();
        callApi.setResponse(callRequest);
        properties.getApi().put(CALL, callApi);

        var opcodesHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        opcodesHeaders.put("Cache-Control", "public, max-age=10");
        var opcodesRequest = new ResponseProperties();
        opcodesRequest.setHeaders(opcodesHeaders);
        var opcodesApi = new ApiProperties();
        opcodesApi.setResponse(opcodesRequest);
        properties.getApi().put(OPCODES, opcodesApi);

        assertThat(properties.getApi(CALL).getResponse().getHeaders())
                .isEqualTo(Map.of(
                        "Cache-Control", "public, max-age=1",
                        "Access-Control-Allow-Origin", "*"));
        assertThat(properties.getApi(OPCODES).getResponse().getHeaders())
                .isEqualTo(Map.of("Cache-Control", "public, max-age=10"));
        assertThat(properties.getApi(PRESTATE).getResponse().getHeaders()).isEmpty();
    }

    @Test
    void mergeHeadersInheritsDefaultsAndAppliesOverrides() {
        var properties = new Web3Properties();
        var defaults = new ApiProperties();
        defaults.getResponse().getHeaders().put("x-cluster-id", "abc1234");
        defaults.getResponse().getHeaders().put("Access-Control-Allow-Origin", "*");
        defaults.setEnabled(false);
        defaults.getRequest().setThrottle(3L);
        defaults.getResponse().setTimeout(Duration.ofSeconds(9L));
        properties.getApi().put(DEFAULT, defaults);
        properties.getApi().put(CALL, new ApiProperties());

        var opcodesHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        opcodesHeaders.put("Cache-Control", "public, max-age=10");
        opcodesHeaders.put("access-control-allow-origin", "https://example.com");
        var opcodesResponse = new ResponseProperties();
        opcodesResponse.setHeaders(opcodesHeaders);
        var opcodesApi = new ApiProperties();
        opcodesApi.setResponse(opcodesResponse);
        properties.getApi().put(OPCODES, opcodesApi);

        properties.mergeHeaders();

        assertThat(properties.getApi(CALL).isEnabled()).isTrue();
        assertThat(properties.getApi(CALL).getRequest().getThrottle()).isZero();
        assertThat(properties.getApi(CALL).getResponse().getTimeout()).isEqualTo(Duration.ofSeconds(4L));
        assertThat(properties.getApi(CALL).getResponse().getHeaders())
                .containsEntry("x-cluster-id", "abc1234")
                .containsEntry("Access-Control-Allow-Origin", "*");
        assertThat(properties.getApi(OPCODES).getResponse().getHeaders())
                .containsEntry("x-cluster-id", "abc1234")
                .containsEntry("Access-Control-Allow-Origin", "https://example.com")
                .containsEntry("Cache-Control", "public, max-age=10");
        assertThat(properties.getApi(PRESTATE).getResponse().getHeaders()).isEmpty();
    }

    @Test
    void fromPathResolvesKnownEndpoints() {
        assertThat(CALL.getPath()).isEqualTo("/api/v1/contracts/call");
        assertThat(DEFAULT.getPath()).isNull();
        assertThat(OPCODES.getPath()).isEqualTo("/api/v1/contracts/results/{transactionIdOrHash}/opcodes");
        assertThat(PRESTATE.getPath()).isEqualTo("/api/v1/contracts/results/{transactionIdOrHash}/prestate");

        assertThat(ApiEndpointName.fromPath(CALL.getPath())).isEqualTo(CALL);
        assertThat(ApiEndpointName.fromPath(OPCODES.getPath())).isEqualTo(OPCODES);
        assertThat(ApiEndpointName.fromPath(PRESTATE.getPath())).isEqualTo(PRESTATE);
        assertThat(ApiEndpointName.fromPath("/unknown")).isNull();
        assertThat(ApiEndpointName.fromPath(null)).isNull();
    }
}
