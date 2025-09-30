// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class MirrorNodeProperties {

    @NotNull
    private GrpcProperties grpc = new GrpcProperties();

    @NotNull
    private RestProperties rest = new RestProperties();

    private RestProperties restJava;

    public RestProperties getRestJava() {
        return restJava != null ? restJava : rest;
    }

    @Data
    @Validated
    public static class GrpcProperties {

        @NotBlank
        private String host;

        @Min(0)
        @Max(65535)
        private int port = 443;

        public String getEndpoint() {
            if (host.startsWith("in-process:")) {
                return host;
            }
            return host + ":" + port;
        }
    }

    @Data
    @Validated
    public static class RestProperties {

        @NotBlank
        private String host;

        @Min(0)
        @Max(65535)
        private int port = 443;

        public String getBaseUrl() {
            String scheme = port == 443 ? "https://" : "http://";
            return scheme + host + ":" + port + "/api/v1";
        }
    }
}
