// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class MultipleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @AutoClose
    private BlockNodeSimulator firstSimulator;

    @AutoClose
    private BlockNodeSimulator secondSimulator;

    @AutoClose
    private BlockNodeSubscriber subscriber;

    @Test
    void missingStartBlockInHighPriorityNode() {
        // given:
        // firstGenerator (higher priority) has only blocks 56,7 → does NOT have start block 0
        var firstGenerator = new BlockGenerator(5);
        firstSimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
                .withHttpChannel()
                .start();
        // secondGenerator (lower priority) has blocks 0,1,2 → should be picked
        var secondGenerator = new BlockGenerator(0);
        secondSimulator = new BlockNodeSimulator()
                .withBlocks(secondGenerator.next(3))
                .withHttpChannel()
                .start();

        // Set priorities
        var firstSimulatorProperties = firstSimulator.toClientProperties();
        firstSimulatorProperties.setPriority(0);

        var secondSimulatorProperties = secondSimulator.toClientProperties();
        secondSimulatorProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(firstSimulatorProperties, secondSimulatorProperties));
        // when
        subscriber.get();

        // then
        // should have streamed exactly 0,1,2 (from secondSimulator)
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);
    }
}
