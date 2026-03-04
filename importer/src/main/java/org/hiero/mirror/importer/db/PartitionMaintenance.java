// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.config.Owner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
@RequiredArgsConstructor
public class PartitionMaintenance {

    private static final String RUN_MAINTENANCE_QUERY = "call create_mirror_node_time_partitions()";

    @Owner
    private final JdbcTemplate jdbcTemplate;

    private final PartitionProperties partitionProperties;

    @EventListener(ApplicationReadyEvent.class)
    @Retryable
    @Scheduled(cron = "${hiero.mirror.importer.db.partition.cron:0 0 0 * * ?}")
    public synchronized void runMaintenance() {
        if (!partitionProperties.isEnabled()) {
            return;
        }

        log.info("Running partition maintenance");
        Stopwatch stopwatch = Stopwatch.createStarted();
        jdbcTemplate.execute(RUN_MAINTENANCE_QUERY);
        log.info("Partition maintenance completed successfully in {}", stopwatch);
    }
}
