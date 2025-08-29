// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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

    @Resource
    private RecordFileRepository recordFileRepository;

    @Resource
    private BlockFileTransformer blockFileTransformer;

    @Resource
    private BlockStreamReader blockStreamReader;

    @Resource
    private ManagedChannelBuilderProvider managedChannelBuilderProvider;

    @Mock
    private BlockStreamVerifier blockStreamVerifier;

    @Mock
    private BlockFileSource fileSource;

    @Test
    void missingStartBlockInNodeADifferentPriorities() {
        // given
        // Node A has higher priority, has only blocks [5,6,7] and does NOT have start block 0
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(7));
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(4), blocks.get(5), blocks.get(6)))
                .withHttpChannel()
                .start();

        // Node B has lower priority, has blocks [0,1,2] and should be picked
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0), blocks.get(1), blocks.get(2)))
                .withHttpChannel()
                .start();

        // Set priorities
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));
        // when
        subscriber.get();

        // then
        // should have processed exactly [0,1,2] (from Node B)
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);
    }

    @Test
    void missingStartBlockInNodeASamePriorities() {
        // given:
        // Node A has priority 0, but it has only blocks 5,6,7 and does NOT have start block 0
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(7));
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(4), blocks.get(5), blocks.get(6)))
                .withHttpChannel()
                .start();

        // Node B has lower priority, has blocks [0,1,2] and should be picked
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0), blocks.get(1), blocks.get(2)))
                .withHttpChannel()
                .start();

        // Set same priorities
        var firstSimulatorProperties = nodeASimulator.toClientProperties();
        firstSimulatorProperties.setPriority(0);

        var secondSimulatorProperties = nodeBSimulator.toClientProperties();
        secondSimulatorProperties.setPriority(0);

        subscriber = getBlockNodeSubscriber(List.of(firstSimulatorProperties, secondSimulatorProperties));
        // when
        subscriber.get();

        // then
        // should have processed exactly 0,1,2
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);
    }

    @Test
    void twoNodesChoseByPriority() {
        // given
        // Both nodes start at block 0, but higher-priority node has fewer blocks (0,1)
        var generator = new BlockGenerator(0);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(generator.next(2))
                .withHttpChannel()
                .start();

        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(generator.next(3))
                .withHttpChannel()
                .start();

        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);
        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Intentionally set lower-priority node first in the list
        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));
        // when
        subscriber.get();

        // then
        // Verify that Exactly 2 blocks were processed (0 and 1) from Node A
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();

        assertThat(indices).containsExactly(0L, 1L);
    }

    @Test
    void twoNodesWithSameBlocksChoseByPriority(CapturedOutput output) {
        // given:
        // node A (priority 0)
        var firstGenerator = new BlockGenerator(0);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
                .withHttpChannel()
                .start();

        // node B (priority 1)
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
                .withHttpChannel()
                .start();

        // Set priorities
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Intentionally set lower-priority node first to ensure priority sorting is actually used
        subscriber = getBlockNodeSubscriber(List.of(nodeBProperties, nodeAProperties));

        // when
        subscriber.get();

        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(3)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L, 1L, 2L);

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        // then
        // Verify that the logs the high-priority node's port
        var nodeAPort = String.valueOf(nodeAProperties.getPort());
        var nodeBPort = String.valueOf(nodeBProperties.getPort());

        assertThat(nodeLogs).containsExactly("Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ");
        assertThat(nodeLogs).doesNotContain(nodeBPort);
    }

    @Test
    void switchFromAutoToFileAfterThreeConsecutiveNodeFailures() {
        // given
        var blockProperties = new BlockProperties();
        blockProperties.setEnabled(true);
        blockProperties.setSourceType(BlockSourceType.AUTO);

        var dummyNode = new BlockNodeProperties();
        dummyNode.setHost("localhost");
        dummyNode.setPort(0);
        blockProperties.setNodes(List.of(dummyNode));

        when(blockStreamVerifier.getLastBlockFile()).thenReturn(Optional.empty());

        var nodeSubscriber = mock(BlockNodeSubscriber.class);
        var fileSource = mock(BlockFileSource.class);

        doThrow(new RuntimeException("boom")).when(nodeSubscriber).get();

        var composite = new CompositeBlockSource(fileSource, nodeSubscriber, blockStreamVerifier, blockProperties);

        // When there are 3 failures on nodeSubscriber, then it switches to fileSubscriber
        assertThatCode(composite::get).doesNotThrowAnyException();
        assertThatCode(composite::get).doesNotThrowAnyException();
        assertThatCode(composite::get).doesNotThrowAnyException();
        assertThatCode(composite::get).doesNotThrowAnyException();

        verify(nodeSubscriber, times(3)).get();
        verify(fileSource, times(1)).get();
    }

    @Test
    void switchFromNodeAToNodeCWhenHigherPriorityLacksNextBlock(CapturedOutput output) {

        var firstGenerator = new BlockGenerator(0);
        var blocks = new ArrayList<>(firstGenerator.next(3));

        // Node A has priority 0 and has only block 0
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0)))
                .withHttpChannel()
                .start();

        // Node B has priority 1 and does NOT have block 1
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(new BlockGenerator(5).next(3))
                .withHttpChannel()
                .start();

        // Node C has priority 2 and has blocks 1 and 2
        nodeCSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(1), blocks.get(2)))
                .withHttpChannel()
                .start();

        // Set priorities
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);
        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);
        var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(2);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties, nodeCProperties));

        // Attempt 1:  should pick A and process only block 0
        subscriber.get();

        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        assertThat(indices).doesNotContain(1L, 2L);
        clearInvocations(streamFileNotifier);

        var nodeAPort = String.valueOf(nodeAProperties.getPort());
        var nodeBPort = String.valueOf(nodeBProperties.getPort());
        var nodeCPort = String.valueOf(nodeCProperties.getPort());

        // Attempt 2: next block is 1 - Nodes A and B don't have it so Node C must be chosen
        subscriber.get();

        // Verify that block 1 is processed from Node C
        var logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");
        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeCPort + ") ");
        assertThat(nodeLogs).doesNotContain(nodeBPort);

        // Verify that blocks 1 and 2 are verified exactly once
        var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(secondCaptor.capture());

        var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L, 2L);
    }

    @Test
    void startsStreamingAtSpecificStartBlockNumber(CapturedOutput output) {
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(8));
        nodeASimulator =
                new BlockNodeSimulator().withBlocks(blocks).withHttpChannel().start();
        var nodeAProperties = nodeASimulator.toClientProperties();

        // Save initial startBlockNumber to avoid state mismatch with other tests
        var properties = commonDownloaderProperties.getImporterProperties();
        var initialStartBlockNumber = properties.getStartBlockNumber();

        // Set new start block number
        properties.setStartBlockNumber(5L);

        subscriber = getBlockNodeSubscriber(List.of(nodeASimulator.toClientProperties()));

        try {
            subscriber.get();
            // Verify that only blocks 5,6 and 7 were verified
            var captor = ArgumentCaptor.forClass(RecordFile.class);
            verify(streamFileNotifier, times(3)).verified(captor.capture());

            var indices =
                    captor.getAllValues().stream().map(RecordFile::getIndex).toList();
            assertThat(indices).containsExactly(5L, 6L, 7L);

            // Verify that logs explicitly show that it starts processing from block 5
            String logs = output.getAll();
            var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

            var nodeAPort = String.valueOf(nodeAProperties.getPort());

            assertThat(nodeLogs)
                    .containsExactly("Start streaming block 5 from BlockNode(localhost:" + nodeAPort + ") ");

        } finally {
            properties.setStartBlockNumber(initialStartBlockNumber);
        }
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockSamePriorities(CapturedOutput output) {
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(3));

        // Node A has priority 0 and only block 0
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0)))
                .withHttpChannel()
                .start();
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and only block 1
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(1)))
                .withHttpChannel()
                .start();
        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(0);

        // Node C priority 0 only block 2
        nodeCSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(2)))
                .withHttpChannel()
                .start();
        var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(0);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties, nodeCProperties));

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(secondCaptor.capture());

        var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L);
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        var thirdCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(thirdCaptor.capture());

        var thirdIndices =
                thirdCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(thirdIndices).containsExactly(2L);

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        var nodeAPort = String.valueOf(nodeAProperties.getPort());
        var nodeBPort = String.valueOf(nodeBProperties.getPort());
        var nodeCPort = String.valueOf(nodeCProperties.getPort());

        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeBPort + ") ",
                        "Start streaming block 2 from BlockNode(localhost:" + nodeCPort + ") ");
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockDifferentPriorities(CapturedOutput output) {
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(3));

        // Node A has priority 0 and only block 0
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0)))
                .withHttpChannel()
                .start();
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and only block 1
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(1)))
                .withHttpChannel()
                .start();
        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        // Node C priority 0 only block 2
        nodeCSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(2)))
                .withHttpChannel()
                .start();
        var nodeCProperties = nodeCSimulator.toClientProperties();
        nodeCProperties.setPriority(2);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeCProperties, nodeBProperties));

        // Attempt 1: Node A is selected for block 0
        subscriber.get();
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(0L);
        clearInvocations(streamFileNotifier);

        // Attempt 2: Node B is selected for block 1
        subscriber.get();
        var secondCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(secondCaptor.capture());

        var secondIndices =
                secondCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(secondIndices).containsExactly(1L);
        clearInvocations(streamFileNotifier);

        // Attempt 3: Node C is selected for block 2
        subscriber.get();
        var thirdCaptor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(thirdCaptor.capture());

        var thirdIndices =
                thirdCaptor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(thirdIndices).containsExactly(2L);

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");

        var nodeAPort = String.valueOf(nodeAProperties.getPort());
        var nodeBPort = String.valueOf(nodeBProperties.getPort());
        var nodeCPort = String.valueOf(nodeCProperties.getPort());

        assertThat(nodeLogs)
                .containsExactly(
                        "Start streaming block 0 from BlockNode(localhost:" + nodeAPort + ") ",
                        "Start streaming block 1 from BlockNode(localhost:" + nodeBPort + ") ",
                        "Start streaming block 2 from BlockNode(localhost:" + nodeCPort + ") ");
    }

    @Test
    void switchesToLowerPriorityWhenHigherPriorityHasMalformedBlock(CapturedOutput output) {

        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(3));

        // Corrupt block #1 on Node A by removing its BLOCK_HEADER
        var firstBlock = blocks.get(1);
        var itemsNoHeader = firstBlock.getBlockItemsList().stream()
                .filter(it -> it.getItemCase() != BlockItem.ItemCase.BLOCK_HEADER)
                .toList();
        var block1NoHeader =
                BlockItemSet.newBuilder().addAllBlockItems(itemsNoHeader).build();

        // Node A has priority 0, block 0 and malformed block 1
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0), block1NoHeader, blocks.get(2)))
                .withHttpChannel()
                .start();
        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);

        // Node B has priority 1 and healthy [0,1,2]
        nodeBSimulator =
                new BlockNodeSimulator().withBlocks(blocks).withHttpChannel().start();
        var nodeBProperties = nodeBSimulator.toClientProperties();
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
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(2)).verified(captor.capture());

        var indices = captor.getAllValues().stream().map(RecordFile::getIndex).toList();
        assertThat(indices).containsExactly(1L, 2L);

        String logs = output.getAll();
        var nodeLogs = findAllMatches(logs, "Start streaming block \\d+ from BlockNode\\(localhost:\\d+\\) ?");
        var nodeLogsMarkedInactive = findAllMatches(
                logs, "Marking connection to BlockNode\\(localhost:\\d+\\) as inactive after 3 attempts");

        var nodeAPort = String.valueOf(nodeAProperties.getPort());
        var nodeBPort = String.valueOf(nodeBProperties.getPort());

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
}
