// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties
@Data
@NoArgsConstructor
@Validated
public class MirrorNodeProperties {

    @NotNull
    private GrpcProperties grpc = new GrpcProperties();

    @NotNull
    private RestProperties rest = new RestProperties();

    private RestProperties restJava;

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

        private static final int HTTPS_PORT = 443;

        @NotBlank
        private String host;

        @Min(0)
        @Max(65535)
        private int port = 443;

        @NotNull
        private TlsMode tls = TlsMode.AUTO;

        public String getBaseUrl() {
            final var scheme = isSecure() ? "https://" : "http://";
            return scheme + host + ":" + port + "/api/v1";
        }

        public boolean isSecure() {
            return switch (tls) {
                case AUTO -> port == HTTPS_PORT;
                case DISABLED -> false;
                case ENABLED -> true;
            };
        }

        public enum TlsMode {
            AUTO, // Use TLS only if the port is 443
            DISABLED,
            ENABLED
        }
    }
}
