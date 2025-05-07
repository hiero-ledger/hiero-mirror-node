// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;

@Data
public class StreamSourceProperties {

    @DurationMin(seconds = 5)
    @NotNull
    private Duration backoff = Duration.ofSeconds(60L);

    @DurationMin(seconds = 1)
    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(5L);

    private SourceCredentials credentials;

    @Min(0)
    private int maxConcurrency = 1000; // aws sdk default = 50

    private String projectId;

    @NotNull
    private String region = "us-east-1";

    @NotNull
    private CommonDownloaderProperties.SourceType type;

    private URI uri;

    /*
     * If the cloud provider is GCP, it must use the static provider.  If the static credentials are both present,
     * force the mirror node to use the static provider.
     */
    public boolean isStaticCredentials() {
        return type == CommonDownloaderProperties.SourceType.GCP || credentials != null;
    }

    @Data
    public static class SourceCredentials {

        @NotBlank
        private String accessKey;

        @NotBlank
        private String secretKey;
    }
}
