// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.EnabledIfV2;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.config.MetricsConfiguration.TableMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
@RequiredArgsConstructor
class MetricsConfigurationTest extends ImporterIntegrationTest {

    private final MeterRegistry meterRegistry;
    private final MetricsConfiguration metricsConfiguration;
    private final DomainBuilder domainBuilder;

    @Override
    protected void reset() {
        // Skip so we don't clear the metric
    }

    @EnumSource(TableMetric.class)
    @ParameterizedTest
    void partitionedTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "record_file").gauges()).hasSize(1);
        assertThat(search.tag("table", "record_file_p2020_01").gauges()).isEmpty();
    }

    @EnumSource(TableMetric.class)
    @ParameterizedTest
    void regularTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "node_stake").gauges()).hasSize(1);
    }

    @Test
    public void updatesMetrics() {
        var metric = TableMetric.TABLE_BYTES;
        var search = meterRegistry.find(metric.getMetricName());
        var tag = search.tag("table", "transaction");
        var currentValue = tag.gauge().value();

        domainBuilder.transaction().persist();
        metricsConfiguration.hydrateMetricsCache();

        assertThat(tag.gauge().value()).isGreaterThan(currentValue);
    }

    @EnabledIfV2
    @EnumSource(TableMetric.class)
    @ParameterizedTest
    void distributedPartitionedTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "transaction").gauges()).hasSize(1);
        assertThat(search.tag("table", "transaction_p2020_01").gauges()).isEmpty();
    }

    @EnabledIfV2
    @ParameterizedTest
    @EnumSource(TableMetric.class)
    void distributedTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "transaction_hash").gauges()).hasSize(1);
    }

    @EnabledIfV2
    @ParameterizedTest
    @EnumSource(TableMetric.class)
    void referenceTable(TableMetric metric) {
        var search = meterRegistry.find(metric.getMetricName());
        assertThat(search.tag("table", "account_balance_file").gauges()).hasSize(1);
    }
}
