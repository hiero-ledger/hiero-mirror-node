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
    static final String CITUS_DISK_USAGE_QUERY = "select pg_database_size(current_database()) as db_size "
            + "union all "
            + "select result::bigint from run_command_on_workers('select pg_database_size(current_database())') where success";
    static final String DISK_USAGE_QUERY = "select pg_database_size(current_database())";

    private final DiskSpaceProperties diskSpaceProperties;
    private final JdbcOperations jdbcOperations;
    private final MeterRegistry meterRegistry;

    @Getter
    private volatile boolean exceeded = false;

    private volatile boolean warned = false;
    private volatile long lastUsedBytes = 0L;
    private String diskUsageQuery = DISK_USAGE_QUERY;

    @PostConstruct
    void init() {
        Gauge.builder(DISK_USAGE_METRIC_NAME, this, service -> service.lastUsedBytes)
                .description("Database disk usage in bytes")
                .baseUnit(BaseUnits.BYTES)
                .register(meterRegistry);

        final var isCitus = jdbcOperations.queryForObject(CITUS_CHECK_QUERY, Boolean.class);
        if (Boolean.TRUE.equals(isCitus)) {
            diskUsageQuery = CITUS_DISK_USAGE_QUERY;
            log.info("Citus extension detected, monitoring disk usage across all nodes");
        }
    }

    @Scheduled(fixedDelayString = "#{@diskSpaceProperties.getCheckFrequency().toMillis()}")
    public void check() {
        final var maxDatabaseSizes = diskSpaceProperties.getMaxDatabaseSizes();
        if (!diskSpaceProperties.isEnabled() || maxDatabaseSizes.isEmpty()) {
            return;
        }

        try {
            final var usedBytesByNode = jdbcOperations.queryForList(diskUsageQuery, Long.class);
            if (usedBytesByNode.isEmpty()) {
                return;
            }

            final var threshold = diskSpaceProperties.getThreshold();
            final var haltPercentage = threshold.getHalt();
            final var warnPercentage = threshold.getWarn();
            long maxUsedBytes = 0L;
            boolean anyHalted = false;
            boolean anyWarned = false;

            for (int i = 0; i < usedBytesByNode.size() && i < maxDatabaseSizes.size(); i++) {
                final var usedBytes = usedBytesByNode.get(i);
                if (usedBytes == null) {
                    continue;
                }

                final var maxBytes = maxDatabaseSizes.get(i).toBytes();
                final var haltBytes = maxBytes * haltPercentage / 100;
                final var warnBytes = maxBytes * warnPercentage / 100;

                maxUsedBytes = Math.max(maxUsedBytes, usedBytes);
                anyHalted |= usedBytes >= haltBytes;
                anyWarned |= usedBytes >= warnBytes;
            }

            lastUsedBytes = maxUsedBytes;
            final var wasExceeded = this.exceeded;
            final var wasWarned = this.warned;
            this.exceeded = anyHalted;
            this.warned = anyWarned;

            if (this.exceeded && !wasExceeded) {
                log.warn(
                        "Database disk usage {} bytes is at or above the halt threshold, halting ingest", maxUsedBytes);
            } else if (!this.exceeded && wasExceeded) {
                log.info("Database disk usage {} bytes is below the halt threshold, resuming ingest", maxUsedBytes);
            } else if (!this.exceeded && this.warned && !wasWarned) {
                log.warn("Database disk usage {} bytes is at or above the warn threshold", maxUsedBytes);
            } else if (!this.exceeded && !this.warned && wasWarned) {
                log.info("Database disk usage {} bytes is below the warn threshold", maxUsedBytes);
            }
        } catch (Exception e) {
            log.warn("Unable to query database disk space", e);
        }
    }

    public Duration getCheckFrequency() {
        return diskSpaceProperties.getCheckFrequency();
    }
}
