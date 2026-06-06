// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.time.Duration;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@RequiredArgsConstructor
public class DiskSpaceService {

    static final String DISK_USAGE_METRIC_NAME = "db.disk.usage.bytes";
    static final String CITUS_CHECK_QUERY = "select exists(select 1 from pg_extension where extname = 'citus')";
    static final String CITUS_DISK_USAGE_QUERY = "select max(db_size) from ("
            + "select pg_database_size(current_database()) as db_size "
            + "union all "
            + "select result::bigint from run_command_on_workers('select pg_database_size(current_database())') where success"
            + ") t";
    static final String DISK_USAGE_QUERY = "select pg_database_size(current_database())";

    private final DiskSpaceProperties diskSpaceProperties;
    private final JdbcOperations jdbcOperations;
    private final MeterRegistry meterRegistry;

    @Getter
    private volatile boolean exceeded = false;

    private volatile long lastUsedBytes = 0L;
    private String diskUsageQuery = DISK_USAGE_QUERY;

    @PostConstruct
    void init() {
        Gauge.builder(DISK_USAGE_METRIC_NAME, this, s -> s.lastUsedBytes)
                .description("Database disk usage in bytes")
                .baseUnit(BaseUnits.BYTES)
                .register(meterRegistry);

        Boolean isCitus = jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class);
        if (Boolean.TRUE.equals(isCitus)) {
            diskUsageQuery = CITUS_DISK_USAGE_QUERY;
            log.info("Citus extension detected, monitoring disk usage across all nodes");
        }
    }

    @Scheduled(fixedDelayString = "#{@diskSpaceProperties.getCheckFrequency().toMillis()}")
    public void check() {
        if (diskSpaceProperties.getMaxBytes() <= 0) {
            return;
        }

        try {
            Long usedBytes = jdbcOperations.queryForObject(diskUsageQuery, Long.class);
            if (usedBytes == null) {
                return;
            }

            lastUsedBytes = usedBytes;
            boolean exceeded = usedBytes >= diskSpaceProperties.getMaxBytes();

            if (exceeded && !this.exceeded) {
                log.warn(
                        "Database disk usage {} bytes is at or above the threshold of {} bytes, halting ingest",
                        usedBytes,
                        diskSpaceProperties.getMaxBytes());
            } else if (!exceeded && this.exceeded) {
                log.info(
                        "Database disk usage {} bytes is below the threshold of {} bytes, resuming ingest",
                        usedBytes,
                        diskSpaceProperties.getMaxBytes());
            }

            this.exceeded = exceeded;
        } catch (Exception e) {
            log.warn("Unable to query database disk space", e);
        }
    }

    public Duration getCheckFrequency() {
        return diskSpaceProperties.getCheckFrequency();
    }
}
