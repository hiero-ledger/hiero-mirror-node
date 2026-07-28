// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.unit.DataSize;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DiskSpaceServiceTest {

    private static final String CITUS_CHECK_QUERY = DiskSpaceService.CITUS_CHECK_QUERY;
    private static final String CITUS_DISK_USAGE_QUERY = DiskSpaceService.CITUS_DISK_USAGE_QUERY;
    private static final String DISK_USAGE_QUERY = DiskSpaceService.DISK_USAGE_QUERY;
    private static final List<DataSize> MAX_DATABASE_SIZES = List.of(DataSize.ofBytes(1000L));

    @Mock
    private JdbcOperations jdbcOperations;

    private DiskSpaceProperties diskSpaceProperties;
    private DiskSpaceService diskSpaceService;

    @BeforeEach
    void setup() {
        diskSpaceProperties = new DiskSpaceProperties();
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setCheckFrequency(Duration.ofMillis(200));
        when(jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class)).thenReturn(false);
        diskSpaceService = new DiskSpaceService(diskSpaceProperties, jdbcOperations, new SimpleMeterRegistry());
        diskSpaceService.init();
        clearInvocations(jdbcOperations);
    }

    @Test
    void defaultsToHavingEnoughSpace() {
        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void maxDatabaseSizesEmptySkipsQuery() {
        diskSpaceProperties.setMaxDatabaseSizes(Collections.emptyList());

        diskSpaceService.check();

        verifyNoInteractions(jdbcOperations);
        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void disabledSkipsQuery() {
        diskSpaceProperties.setEnabled(false);
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);

        diskSpaceService.check();

        verifyNoInteractions(jdbcOperations);
        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void belowThresholdHasEnoughSpace() {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(500L));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void atThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(950L));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isTrue();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void aboveThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(1500L));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isTrue();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void recoversWhenSpaceFreed(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(1500L));
        diskSpaceService.check();
        assertThat(diskSpaceService.isExceeded()).isTrue();

        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(800L));
        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("resuming ingest");
    }

    @Test
    void warnThresholdLogsWarningWithoutHalting(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(850L));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("at or above the warn threshold");
        assertThat(output.getAll()).doesNotContain("halting ingest");
    }

    @Test
    void warnClearedWhenUsageDropsBelowWarnThreshold(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(850L));
        diskSpaceService.check();

        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(700L));
        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("below the warn threshold");
    }

    @Test
    void queryExceptionKeepsPreviousState(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class))
                .thenThrow(new RuntimeException("connection error"));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("Unable to query database disk space");
    }

    @Test
    void nullResultKeepsPreviousState() {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(Arrays.asList((Long) null));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void warnLoggedOnlyOnStateChange(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForList(DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(1500L));

        diskSpaceService.check();
        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isTrue();
        long warnCount = output.getAll()
                .lines()
                .filter(l -> l.contains("halting ingest"))
                .count();
        assertThat(warnCount).isEqualTo(1);
    }

    @Test
    void citusUsesDistributedQuery(CapturedOutput output) {
        diskSpaceProperties.setMaxDatabaseSizes(MAX_DATABASE_SIZES);
        when(jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class)).thenReturn(true);
        when(jdbcOperations.queryForList(CITUS_DISK_USAGE_QUERY, Long.class)).thenReturn(List.of(1500L));
        var service = new DiskSpaceService(diskSpaceProperties, jdbcOperations, new SimpleMeterRegistry());
        service.init();

        service.check();

        assertThat(service.isExceeded()).isTrue();
        assertThat(output.getAll()).contains("Citus extension detected");
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void metricsRegistered() {
        var registry = new SimpleMeterRegistry();
        when(jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class)).thenReturn(false);
        var service = new DiskSpaceService(diskSpaceProperties, jdbcOperations, registry);
        service.init();

        assertThat(registry.find(DiskSpaceService.DISK_USAGE_METRIC_NAME).gauge())
                .isNotNull();
    }
}
