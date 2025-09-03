// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class SchedulerSupplierTest {

    @ParameterizedTest
    @EnumSource(SchedulerType.class)
    void get(SchedulerType type) {
        // given
        var blockNodeProperties = new BlockNodeProperties();
        blockNodeProperties.setHost("localhost");
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(type);
        var blockProperties = new BlockProperties();
        blockProperties.setNodes(List.of(blockNodeProperties));
        blockProperties.setScheduler(schedulerProperties);
        var latencyService = mock(LatencyService.class);
        var factory =
                new SchedulerSupplier(blockProperties, latencyService, InProcessManagedChannelBuilderProvider.INSTANCE);

        // when, then
        assertThat(factory.get()).isInstanceOf(getExpectedClass(type));
    }

    private static Class<?> getExpectedClass(SchedulerType type) {
        return switch (type) {
            case LATENCY -> LatencyScheduler.class;
            case PRIORITY -> PriorityScheduler.class;
            case PRIORITY_THEN_LATENCY -> PriorityAndLatencyScheduler.class;
        };
    }
}
