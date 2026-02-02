// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({OutputCaptureExtension.class})
final class MultipleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @AutoClose
    private BlockNodeSimulator nodeASimulator;

    @AutoClose
    private BlockNodeSimulator nodeBSimulator;

    @AutoClose
    private BlockNodeSimulator nodeCSimulator;

    @AutoClose
    private BlockNodeSubscriber subscriber;

    @Resource
    private CommonDownloaderProperties commonDownloaderProperties;

    @Test
    void missingStartBlockInNodeADifferentPriorities() {
        // given
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(7));

        // Node A has higher priority, has only blocks [5,6,7] and does NOT have start block 0
        nodeASimulator = startBlockNodeSimulatorWithBlocks(blocks.subList(4, 7));

        // Node B has lower priority, has blocks [0,1,2] and should be picked
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(blocks.subList(0, 3));

        // Set priorities
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));
        // when
        subscriber.get();

        // then
        // should have processed exactly [0,1,2] (from Node B)
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);
    }

    @Test
    void missingStartBlockInNodeASamePriorities() {
        // given:
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(7));

        // Node A has priority 0, but it has only blocks 5,6,7 and does NOT have start block 0
        nodeASimulator = startBlockNodeSimulatorWithBlocks(blocks.subList(4, 7));

        // Node B has lower priority, has blocks [0,1,2] and should be picked
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(blocks.subList(0, 3));

        // Set same priorities
        final var firstSimulatorProperties = nodeASimulator.toClientProperties();
        firstSimulatorProperties.setPriority(0);

        final var secondSimulatorProperties = nodeBSimulator.toClientProperties();
        secondSimulatorProperties.setPriority(0);

        subscriber = getBlockNodeSubscriber(List.of(firstSimulatorProperties, secondSimulatorProperties));
        // when
        subscriber.get();

        // then
        // should have processed exactly 0,1,2
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);
    }

    @Test
    void twoNodesChoseByPriority() {
        // given
        final var generator = new BlockGenerator(0);

        // Both nodes start at block 0, but higher-priority node has fewer blocks (0,1)
        nodeASimulator = startBlockNodeSimulatorWithBlocks(generator.next(2));

        nodeBSimulator = startBlockNodeSimulatorWithBlocks(generator.next(3));

        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);
        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Intentionally set lower-priority node first in the list
        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));
        // when
        subscriber.get();

        // then
        // Verify that Exactly 2 blocks were processed (0 and 1) from Node A
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();

        assertThat(indices).containsExactly(0L, 1L);
    }

    @Test
    void twoNodesWithSameBlocksChoseByPriority(CapturedOutput output) {
        // given:
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(3));

        // node A (priority 0)
        nodeASimulator = startBlockNodeSimulatorWithBlocks(blocks);

        // node B (priority 1)
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(blocks);

        // Set priorities
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Intentionally set lower-priority node first to ensure priority sorting is actually used
        subscriber = getBlockNodeSubscriber(List.of(nodeBProperties, nodeAProperties));

        // when
        subscriber.get();

        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);

        String logs = output.getAll();
        final var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        // then
        // Verify that the logs the high-priority node's port
        final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());
        final var nodeBPort = String.valueOf(nodeBProperties.getStatusPort());

        assertThat(nodeLogs).containsExactly("Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ");
        assertThat(nodeLogs).doesNotContain(nodeBPort);
    }

    @Test
    void switchFromNodeAToNodeCWhenHigherPriorityLacksNextBlock(CapturedOutput output) {

        final var firstGenerator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(firstGenerator.next(3));

        // Node A has priority 0 and has only block 0
        nodeASimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(0)));

        // Node B has priority 1 and does NOT have block 1
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(new BlockGenerator(5).next(3));

        // Node C has priority 2 and has blocks 1 and 2
        nodeCSimulator = startBlockNodeSimulatorWithBlocks(blocks.subList(1, 3));

        // Set priorities
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);
        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);
        final var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(2);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties, nodeCProperties));

        // Attempt 1:  should pick A and process only block 0
        subscriber.get();

        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        assertThat(indices).doesNotContain(1L, 2L);
        clearInvocations(streamFileNotifier);

        final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());
        final var nodeBPort = String.valueOf(nodeBProperties.getStatusPort());
        final var nodeCPort = String.valueOf(nodeCProperties.getStatusPort());

        // Attempt 2: next block is 1 - Nodes A and B don't have it so Node C must be chosen
        subscriber.get();

        // Verify that block 1 is processed from Node C
        final var logs = output.getAll();
        final var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");
        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeCPort + ") ");
        assertThat(nodeLogs).doesNotContain(nodeBPort);

        // Verify that blocks 1 and 2 are verified exactly once
        final var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(secondCaptor.capture());

        final var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L, 2L);
    }

    @Test
    void startsStreamingAtSpecificStartBlockNumber(CapturedOutput output) {
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(8));

        nodeASimulator = startBlockNodeSimulatorWithBlocks(blocks);
        final var nodeAProperties = nodeASimulator.toClientProperties();

        // Save initial startBlockNumber to avoid state mismatch with other tests
        final var properties = commonDownloaderProperties.getImporterProperties();
        final var initialStartBlockNumber = properties.getStartBlockNumber();

        // Set new start block number
        properties.setStartBlockNumber(5L);

        subscriber = getBlockNodeSubscriber(List.of(nodeASimulator.toClientProperties()));

        try {
            subscriber.get();
            // Verify that only blocks 5,6 and 7 were verified
            final var captor = ArgumentCaptor.forClass(RecordFile.class);
            verify(streamFileNotifier, times(3)).verified(captor.capture());

            final var indices =
                    captor.getAllValues().stream().map(RecordFile::getIndex).toList();
            assertThat(indices).containsExactly(5L, 6L, 7L);

            // Verify that logs explicitly show that it starts processing from block 5
            String logs = output.getAll();
            final var nodeLogs =
                    findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

            final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());

            assertThat(nodeLogs)
                    .containsExactly("Start streaming block 5 from BlockNode(localhost:" + nodeAPort + ") ");

        } finally {
            properties.setStartBlockNumber(initialStartBlockNumber);
        }
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockSamePriorities(CapturedOutput output) {
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(3));

        // Node A has priority 0 and only block 0
        nodeASimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(0)));
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and only block 1
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(1)));
        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(0);

        // Node C priority 0 only block 2
        nodeCSimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(2)));
        final var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(0);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties, nodeCProperties));

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        final var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(secondCaptor.capture());

        final var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L);
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        final var thirdCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(thirdCaptor.capture());

        final var thirdIndices =
                thirdCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(thirdIndices).containsExactly(2L);

        String logs = output.getAll();
        final var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());
        final var nodeBPort = String.valueOf(nodeBProperties.getStatusPort());
        final var nodeCPort = String.valueOf(nodeCProperties.getStatusPort());

        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeBPort + ") ",
                        "Start streaming block 2 from BlockNode(localhost:" + nodeCPort + ") ");
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockDifferentPriorities(CapturedOutput output) {
        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(3));

        //        nodeASimulator = createBlockNodeSimulatorWithBlocks(0, List.of(blocks.get(0)));

        // Node A has priority 0 and only block 0
        nodeASimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(0)));
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and only block 1
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(1)));
        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Node C priority 0 only block 2
        nodeCSimulator = startBlockNodeSimulatorWithBlocks(List.of(blocks.get(2)));
        final var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(2);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeCProperties, nodeBProperties));

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        final var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(secondCaptor.capture());

        final var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L);
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        final var thirdCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(thirdCaptor.capture());

        final var thirdIndices =
                thirdCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(thirdIndices).containsExactly(2L);

        String logs = output.getAll();
        final var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());
        final var nodeBPort = String.valueOf(nodeBProperties.getStatusPort());
        final var nodeCPort = String.valueOf(nodeCProperties.getStatusPort());

        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeBPort + ") ",
                        "Start streaming block 2 from BlockNode(localhost:" + nodeCPort + ") ");
    }

    @Test
    void switchesToLowerPriorityWhenHigherPriorityHasMalformedBlock(CapturedOutput output) {

        final var generator = new BlockGenerator(0);
        final var blocks = new ArrayList<>(generator.next(3));

        // Corrupt block #1 on Node A by removing its BLOCK_HEADER
        final var firstBlock = blocks.get(1);
        final var itemsNoHeader = firstBlock.getBlockItemsList().stream()
                .filter(it -> it.getItemCase() != BlockItem.ItemCase.BLOCK_HEADER)
                .toList();
        final var block1NoHeader =
                BlockItemSet.newBuilder().addAllBlockItems(itemsNoHeader).build();

        // Node A has priority 0, block 0 and malformed block 1
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0), block1NoHeader, blocks.get(2)))
                .withHttpChannel()
                .start();
        final var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and healthy [0,1,2]
        nodeBSimulator = startBlockNodeSimulatorWithBlocks(blocks);
        final var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));

        // Attempt 1: streams block 0 from A, then fails on malformed block 1
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasNoCause()
                .hasMessageContaining("Incorrect first block item case")
                .hasMessageContaining("ROUND_HEADER");
        verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 0L));

        // Attempts 2 and 3: keep failing on A (hits failure threshold)
        assertThatThrownBy(subscriber::get).isInstanceOf(BlockStreamException.class);
        assertThatThrownBy(subscriber::get).isInstanceOf(BlockStreamException.class);
        clearInvocations(streamFileNotifier);

        // Attempt 4: should switch to B and successfully stream blocks 1 and 2
        subscriber.get();
        final var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());

        final var indices =
                captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(1L, 2L);

        String logs = output.getAll();
        final var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");
        final var nodeLogsMarkedInactive = findAllMatches(
                logs, "Marking connection to BlockNode\\(localhost:\\d+\\) as inactive after 3 attempts");

        final var nodeAPort = String.valueOf(nodeAProperties.getStatusPort());
        final var nodeBPort = String.valueOf(nodeBProperties.getStatusPort());

        assertThat(nodeLogsMarkedInactive)
                .containsExactly(
                        "Marking connection to BlockNode(localhost:" + nodeAPort + ") as inactive after 3 attempts");
        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeBPort + ") ");
    }

    private Collection<String> findAllMatches(String message, String pattern) {
        var matcher = Pattern.compile(pattern).matcher(message);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private BlockNodeSimulator startBlockNodeSimulatorWithBlocks(List<BlockItemSet> blocks) {
        return new BlockNodeSimulator().withBlocks(blocks).withHttpChannel().start();
    }
}
