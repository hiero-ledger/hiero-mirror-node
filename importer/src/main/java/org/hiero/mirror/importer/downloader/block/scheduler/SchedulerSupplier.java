// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;

@Named
@RequiredArgsConstructor
public final class SchedulerSupplier implements Supplier<Scheduler> {

    private final BlockNodeDiscoveryService blockNodeDiscoveryService;
    private final BlockProperties blockProperties;
    private final LatencyService latencyService;
    private final ManagedChannelBuilderProvider managedChannelBuilderProvider;
    private final MeterRegistry meterRegistry;
    private final SchedulerProperties schedulerProperties;

    @Override
    public Scheduler get() {
        final var streamProperties = blockProperties.getStream();
        return switch (schedulerProperties.getType()) {
            case LATENCY ->
                new LatencyScheduler(
                        blockNodeDiscoveryService,
                        managedChannelBuilderProvider,
                        latencyService,
                        meterRegistry,
                        schedulerProperties,
                        streamProperties);
            case PRIORITY ->
                new PriorityScheduler(
                        blockNodeDiscoveryService, managedChannelBuilderProvider, meterRegistry, streamProperties);
            case PRIORITY_THEN_LATENCY ->
                new PriorityAndLatencyScheduler(
                        blockNodeDiscoveryService,
                        managedChannelBuilderProvider,
                        latencyService,
                        meterRegistry,
                        schedulerProperties,
                        streamProperties);
        };
    }
}
