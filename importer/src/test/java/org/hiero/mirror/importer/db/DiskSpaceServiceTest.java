// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcOperations;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DiskSpaceServiceTest {

    private static final String DISK_USAGE_QUERY = "select pg_database_size(current_database())";

    @Mock
    private JdbcOperations jdbcOperations;

    private DiskSpaceProperties diskSpaceProperties;
    private DiskSpaceService diskSpaceService;

    @BeforeEach
    void setup() {
        diskSpaceProperties = new DiskSpaceProperties();
        diskSpaceProperties.setCheckFrequency(Duration.ofSeconds(30));
        diskSpaceService = new DiskSpaceService(diskSpaceProperties, jdbcOperations, new SimpleMeterRegistry());
        diskSpaceService.init();
    }

    @Test
    void defaultsToHavingEnoughSpace() {
        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
    }

    @Test
    void disabledSkipsQuery() {
        diskSpaceProperties.setEnabled(false);

        diskSpaceService.check();

        verifyNoInteractions(jdbcOperations);
        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
    }

    @Test
    void maxBytesZeroSkipsQuery() {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(0);

        diskSpaceService.check();

        verifyNoInteractions(jdbcOperations);
        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
    }

    @Test
    void belowThresholdHasEnoughSpace() {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(500L);

        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
    }

    @Test
    void atThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1000L);

        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isFalse();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void aboveThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);

        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isFalse();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void recoversWhenSpaceFreed(CapturedOutput output) {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);
        diskSpaceService.check();
        assertThat(diskSpaceService.hasEnoughSpace()).isFalse();

        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(800L);
        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
        assertThat(output.getAll()).contains("resuming ingest");
    }

    @Test
    void queryExceptionKeepsPreviousState(CapturedOutput output) {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class))
                .thenThrow(new RuntimeException("connection error"));

        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
        assertThat(output.getAll()).contains("Unable to query database disk space");
    }

    @Test
    void nullResultKeepsPreviousState() {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(null);

        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isTrue();
    }

    @Test
    void warnLoggedOnlyOnStateChange(CapturedOutput output) {
        diskSpaceProperties.setEnabled(true);
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);

        diskSpaceService.check();
        diskSpaceService.check();

        assertThat(diskSpaceService.hasEnoughSpace()).isFalse();
        long warnCount = output.getAll()
                .lines()
                .filter(l -> l.contains("halting ingest"))
                .count();
        assertThat(warnCount).isEqualTo(1);
    }

    @Test
    void metricsRegistered() {
        var registry = new SimpleMeterRegistry();
        var service = new DiskSpaceService(diskSpaceProperties, jdbcOperations, registry);
        service.init();

        assertThat(registry.find(DiskSpaceService.DISK_USAGE_METRIC_NAME).gauge())
                .isNotNull();
    }
}
