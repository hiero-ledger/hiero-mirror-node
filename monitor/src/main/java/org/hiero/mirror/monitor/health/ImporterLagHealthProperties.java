// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

@Data
@ConfigurationProperties(prefix = "hiero.mirror.monitor.health.importer-lag")
final class ImporterLagHealthProperties {
    /**
     * Clusters to compare against
     */
    private List<String> clusters = new ArrayList<>();

    /**
     * Enable the lag indicator.
     */
    private boolean enabled = true;

    /**
     * Another cluster must be better than local by at least this many seconds to mark DOWN.
     */
    private int failoverMarginSeconds = 20;

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
     * Basic auth username
     */
    private String prometheusUsername;

    /**
     * If local lag is <= thresholdSeconds, report UP.
     */
    private int thresholdSeconds = 20;

    /**
     * Timeout for the Prometheus query.
     */
    private Duration timeout = Duration.ofSeconds(5);

    boolean isEnabled() {
        return enabled
                && StringUtils.isNotBlank(StringUtils.trimToNull(prometheusBaseUrl))
                && StringUtils.isNotBlank(StringUtils.trimToNull(localCluster))
                && !CollectionUtils.isEmpty(clusters);
    }
}
