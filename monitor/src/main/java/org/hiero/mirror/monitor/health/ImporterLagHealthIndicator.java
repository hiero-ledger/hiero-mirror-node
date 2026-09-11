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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

@CustomLog
@Named
@RequiredArgsConstructor
final class ImporterLagHealthIndicator implements HealthIndicator {

    private static final String PROMETHEUS_LAG_QUERY = """
      max by (cluster) (
        sum(rate(hiero_mirror_importer_stream_latency_seconds_sum
            {application="importer",type!="BALANCE",cluster=~"%1$s"}[3m]))
        by (cluster, namespace)
        /
        sum(rate(hiero_mirror_importer_stream_latency_seconds_count
            {application="importer",type!="BALANCE",cluster=~"%1$s"}[3m]))
        by (cluster, namespace)
      )
      and on (cluster)
      (
        sum by (cluster) (
          max by (cluster, type) (
            hiero_mirror_monitor_health{application="monitor",cluster=~"%1$s"}
          )
        ) >= 2
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
        final var localCluster = properties.getLocalCluster();
        final var threshold = properties.getThresholdSeconds();

        if (resp == null || resp.getSeries().isEmpty()) {
            log.info("Importer lag for {}: no data available, threshold={}s, result=UP", localCluster, threshold);
            return up();
        }

        final var lagByCluster = parseLagByCluster(resp.getSeries());
        final var localLag = lagByCluster.get(localCluster);

        if (!isLagAboveThreshold(localLag)) {
            log.info("Importer lag for {}: lag={}s, threshold={}s, result=UP", localCluster, localLag, threshold);
            return up();
        }

        lagByCluster.remove(localCluster);
        final var bestOther = bestOtherLag(lagByCluster);

        if (bestOther.isEmpty()) {
            // No peer data is not the same as a peer confirming it's also fine - don't let it drag the group
            // DOWN, but don't silently report UP either.
            log.info(
                    "Importer lag for {}: lag={}s exceeds threshold={}s but no peer data to compare against, result=UNKNOWN",
                    localCluster,
                    localLag,
                    threshold);
            return unknown();
        }

        final var health = isOtherClearlyBetter(bestOther.getAsDouble(), localLag) ? down() : up();
        log.info(
                "Importer lag for {}: lag={}s, threshold={}s, bestPeerLag={}s, result={}",
                localCluster,
                localLag,
                threshold,
                bestOther.getAsDouble(),
                health.getStatus());
        return health;
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

    private boolean isOtherClearlyBetter(final double bestOtherLag, final double localLag) {
        final var margin = properties.getThresholdSeconds();
        return (bestOtherLag + margin) < localLag;
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

    private static Health unknown() {
        return Health.unknown().build();
    }
}
