// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class MultipleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    private BlockNodeSimulator firstSimulator;
    private BlockNodeSimulator secondSimulator;
    private BlockNodeSubscriber subscriber;

    @AfterEach
    void cleanup() {
        if (subscriber != null) {
            subscriber.close();
            subscriber = null;
        }

        if (firstSimulator != null) {
            firstSimulator.close();
            firstSimulator = null;
        }

        if (secondSimulator != null) {
            secondSimulator.close();
            secondSimulator = null;
        }
    }

    @Test
    void missingStartBlockInHighPriorityNode() {
        // given:
        // simA (higher priority) has only blocks 5..7 → does NOT have start block 0
        // simB (lower priority) has blocks 0..2 → should be picked
        var genA = new BlockGenerator(5);
        firstSimulator = new BlockNodeSimulator()
                .withBlocks(genA.next(3))   // 5,6,7
                .withHttpChannel()
                .start();

        var genB = new BlockGenerator(0);
        secondSimulator = new BlockNodeSimulator()
                .withBlocks(genB.next(3))   // 0,1,2
                .withHttpChannel()
                .start();

        var firstSimulatorProperties = firstSimulator.toClientProperties();
        firstSimulatorProperties.setPriority(0);

        var secondSimulatorProperties = secondSimulator.toClientProperties();
        secondSimulatorProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(firstSimulatorProperties, secondSimulatorProperties));
        subscriber.get();

        // then: we should have streamed exactly 0,1,2 (from simB)
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        org.assertj.core.api.Assertions.assertThat(indices).containsExactly(0L, 1L, 2L);

    }
}
