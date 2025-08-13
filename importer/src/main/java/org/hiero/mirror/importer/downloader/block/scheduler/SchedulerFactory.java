// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;

@Named
@RequiredArgsConstructor
public final class SchedulerFactory {

    private final BlockProperties blockProperties;
    private final LatencyService latencyService;
    private final ManagedChannelBuilderProvider managedChannelBuilderProvider;

    public Scheduler getScheduler(Collection<BlockNodeProperties> blockNodeProperties, SchedulerType type) {
        return switch (type) {
            case LATENCY ->
                new LatencyScheduler(
                        blockNodeProperties, blockProperties, latencyService, managedChannelBuilderProvider);
            case PRIORITY ->
                new PriorityScheduler(blockNodeProperties, managedChannelBuilderProvider, blockProperties.getStream());
            case PRIORITY_THEN_LATENCY ->
                new PriorityAndLatencyScheduler(
                        blockNodeProperties, blockProperties, latencyService, managedChannelBuilderProvider);
        };
    }
}
