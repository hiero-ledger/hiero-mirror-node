// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.CALL;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.OPCODES;

import java.time.Duration;
import java.util.Map;
import org.hiero.mirror.web3.ApiProperties.RequestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Web3PropertiesTest {

    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            "default-header-1", "default value1",
            "Default-Header-2", "default value2");

    private static final Map<String, Map<String, String>> PATH_OVERRIDES = Map.of(
            "path1", Map.of("Default-Header-1", "override value1", "path1-header-1", "path1 value1"),
            "path2",
                    Map.of(
                            "default-header-2",
                            "override value2",
                            "path2-header-1",
                            "path2 value1",
                            "path2-header-2",
                            "path2 value2"));

    /*
     * Headers are merged using a key case insensitive comparator with path specific headers first inheriting
     * from the default headers. So, the header names in the defaults set the map key case even if the value is
     * overridden for a path. This is evident when entries are later iterated over.
     */
    private static final Map<String, Map<String, String>> EXPECTED_MERGE = Map.of(
            "path1",
                    Map.of(
                            "default-header-1",
                            "override value1",
                            "Default-Header-2",
                            "default value2",
                            "path1-header-1",
                            "path1 value1"),
            "path2",
                    Map.of(
                            "default-header-1",
                            "default value1",
                            "Default-Header-2",
                            "override value2",
                            "path2-header-1",
                            "path2 value1",
                            "path2-header-2",
                            "path2 value2"));

    @Test
    void verifyEmptyResponseHeaderMapping() {
        // When
        var properties = new Web3Properties();
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersConfig = properties.getResponse().getHeaders();
        assertThat(headersConfig.getDefaults()).isEmpty();
        assertThat(headersConfig.getPath()).isEmpty();
        assertThat(headersConfig.getHeadersForPath(null)).isEmpty();
        assertThat(headersConfig.getHeadersForPath("path1")).isEmpty();
    }

    @ParameterizedTest(name = "Default headers for path {0}")
    @CsvSource({",", "path1", "path2"})
    void verifyDefaultsReturnedForAll(String apiPath) {
        // When
        var properties = new Web3Properties();
        var headersConfig = properties.getResponse().getHeaders();
        headersConfig.getDefaults().putAll(DEFAULT_HEADERS);
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersForPath = headersConfig.getHeadersForPath(apiPath);
        assertThat(headersForPath).isEqualTo(DEFAULT_HEADERS);
    }

    @ParameterizedTest(name = "Headers for path {0}")
    @CsvSource({",", "path1", "path2", "path3"})
    void verifyPathOverrides(String apiPath) {
        // When
        var properties = new Web3Properties();
        var headersConfig = properties.getResponse().getHeaders();
        headersConfig.getDefaults().putAll(DEFAULT_HEADERS);
        headersConfig.getPath().putAll(PATH_OVERRIDES);
        properties.mergeHeaders(); // @PostConstruct

        // Then
        var headersForPath = headersConfig.getHeadersForPath(apiPath);
        if (apiPath != null && EXPECTED_MERGE.containsKey(apiPath)) {
            assertThat(headersForPath).isEqualTo(EXPECTED_MERGE.get(apiPath));
        } else {
            assertThat(headersForPath).isEqualTo(DEFAULT_HEADERS);
        }
    }

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
