// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
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
      (
        max by (cluster) (
          sum(rate(hiero_mirror_importer_stream_latency_seconds_sum
              {application="importer",type!="BALANCE",cluster=~"%1$s"}[3m]))
          by (cluster, namespace)
          /
          sum(rate(hiero_mirror_importer_stream_latency_seconds_count
              {application="importer",type!="BALANCE",cluster=~"%1$s"}[3m]))
          by (cluster, namespace)
        )
      )
      and on (cluster)
      (
        (hiero_mirror_monitor_cluster_health{application="monitor",cluster=~"%1$s"} >= 1)
        and on (cluster)
        (hiero_mirror_monitor_release_health{application="monitor",cluster=~"%1$s"} >= 1)
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
        return PROMETHEUS_LAG_QUERY.formatted(properties.getClusterRegex());
    }

    private Health evaluate(final PrometheusApiClient.PrometheusQueryResponse resp) {
        if (resp == null) {
            return up();
        }

        final var series = resp.getSeries();
        if (series.isEmpty()) {
            return up();
        }

        final var localCluster = properties.getLocalCluster();
        final var lagByCluster = parseLagByCluster(series);
        final var localLag = lagByCluster.get(localCluster);

        if (!isLagAboveThreshold(localLag)) {
            return up();
        }

        lagByCluster.remove(localCluster);
        final var bestOther = bestOtherLag(lagByCluster);
        return isOtherClearlyBetter(bestOther, localLag) ? down() : up();
    }

    private boolean isLagAboveThreshold(final Double lagSeconds) {
        return lagSeconds != null && Double.isFinite(lagSeconds) && lagSeconds > properties.getThresholdSeconds();
    }

    private OptionalDouble bestOtherLag(final Map<String, Double> lagByCluster) {
        return lagByCluster.values().stream()
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .min();
    }

    private boolean isOtherClearlyBetter(final OptionalDouble bestOther, final double localLag) {
        final var margin = properties.getThresholdSeconds();
        return bestOther.isPresent() && (bestOther.getAsDouble() + margin) < localLag;
    }

    private Map<String, Double> parseLagByCluster(final List<PrometheusApiClient.PrometheusSeries> series) {
        return series.stream()
                .filter(Objects::nonNull)
                .map(this::toClusterLagEntry)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (_, b) -> b, HashMap::new));
    }

    private Optional<Map.Entry<String, Double>> toClusterLagEntry(final PrometheusApiClient.PrometheusSeries s) {
        final var metric = s.metric();
        final var value = s.value();

        if (metric == null || value == null || value.size() < 2) {
            return Optional.empty();
        }

        final var cluster = StringUtils.trimToNull(metric.cluster());
        if (cluster == null) {
            return Optional.empty();
        }

        final var raw = value.get(1);
        return parseDouble(raw).map(lagSeconds -> Map.entry(cluster, lagSeconds));
    }

    private Optional<Double> parseDouble(final Object raw) {
        try {
            return Optional.of(Double.parseDouble(String.valueOf(raw)));
        } catch (final NumberFormatException _) {
            log.warn("Error parsing raw value {}", raw);
            return Optional.empty();
        }
    }

    private static Health up() {
        return Health.up().build();
    }

    private static Health down() {
        return Health.down().build();
    }
}
