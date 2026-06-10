// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
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

    private static final String CITUS_CHECK_QUERY = DiskSpaceService.CITUS_CHECK_QUERY;
    private static final String CITUS_DISK_USAGE_QUERY = DiskSpaceService.CITUS_DISK_USAGE_QUERY;
    private static final String DISK_USAGE_QUERY = DiskSpaceService.DISK_USAGE_QUERY;

    @Mock
    private JdbcOperations jdbcOperations;

    private DiskSpaceProperties diskSpaceProperties;
    private DiskSpaceService diskSpaceService;

    @BeforeEach
    void setup() {
        diskSpaceProperties = new DiskSpaceProperties();
        diskSpaceProperties.setCheckFrequency(Duration.ofSeconds(30));
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
    void maxBytesZeroSkipsQuery() {
        diskSpaceProperties.setMaxBytes(0);

        diskSpaceService.check();

        verifyNoInteractions(jdbcOperations);
        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void belowThresholdHasEnoughSpace() {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(500L);

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void atThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1000L);

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isTrue();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void aboveThresholdHaltsIngest(CapturedOutput output) {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isTrue();
        assertThat(output.getAll()).contains("halting ingest");
    }

    @Test
    void recoversWhenSpaceFreed(CapturedOutput output) {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);
        diskSpaceService.check();
        assertThat(diskSpaceService.isExceeded()).isTrue();

        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(800L);
        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("resuming ingest");
    }

    @Test
    void queryExceptionKeepsPreviousState(CapturedOutput output) {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class))
                .thenThrow(new RuntimeException("connection error"));

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
        assertThat(output.getAll()).contains("Unable to query database disk space");
    }

    @Test
    void nullResultKeepsPreviousState() {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(null);

        diskSpaceService.check();

        assertThat(diskSpaceService.isExceeded()).isFalse();
    }

    @Test
    void warnLoggedOnlyOnStateChange(CapturedOutput output) {
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);

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
        diskSpaceProperties.setMaxBytes(1000L);
        when(jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class)).thenReturn(true);
        when(jdbcOperations.queryForObject(CITUS_DISK_USAGE_QUERY, Long.class)).thenReturn(1500L);
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
