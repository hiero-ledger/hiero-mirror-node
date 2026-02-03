// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;

@Named
@RequiredArgsConstructor
public final class SchedulerSupplier implements Supplier<Scheduler> {

    private final BlockProperties blockProperties;
    private final LatencyService latencyService;
    private final ManagedChannelBuilderProvider managedChannelBuilderProvider;
    private final MeterRegistry meterRegistry;

    @Override
    public Scheduler get() {
        final var blockNodeProperties = blockProperties.getNodes();
        final var schedulerProperties = blockProperties.getScheduler();
        final var streamProperties = blockProperties.getStream();
        return switch (schedulerProperties.getType()) {
            case LATENCY ->
                new LatencyScheduler(
                        blockNodeProperties,
                        latencyService,
                        managedChannelBuilderProvider,
                        meterRegistry,
                        schedulerProperties,
                        streamProperties);
            case PRIORITY ->
                new PriorityScheduler(
                        blockNodeProperties, managedChannelBuilderProvider, meterRegistry, streamProperties);
            case PRIORITY_THEN_LATENCY ->
                new PriorityAndLatencyScheduler(
                        blockNodeProperties,
                        latencyService,
                        managedChannelBuilderProvider,
                        meterRegistry,
                        schedulerProperties,
                        streamProperties);
        };
    }
}
