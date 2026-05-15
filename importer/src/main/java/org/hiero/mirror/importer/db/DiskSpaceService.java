// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@RequiredArgsConstructor
public class DiskSpaceService {

    static final String DISK_USAGE_METRIC_NAME = "hiero.mirror.importer.db.disk.usage";
    private static final String DISK_USAGE_QUERY = "select pg_database_size(current_database())";

    private final DiskSpaceProperties diskSpaceProperties;
    private final JdbcOperations jdbcOperations;
    private final MeterRegistry meterRegistry;

    private volatile boolean hasEnoughSpace = true;
    private volatile long lastUsedBytes = 0L;

    @PostConstruct
    void init() {
        Gauge.builder(DISK_USAGE_METRIC_NAME, this, s -> s.lastUsedBytes)
                .description("Database disk usage in bytes")
                .baseUnit(BaseUnits.BYTES)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "#{@diskSpaceProperties.getCheckFrequency().toMillis()}")
    public void check() {
        if (!diskSpaceProperties.isEnabled() || diskSpaceProperties.getMaxBytes() <= 0) {
            hasEnoughSpace = true;
            return;
        }

        try {
            Long usedBytes = jdbcOperations.queryForObject(DISK_USAGE_QUERY, Long.class);
            if (usedBytes == null) {
                return;
            }

            lastUsedBytes = usedBytes;
            boolean exceeded = usedBytes >= diskSpaceProperties.getMaxBytes();

            if (exceeded && hasEnoughSpace) {
                log.warn(
                        "Database disk usage {} bytes is at or above the threshold of {} bytes, halting ingest",
                        usedBytes,
                        diskSpaceProperties.getMaxBytes());
            } else if (!exceeded && !hasEnoughSpace) {
                log.info(
                        "Database disk usage {} bytes is below the threshold of {} bytes, resuming ingest",
                        usedBytes,
                        diskSpaceProperties.getMaxBytes());
            }

            hasEnoughSpace = !exceeded;
        } catch (Exception e) {
            log.warn("Unable to query database disk space", e);
        }
    }

    public boolean hasEnoughSpace() {
        return hasEnoughSpace;
    }
}
