// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.TestUtils.findAllMatches;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import java.util.ArrayList;
import java.util.List;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;

final class MultipleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @Test
    void missingStartBlockInNodeADifferentPriorities() {
        // given
        // - Node A has higher priority, has only blocks [5,6,7] and does NOT have start block 0
        // - Node B has lower priority, has blocks [0,1,2] and should be picked
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(7);
        addSimulatorWithBlocks(blocks.subList(4, 7));
        addSimulatorWithBlocks(blocks.subList(0, 3)).withPriority(1);
        subscriber = getBlockNodeSubscriber();

        // when
        subscriber.get();

        // then
        // should have processed exactly [0,1,2] (from Node B)
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(0L, 1L, 2L);
    }

    @Test
    void missingStartBlockInNodeASamePriorities() {
        // given
        // - Node A has priority 0, but it has only blocks 4,5,6 and does NOT have start block 0
        // - Node B has lower priority, has blocks [0,1,2] and should be picked
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(7);
        addSimulatorWithBlocks(blocks.subList(4, 7));
        addSimulatorWithBlocks(blocks.subList(0, 3));
        subscriber = getBlockNodeSubscriber();

        // when
        subscriber.get();

        // then
        // should have processed exactly 0,1,2
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(0L, 1L, 2L);
    }

    @Test
    void twoNodesChoseByPriority() {
        // given both nodes start at block 0, but higher-priority node has blocks 0,1 and lower-priority node has
        // blocks 2,3,4
        final var generator = new BlockGenerator(0);
        addSimulatorWithBlocks(generator.next(2));
        addSimulatorWithBlocks(generator.next(3)).withPriority(1);
        // Intentionally set lower-priority node first in the list
        subscriber = getBlockNodeSubscriber(true);

        // when
        subscriber.get();

        // then
        // Verify that Exactly 2 blocks were processed (0 and 1) from Node A
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(0L, 1L);
    }

    @Test
    void twoNodesWithSameBlocksChoseByPriority(CapturedOutput output) {
        // given:
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(3);
        addSimulatorWithBlocks(blocks);
        addSimulatorWithBlocks(blocks).withPriority(1);
        // Intentionally set lower-priority node first to ensure priority sorting is actually used
        subscriber = getBlockNodeSubscriber(true);

        // when
        subscriber.get();

        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(0L, 1L, 2L);

        // then
        // Verify that the logs the high-priority node's port
        final var nodeAPort = simulators.getFirst().getPort();
        assertThat(findAllMatches(output.getAll(), "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(String.format("Start streaming block 0 from BlockNode(localhost:%d)", nodeAPort));
    }

    @Test
    void switchFromNodeAToNodeCWhenHigherPriorityLacksNextBlock(CapturedOutput output) {
        // given
        // - Node A has priority 0 and has only block 0
        // - Node B has priority 1 and does NOT have block 1
        // - Node C has priority 2 and has blocks 1 and 2
        final var firstGenerator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(firstGenerator.next(6));
        addSimulatorWithBlocks(blocks.subList(0, 1));
        addSimulatorWithBlocks(blocks.subList(4, 6)).withPriority(1);
        addSimulatorWithBlocks(blocks.subList(1, 3)).withPriority(2);
        subscriber = getBlockNodeSubscriber();

        // Attempt 1:  should pick A and process only block 0
        subscriber.get();

        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier).verified(captor.capture());

        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(0L);
        clearInvocations(streamFileNotifier);

        final var nodeAPort = simulators.get(0).getPort();
        final var nodeCPort = simulators.get(2).getPort();

        // Attempt 2: next block is 1 - Nodes A and B don't have it so Node C must be chosen
        subscriber.get();

        // Verify that block 1 is processed from Node C
        assertThat(findAllMatches(output.getAll(), "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("Start streaming block 0 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeCPort));

        // Verify that blocks 1 and 2 are verified exactly once
        captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(1L, 2L);
    }

    @Test
    void startsStreamingAtSpecificStartBlockNumber(CapturedOutput output) {
        // given
        final var generator = new BlockGenerator(0);
        addSimulatorWithBlocks(generator.next(8));
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(5L);
        subscriber = getBlockNodeSubscriber();

        // when
        subscriber.get();

        // Verify that only blocks 5, 6 and 7 were verified
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(5L, 6L, 7L);

        // Verify that logs explicitly show that it starts processing from block 5
        final var port = simulators.getFirst().getPort();
        assertThat(findAllMatches(output.getAll(), "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(String.format("Start streaming block 5 from BlockNode(localhost:%d)", port));
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockSamePriorities(CapturedOutput output) {
        // given
        // - Node A has priority 0 and only block 0
        // - Node B has priority 1 and only block 1
        // - Node C priority 0 only block 2
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(3);
        addSimulatorWithBlocks(blocks.subList(0, 1));
        addSimulatorWithBlocks(blocks.subList(1, 2));
        addSimulatorWithBlocks(blocks.subList(2, 3));
        subscriber = getBlockNodeSubscriber();

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 0));
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 1));
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 2));

        final var nodeAPort = simulators.get(0).getPort();
        final var nodeBPort = simulators.get(1).getPort();
        final var nodeCPort = simulators.get(2).getPort();
        assertThat(findAllMatches(output.getAll(), "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("Start streaming block 0 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeBPort),
                        String.format("Start streaming block 2 from BlockNode(localhost:%d)", nodeCPort));
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockDifferentPriorities(CapturedOutput output) {
        // given
        // - Node A has priority 0 and only block 0
        // - Node B has priority 1 and only block 1
        // - Node C priority 0 only block 2
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(3);
        addSimulatorWithBlocks(blocks.subList(0, 1));
        addSimulatorWithBlocks(blocks.subList(1, 2)).withPriority(1);
        addSimulatorWithBlocks(blocks.subList(2, 3)).withPriority(2);
        subscriber = getBlockNodeSubscriber();

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 0));
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 1));
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 2));

        final var nodeAPort = simulators.get(0).getPort();
        final var nodeBPort = simulators.get(1).getPort();
        final var nodeCPort = simulators.get(2).getPort();
        assertThat(findAllMatches(output.getAll(), "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("Start streaming block 0 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeBPort),
                        String.format("Start streaming block 2 from BlockNode(localhost:%d)", nodeCPort));
    }

    @Test
    void switchesToLowerPriorityWhenHigherPriorityHasMalformedBlock(CapturedOutput output) {
        // given
        // - Node A has priority 0, block 0 and malformed block 1
        // - Node B has priority 1 and healthy [0,1,2]
        final var generator = new BlockGenerator(0);
        final var blocks = generator.next(3);

        // Corrupt block #1 on Node A by removing its BLOCK_HEADER
        final var firstBlock = blocks.get(1);
        final var itemsNoHeader = firstBlock.block().getBlockItemsList().stream()
                .filter(it -> it.getItemCase() != BlockItem.ItemCase.BLOCK_HEADER)
                .toList();
        final var block1NoHeader =
                BlockItemSet.newBuilder().addAllBlockItems(itemsNoHeader).build();

        addSimulatorWithBlocks(List.of(blocks.get(0), new BlockGenerator.BlockRecord(block1NoHeader), blocks.get(2)));
        addSimulatorWithBlocks(blocks).withPriority(1);
        subscriber = getBlockNodeSubscriber();

        // Attempt 1: streams block 0 from A, then fails on malformed block 1
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Incorrect first block item case ROUND_HEADER");
        verify(streamFileNotifier).verified(argThat(rf -> rf.getIndex() == 0L));

        // Attempts 2 and 3: keep failing on A (hits failure threshold)
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Incorrect first block item case ROUND_HEADER");
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Incorrect first block item case ROUND_HEADER");
        clearInvocations(streamFileNotifier);

        // Attempt 4: should switch to B and successfully stream blocks 1 and 2
        subscriber.get();
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());
        assertThat(captor.getAllValues()).extracting(RecordFile::getIndex).containsExactly(1L, 2L);

        String logs = output.getAll();
        final var nodeAPort = simulators.get(0).getPort();
        final var nodeBPort = simulators.get(1).getPort();
        assertThat(findAllMatches(
                        logs, "Marking connection to BlockNode\\(localhost:\\d+\\) as inactive after 3 attempts"))
                .containsExactly(String.format(
                        "Marking connection to BlockNode(localhost:%d) as inactive after 3 attempts", nodeAPort));
        assertThat(findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("Start streaming block 0 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeAPort),
                        String.format("Start streaming block 1 from BlockNode(localhost:%d)", nodeBPort));
    }
}
