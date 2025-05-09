// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java")
public class RestJavaProperties {

    @NotNull
    @Valid
    private ResponseConfig response = new ResponseConfig();

    /*
     * Post process the configured response headers. All header names are treated case insensitively, and, for each path,
     * the default headers are first inherited and their values possibly overridden.
     */
    @PostConstruct
    void mergeHeaders() {
        for (var pathHeaders : response.headers.path.entrySet()) {
            var mergedHeaders = Stream.concat(
                            response.headers.defaults.entrySet().stream(), pathHeaders.getValue().entrySet().stream())
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            Entry::getValue,
                            (v1, v2) -> v2,
                            () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

            pathHeaders.setValue(mergedHeaders);
        }
    }

    @Data
    @Validated
    public static class ResponseConfig {
        @NotNull
        @Valid
        private ResponseHeadersConfig headers = new ResponseHeadersConfig();
    }

    @Data
    @Validated
    public static class ResponseHeadersConfig {
        @NotNull
        private Map<String, String> defaults = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        @NotNull
        private Map<String, Map<String, String>> path = new HashMap<>();

        public Map<String, String> getHeadersForPath(String apiPath) {
            return apiPath == null ? defaults : path.getOrDefault(apiPath, defaults);
        }
    }
}
