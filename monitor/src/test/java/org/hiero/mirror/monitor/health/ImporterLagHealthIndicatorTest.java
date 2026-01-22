// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;

class ImporterLagHealthIndicatorTest {
    private static final String THIS_CLUSTER = "mainnet-1";
    private static final String OTHER_CLUSTER = "mainnet-2";

    @Test
    void upWhenDisabled() {
        final var props = props(p -> p.setEnabled(false));
        final var client = mock(PrometheusApiClient.class);
        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verifyNoInteractions(client);
    }

    @Test
    void upWhenLocalClusterMissing() {
        final var props = props(p -> p.setLocalCluster(null));
        final var client = mock(PrometheusApiClient.class);
        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verifyNoInteractions(client);
    }

    @Test
    void upWhenAlternateClusterMissing() {
        final var props = props(p -> p.setClusters(Collections.emptyList()));
        final var client = mock(PrometheusApiClient.class);
        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verifyNoInteractions(client);
    }

    @Test
    void upOnClientError() {
        final var props = props(p -> {});
        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString())).thenReturn(Mono.error(new RuntimeException("boom")));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        verify(client).query(anyString());
    }

    @Test
    void upWhenLocalBelowThreshold() {
        final var props = props(p -> {
            p.setThresholdSeconds(10);
            p.setFailoverMarginSeconds(20);
            p.setLocalCluster(THIS_CLUSTER);
        });

        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString()))
                .thenReturn(Mono.just(success(List.of(series(THIS_CLUSTER, 5.0), series(OTHER_CLUSTER, 1.0)))));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenLocalAboveThresholdAndOtherClearlyBetter() {
        final var props = props(p -> {
            p.setThresholdSeconds(10);
            p.setFailoverMarginSeconds(20);
            p.setLocalCluster(THIS_CLUSTER);
        });

        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString()))
                .thenReturn(Mono.just(success(List.of(series(THIS_CLUSTER, 50.0), series(OTHER_CLUSTER, 5.0)))));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void upWhenLocalAboveThresholdButOtherNotBetterEnough() {
        final var props = props(p -> {
            p.setThresholdSeconds(10);
            p.setFailoverMarginSeconds(20);
            p.setLocalCluster(THIS_CLUSTER);
        });

        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString()))
                .thenReturn(Mono.just(success(List.of(series(THIS_CLUSTER, 25.0), series(OTHER_CLUSTER, 10.0)))));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void queriesPrometheusWithExpectedMetricName() {
        final var props = props(p -> p.setLocalCluster(THIS_CLUSTER));
        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString())).thenReturn(Mono.just(success(List.of(series(THIS_CLUSTER, 1.0)))));

        final var indicator = new ImporterLagHealthIndicator(props, client);
        indicator.health().block(Duration.ofSeconds(1));

        verify(client).query(argThat(q -> q.contains("hiero_mirror_importer_parse_latency_seconds_sum")));
    }

    @Test
    void upWhenResultSeriesEmpty() {
        final var props = props(p -> {});
        final var client = mock(PrometheusApiClient.class);

        when(client.query(anyString())).thenReturn(Mono.just(success(List.of())));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void upWhenAllSeriesAreInvalid() {
        final var props = props(p -> {});
        final var client = mock(PrometheusApiClient.class);

        final var invalid = Arrays.asList(
                (PrometheusApiClient.PrometheusSeries) null,
                new PrometheusApiClient.PrometheusSeries(new PrometheusApiClient.PrometheusMetric(THIS_CLUSTER), null),
                new PrometheusApiClient.PrometheusSeries(null, List.of(1.0d, "5.0")),
                new PrometheusApiClient.PrometheusSeries(
                        new PrometheusApiClient.PrometheusMetric(THIS_CLUSTER), List.of("only-one")));

        when(client.query(anyString())).thenReturn(Mono.just(success(invalid)));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void upWhenSeriesClusterMissingOrBlank() {
        final var props = props(p -> {});
        final var client = mock(PrometheusApiClient.class);

        final var missingCluster = List.of(series(null, 50.0), series("   ", 50.0));

        when(client.query(anyString())).thenReturn(Mono.just(success(missingCluster)));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void upWhenOtherClusterLagIsNotANumber() {
        final var props = props(p -> {
            p.setLocalCluster(THIS_CLUSTER);
            p.setThresholdSeconds(10);
            p.setFailoverMarginSeconds(20);
        });

        final var client = mock(PrometheusApiClient.class);

        final var local = series(THIS_CLUSTER, 50.0); // above threshold
        final var otherBad = rawSeries(THIS_CLUSTER, "not-a-number"); // triggers NumberFormatException

        when(client.query(anyString())).thenReturn(Mono.just(success(List.of(local, otherBad))));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void upWhenPrometheusStatusNotSuccess() {
        final var props = props(p -> {});
        final var client = mock(PrometheusApiClient.class);
        when(client.query(anyString()))
                .thenReturn(Mono.just(new PrometheusApiClient.PrometheusQueryResponse("error", null)));

        final var indicator = new ImporterLagHealthIndicator(props, client);

        final var health = indicator.health().block(Duration.ofSeconds(1));

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    private static ImporterLagHealthProperties props(final Consumer<ImporterLagHealthProperties> customize) {
        final var p = new ImporterLagHealthProperties();
        p.setEnabled(true);
        p.setPrometheusBaseUrl("https://prometheus.example/api/prom");
        p.setTimeout(Duration.ofSeconds(1));
        p.setLocalCluster(THIS_CLUSTER);
        p.setClusters(List.of(OTHER_CLUSTER));
        p.setThresholdSeconds(10);
        p.setFailoverMarginSeconds(20);
        customize.accept(p);
        return p;
    }

    private static PrometheusApiClient.PrometheusQueryResponse success(
            final List<PrometheusApiClient.PrometheusSeries> result) {
        final var data = new PrometheusApiClient.PrometheusData("vector", result);
        return new PrometheusApiClient.PrometheusQueryResponse("success", data);
    }

    private static PrometheusApiClient.PrometheusSeries series(final String cluster, final double value) {
        final var metric = new PrometheusApiClient.PrometheusMetric(cluster);
        final var sample = List.<Object>of(1.0d, Double.toString(value));
        return new PrometheusApiClient.PrometheusSeries(metric, sample);
    }

    private static PrometheusApiClient.PrometheusSeries rawSeries(final String cluster, final String value) {
        final var metric = new PrometheusApiClient.PrometheusMetric(cluster);
        final var sample = List.<Object>of(1.0d, value);
        return new PrometheusApiClient.PrometheusSeries(metric, sample);
    }
}
