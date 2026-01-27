// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@CustomLog
@Named
@RequiredArgsConstructor
final class ImporterLagHealthIndicator implements HealthIndicator {

    private static final String PROMETHEUS_LAG_QUERY = """
            max by (cluster) (
              sum(rate(hiero_mirror_importer_stream_latency_seconds_sum
                  {application="importer",type="%1$s",cluster=~"%2$s"}[3m]))
              by (cluster, namespace)
              /
              sum(rate(hiero_mirror_importer_stream_latency_seconds_count
                  {application="importer",type="%1$s",cluster=~"%2$s"}[3m]))
              by (cluster, namespace)
            )
            """;

    private final ImporterLagHealthProperties properties;
    private final PrometheusApiClient prometheusClient;

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return up();
        }

        final var query = buildLagQuery();

        try {
            final var resp = prometheusClient.query(query);
            return evaluate(resp);
        } catch (final Exception e) {
            log.warn("Importer lag health check failed; returning UP: {}", e.getMessage());
            return up();
        }
    }

    private String buildLagQuery() {
        return PROMETHEUS_LAG_QUERY.formatted(properties.getStreamType().name(), properties.getClusterRegex());
    }

    private Health evaluate(final PrometheusApiClient.PrometheusQueryResponse resp) {
        final var localCluster = StringUtils.trimToNull(properties.getLocalCluster());

        if (localCluster == null
                || resp == null
                || !"success".equals(resp.status())
                || resp.data() == null
                || resp.data().result() == null) {
            return up();
        }

        final var series = resp.data().result();
        if (series.isEmpty()) {
            return up();
        }

        final var lagByCluster = parseLagByCluster(series);
        final var localLag = lagByCluster.get(localCluster);
        if (localLag == null || !Double.isFinite(localLag) || localLag <= properties.getThresholdSeconds()) {
            return up();
        }

        lagByCluster.remove(localCluster);
        final var bestOther =
                lagByCluster.values().stream().filter(Double::isFinite).min(Comparator.naturalOrder());

        final var margin = properties.getThresholdSeconds();
        final var otherClearlyBetter = bestOther.isPresent() && (bestOther.get() + margin) < localLag;

        return otherClearlyBetter ? down() : up();
    }

    private Map<String, Double> parseLagByCluster(final List<PrometheusApiClient.PrometheusSeries> series) {
        final var lagByCluster = new HashMap<String, Double>();

        for (final var s : series) {
            if (s == null
                    || s.metric() == null
                    || s.value() == null
                    || s.value().size() < 2) {
                continue;
            }

            final var cluster = StringUtils.trimToNull(s.metric().cluster());
            if (cluster == null) {
                continue;
            }

            final var raw = s.value().get(1);
            try {
                final var lagSeconds = Double.parseDouble(String.valueOf(raw));
                lagByCluster.put(cluster, lagSeconds);
            } catch (final NumberFormatException ignored) {
                log.warn("Error parsing raw value {}", raw);
            }
        }

        return lagByCluster;
    }

    private static Health up() {
        return Health.up().build();
    }

    private static Health down() {
        return Health.down().build();
    }
}
