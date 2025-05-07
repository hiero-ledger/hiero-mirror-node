// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Validated
@ConfigurationProperties("hiero.mirror.importer.downloader")
public class CommonDownloaderProperties {

    private static final MathContext MATH_CONTEXT = new MathContext(19, RoundingMode.DOWN);

    private final ImporterProperties importerProperties;

    private String accessKey;

    private Boolean allowAnonymousAccess;

    private int batchSize = 25;

    private String bucketName;

    private SourceType cloudProvider = SourceType.S3;

    @NotNull
    @Max(1)
    @Min(0)
    private BigDecimal consensusRatio = BigDecimal.ONE.divide(BigDecimal.valueOf(3), MATH_CONTEXT);

    @Max(1)
    @Min(0)
    private BigDecimal downloadRatio = BigDecimal.ONE;

    private String endpointOverride;

    private String gcpProjectId;

    @Min(2L)
    private long maxSize = 50L * 1024L * 1024L; // 50 MiB

    @DurationMin(seconds = 1)
    @NotNull
    private Duration pathRefreshInterval = Duration.ofSeconds(10L);

    @NotNull
    private PathType pathType = PathType.ACCOUNT_ID;

    private String pathPrefix = "";

    private String region = "us-east-1";

    private String secretKey;

    @NotNull
    @Valid
    private List<StreamSourceProperties> sources = new ArrayList<>();

    @DurationMin(seconds = 1)
    @NotNull
    private Duration timeout = Duration.ofSeconds(30L);

    @PostConstruct
    public void init() {
        if (StringUtils.isBlank(bucketName)
                && StringUtils.isBlank(HederaNetwork.getBucketName(importerProperties.getNetwork()))) {
            throw new IllegalArgumentException(
                    "Must define bucketName for network named '%s'".formatted(importerProperties.getNetwork()));
        }

        StreamSourceProperties.SourceCredentials credentials = null;
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            credentials = new StreamSourceProperties.SourceCredentials();
            credentials.setAccessKey(accessKey);
            credentials.setSecretKey(secretKey);
        }

        if (credentials != null || sources.isEmpty()) {
            var source = new StreamSourceProperties();
            source.setCredentials(credentials);
            source.setProjectId(gcpProjectId);
            source.setRegion(region);
            source.setType(cloudProvider);
            if (StringUtils.isNotBlank(endpointOverride)) {
                source.setUri(URI.create(endpointOverride));
            }
            sources.add(0, source);
        }

        validateRatios();
    }

    private void validateRatios() {
        if (downloadRatio == null) {
            // defaults to consensusRatio + 15%, but never higher than 100%
            downloadRatio = BigDecimal.ONE.min(consensusRatio.add(new BigDecimal("0.15"), MATH_CONTEXT));
        } else { // enforce that downloadRatio >= consensusRatio
            if (downloadRatio.compareTo(consensusRatio) < 0) {
                throw new IllegalArgumentException(
                        "downloadRatio (%f) must be >= consensusRatio(%f)".formatted(downloadRatio, consensusRatio));
            }
        }
    }

    public String getBucketName() {
        return StringUtils.isNotBlank(bucketName)
                ? bucketName
                : HederaNetwork.getBucketName(importerProperties.getNetwork());
    }

    public boolean isAnonymousCredentials() {
        return allowAnonymousAccess != null
                ? allowAnonymousAccess
                : HederaNetwork.isAllowAnonymousAccess(importerProperties.getNetwork());
    }

    public enum PathType {
        ACCOUNT_ID,
        AUTO,
        NODE_ID
    }

    @Getter
    @RequiredArgsConstructor
    public enum SourceType {
        GCP("https://storage.googleapis.com"),
        LOCAL(""),
        S3("https://s3.amazonaws.com");

        private final String endpoint;
    }
}
