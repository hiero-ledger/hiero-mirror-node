// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.inject.Named;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

@CustomLog
@Named
@RequiredArgsConstructor
final class ImporterLagHealthIndicator implements ReactiveHealthIndicator {

    private final ImporterLagHealthProperties properties;
    private final PrometheusApiClient prometheusClient;

    @Override
    public Mono<Health> health() {
        if (!properties.isEnabled()) {
            return Mono.just(up());
        }

        final var query = buildLagQuery(properties.getLocalCluster());

        return prometheusClient
                .query(query)
                .map(resp -> evaluate(resp, properties.getLocalCluster()))
                .onErrorResume(e -> {
                    log.warn("Importer lag health check failed; returning UP: {}", e.getMessage());
                    return Mono.just(up());
                });
    }

    private String buildLagQuery(final String localCluster) {
        final var clusterRegex = clusterRegexOrNull(localCluster);
        final var clusterMatcher = clusterRegex == null ? "" : ",cluster=~\"" + clusterRegex + "\"";

        final var sum =
                "sum(rate(hiero_mirror_importer_parse_latency_seconds_sum{application=\"importer\",type=\"RECORD\""
                        + clusterMatcher + "}[3m])) by (cluster, namespace)";
        final var count =
                "sum(rate(hiero_mirror_importer_parse_latency_seconds_count{application=\"importer\",type=\"RECORD\""
                        + clusterMatcher + "}[3m])) by (cluster, namespace)";

        return "max by (cluster) (" + sum + " / " + count + ")";
    }

    private String clusterRegexOrNull(final String localCluster) {
        final var set = new LinkedHashSet<>(properties.getClusters());
        set.add(localCluster);

        final var joined = set.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        return joined.isBlank() ? null : "(" + joined + ")";
    }

    private Health evaluate(final PrometheusApiClient.PrometheusQueryResponse resp, final String localCluster) {
        if (resp == null
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
        if (localLag == null || !Double.isFinite(localLag)) {
            return up();
        }

        if (localLag <= properties.getThresholdSeconds()) {
            return up();
        }

        final var bestOther = lagByCluster.entrySet().stream()
                .filter(e -> !localCluster.equals(e.getKey()))
                .map(Map.Entry::getValue)
                .filter(Double::isFinite)
                .min(Comparator.naturalOrder());

        final var margin = properties.getFailoverMarginSeconds();
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

            final var raw = s.value().get(1); // [timestamp, value]
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
