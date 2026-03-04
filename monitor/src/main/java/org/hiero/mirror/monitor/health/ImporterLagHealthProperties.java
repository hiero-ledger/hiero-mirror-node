// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

@Data
@ConfigurationProperties(prefix = "hiero.mirror.monitor.health.importer-lag")
final class ImporterLagHealthProperties {
    /**
     * Clusters to compare against.
     */
    @NotNull
    private List<String> clusters = new ArrayList<>();

    @Getter(lazy = true)
    private final String clusterRegex = clusterRegexOrNull();

    /**
     * Enable the lag indicator.
     */
    private boolean enabled = true;

    /**
     * Cluster label value for THIS cluster.
     */
    private String localCluster;

    /**
     * Prometheus base URL.
     */
    private String prometheusBaseUrl;

    /**
     * Basic auth password/token.
     */
    private String prometheusPassword;

    /**
     * Basic auth username.
     */
    private String prometheusUsername;

    /**
     * If local lag is <= thresholdSeconds, report UP.
     */
    @Positive
    private int thresholdSeconds = 20;

    /**
     * Timeout for the Prometheus query.
     */
    @NotNull
    private Duration timeout = Duration.ofSeconds(5);

    boolean isEnabled() {
        return enabled
                && StringUtils.isNotBlank(StringUtils.trimToNull(prometheusBaseUrl))
                && StringUtils.isNotBlank(StringUtils.trimToNull(localCluster))
                && !CollectionUtils.isEmpty(clusters);
    }

    private String clusterRegexOrNull() {
        final var set = new LinkedHashSet<>(clusters);
        set.add(localCluster);

        final var joined = set.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        return joined.isBlank() ? null : "(" + joined + ")";
    }
}
