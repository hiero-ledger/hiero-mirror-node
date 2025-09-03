// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

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

    @Override
    public Scheduler get() {
        var blockNodeProperties = blockProperties.getNodes();
        var schedulerProperties = blockProperties.getScheduler();
        var streamProperties = blockProperties.getStream();
        return switch (schedulerProperties.getType()) {
            case LATENCY ->
                new LatencyScheduler(
                        blockNodeProperties,
                        latencyService,
                        managedChannelBuilderProvider,
                        schedulerProperties,
                        streamProperties);
            case PRIORITY ->
                new PriorityScheduler(blockNodeProperties, managedChannelBuilderProvider, streamProperties);
            case PRIORITY_THEN_LATENCY ->
                new PriorityAndLatencyScheduler(
                        blockNodeProperties,
                        latencyService,
                        managedChannelBuilderProvider,
                        schedulerProperties,
                        streamProperties);
        };
    }
}
