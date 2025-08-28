// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

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
        var firstGenerator = new BlockGenerator(5);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
                .withHttpChannel()
                .start();
        // Node B has lower priority, has blocks [0,1,2] and should be picked
        var secondGenerator = new BlockGenerator(0);
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(secondGenerator.next(3))
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
        var firstGenerator = new BlockGenerator(5);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
                .withHttpChannel()
                .start();
        // Node B has priority 0, has blocks 0,1,2 and should be picked
        var secondGenerator = new BlockGenerator(0);
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(secondGenerator.next(3))
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
        var firstGenerator = new BlockGenerator(0);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(2))
                .withHttpChannel()
                .start();

        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(firstGenerator.next(3))
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

        var indices = captor.getAllValues().stream()
                .map(RecordFile::getIndex)
                .toList();

        assertThat(indices).containsExactly(0L, 1L);
    }

    @Test
    void twoNodesWithSameBlocksChoseByPriority() {
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

        var logger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            // when
            subscriber.get();

            var captor = ArgumentCaptor.forClass(RecordFile.class);
            verify(streamFileNotifier, times(3)).verified(captor.capture());

            var indices = captor.getAllValues().stream()
                    .map(RecordFile::getIndex)
                    .toList();
            assertThat(indices).containsExactly(0L, 1L, 2L);

            var startMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block' log"));

            // Verify that the logs the high-priority node's port
            var highPriorityPort = ":" + nodeAProperties.getPort();
            var lowPriorityToken = ":" + nodeBProperties.getPort();
            // then
            assertThat(startMessage).contains(highPriorityPort);
            assertThat(startMessage).doesNotContain(lowPriorityToken);
        } finally {
            logger.detachAppender(appender);
        }
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
    void switchFromNodeAToNodeBWhenHigherPriorityLacksNextBlock() {
        // Node A has priority 0, has blocks with bad order [0,2,1]
        var nodeABlocks = new ArrayList<>(new BlockGenerator(0).next(3));
        Collections.swap(nodeABlocks, 1, 2);
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(nodeABlocks)
                .withHttpChannel()
                .start();

        // Node B has priority 1, has all blocks in right order [0,1,2] (should be used after A fails)
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(new BlockGenerator(0).next(3))
                .withHttpChannel()
                .start();

        // Node C has priority 2, has all blocks [0,1,2] (should not be used)
        nodeCSimulator = new BlockNodeSimulator()
                .withBlocks(new BlockGenerator(0).next(3))
                .withHttpChannel()
                .start();

        BlockNodeSimulator nodeAMissingBlockSimulator = null;

        try {
            var nodeAProperties = nodeASimulator.toClientProperties();
            nodeAProperties.setPriority(0);
            var nodeBProperties = nodeBSimulator.toClientProperties();
            nodeBProperties.setPriority(1);
            var nodeCProperties = nodeCSimulator.toClientProperties();
            nodeCProperties.setPriority(2);

            subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties, nodeCProperties));

            // Attempt 1: Node A is chosen and it fails with non-consecutive after verifying block 0
            assertThatThrownBy(subscriber::get)
                    .isInstanceOf(BlockStreamException.class)
                    .hasCauseInstanceOf(InvalidStreamFileException.class)
                    .hasMessageContaining("Non-consecutive block number");

            // Verify that only block 0 is processed
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 0L));
            verify(streamFileNotifier, never()).verified(argThat(rf -> rf.getIndex() == 1L));
            verify(streamFileNotifier, never()).verified(argThat(rf -> rf.getIndex() == 2L));

            // In order to simulate that NodeA is unhealthy we have to  replace it with a node that does NOT have next
            // block (1) so getNode() will skip Node A and pick Node B
            subscriber.close();
            nodeASimulator.close();

            nodeAMissingBlockSimulator = new BlockNodeSimulator()
                    .withBlocks(new BlockGenerator(5).next(3))
                    .withHttpChannel()
                    .start();
            var nodeAMissingBlockProperties = nodeAMissingBlockSimulator.toClientProperties();
            nodeAMissingBlockProperties.setPriority(0);

            subscriber = getBlockNodeSubscriber(List.of(nodeAMissingBlockProperties, nodeBProperties, nodeCProperties));

            // Attempt 2: should stream from B (priority 1) and verify blocks 1 and 2
            assertThatCode(subscriber::get).doesNotThrowAnyException();

            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 1L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 2L));

        } finally {
            // clean up the extra simulator
            if (nodeAMissingBlockSimulator != null) {
                nodeAMissingBlockSimulator.close();
            }
        }
    }

    @Test
    void switchFromNodeAToNodeCWhenHigherPriorityLacksNextBlock() {

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

        // Capture logs to verify which node handled block 1
        var logger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            // Attempt 1:  should pick A and process only block 0
            subscriber.get();

            verify(streamFileNotifier, times(1)).verified(argThat((RecordFile rf) -> rf.getIndex() == 0L));
            verify(streamFileNotifier, never()).verified(argThat((RecordFile rf) -> rf.getIndex() == 1L));
            verify(streamFileNotifier, never()).verified(argThat((RecordFile rf) -> rf.getIndex() == 2L));

            var block0LogMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 0 from "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 0' log"));
            assertThat(block0LogMessage).contains(":" + nodeAProperties.getPort());
            appender.list.clear();

            // Attempt 2: next block is 1 - Nodes A and B don't have it so Node C must be chosen
            subscriber.get();

            // Verify that blocks 1 and 2 are verified exactly once
            verify(streamFileNotifier, times(1)).verified(argThat((RecordFile rf) -> rf.getIndex() == 1L));
            verify(streamFileNotifier, times(1)).verified(argThat((RecordFile rf) -> rf.getIndex() == 2L));

            // Verify that block 1 is processed from Node C
            var block1LogMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 1 from "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 1' log"));
            assertThat(block1LogMessage).contains(":" + nodeCProperties.getPort());
            assertThat(block1LogMessage).doesNotContain(":" + nodeAProperties.getPort());
            assertThat(block1LogMessage).doesNotContain(":" + nodeBProperties.getPort());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void pickBlocksFromNodeAThenSwitchToNodeB() {
        var firstGenerator = new BlockGenerator(0);
        var blocks = new ArrayList<>(firstGenerator.next(4));

        // Node A has priority 0 and has only blocks [0,1]
        nodeASimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(0), blocks.get(1)))
                .withHttpChannel()
                .start();

        // Node B has priority 1 and has blocks [2,3]
        nodeBSimulator = new BlockNodeSimulator()
                .withBlocks(List.of(blocks.get(2), blocks.get(3)))
                .withHttpChannel()
                .start();

        var nodeAProperties = nodeASimulator.toClientProperties();
        nodeAProperties.setPriority(0);
        var nodeBProperties = nodeBSimulator.toClientProperties();
        nodeBProperties.setPriority(1);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeBProperties));

        // Capture logs to confirm which node was picked
        var logger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            // Attempt 1: should pick A and process blocks 0 and 1
            subscriber.get();

            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 0L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 1L));
            verify(streamFileNotifier, never()).verified(argThat(rf -> rf.getIndex() == 2L));

            // Verify that blocks 0 is streamed from Node A port
            var initialBlocksLogMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 0 from "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 0' log"));
            assertThat(initialBlocksLogMessage).contains(":" + nodeAProperties.getPort());
            appender.list.clear();

            // Attempt 2: next block is 2; A does NOT have it, so pick B and stream 2,3
            subscriber.get();

            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 2L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 3L));

            // Verify that blocks 2 is streamed from Node B port
            var nextBlocksLogMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 2 from "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 2' log"));
            assertThat(nextBlocksLogMessage).contains(":" + nodeBProperties.getPort());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void startsStreamingAtSpecificStartBlockNumber() {
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(8));
        nodeASimulator =
                new BlockNodeSimulator().withBlocks(blocks).withHttpChannel().start();

        // Save initial startBlockNumber to avoid state mismatch with other tests
        var properties = commonDownloaderProperties.getImporterProperties();
        var initialStartBlockNumber = properties.getStartBlockNumber();
        // Set new start block number
        properties.setStartBlockNumber(5L);

        subscriber = getBlockNodeSubscriber(List.of(nodeASimulator.toClientProperties()));

        var logger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            subscriber.get();
            // Verify that only blocks 5,6 and 7 were verified
            verify(streamFileNotifier, times(3)).verified(any(RecordFile.class));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 5L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 6L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 7L));
            verify(streamFileNotifier, never()).verified(argThat(rf -> rf.getIndex() < 5L));

            // Verify that logs explicitly show that it starts processing from block 5
            String startLogMessage = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 5 from "))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 5' log"));
            assertThat(startLogMessage)
                    .contains(":" + nodeASimulator.toClientProperties().getPort());
        } finally {
            logger.detachAppender(appender);
            properties.setStartBlockNumber(initialStartBlockNumber);
        }
    }

    @Test
    void autoSwitchesToFileSourceWhenNoBlockNodesConfigured() {

        var properties = new BlockProperties();
        properties.setEnabled(true);
        properties.setSourceType(BlockSourceType.AUTO);
        properties.setNodes(List.of());

        when(blockStreamVerifier.getLastBlockFile()).thenReturn(Optional.empty());

        var nodeSubscriber = mock(BlockNodeSubscriber.class);
        var filesSubscriber = mock(BlockFileSource.class);

        var composite = new CompositeBlockSource(filesSubscriber, nodeSubscriber, blockStreamVerifier, properties);

        // when
        assertThatCode(composite::get).doesNotThrowAnyException();

        // then: immediately uses FILE and never touches BLOCK_NODE
        verify(filesSubscriber, times(1)).get();
        verify(nodeSubscriber, never()).get();
    }

    @Test
    void switchesNodeAtoNodeBtoNodeCForNextBlockssamePriorities() {
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
        nodeCProperties.setPriority(0);

        subscriber = getBlockNodeSubscriber(List.of(nodeAProperties, nodeCProperties, nodeBProperties));

        var logger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            // Attempt 1: Node A is selected for block 0
            assertThatCode(subscriber::get).doesNotThrowAnyException();
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 0L));
            var start0 = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 0 from "))
                    .findFirst()
                    .orElseThrow();
            assertThat(start0).contains(":" + nodeAProperties.getPort());
            appender.list.clear();

            // Attempt 2: Node B is selected for block 1
            assertThatCode(subscriber::get).doesNotThrowAnyException();
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 1L));
            var start1 = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 1 from "))
                    .findFirst()
                    .orElseThrow();
            assertThat(start1).contains(":" + nodeBProperties.getPort());
            appender.list.clear();

            // Attempt 3: Node C is selected for block 2
            assertThatCode(subscriber::get).doesNotThrowAnyException();
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 2L));
            var start2 = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 2 from "))
                    .findFirst()
                    .orElseThrow();
            assertThat(start2).contains(":" + nodeCProperties.getPort());

            verify(streamFileNotifier, times(3)).verified(any(RecordFile.class));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void switchesToLowerPriorityWhenHigherPriorityHasMalformedBlock() {

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

        // Appender for BlockNodeSubscriber
        var subscriberLogger = (Logger) LoggerFactory.getLogger(BlockNodeSubscriber.class);
        var subscriberAppender = new ListAppender<ILoggingEvent>();
        subscriberAppender.start();
        subscriberLogger.addAppender(subscriberAppender);

        // Appender for BlockNode
        var nodeLogger = (Logger) LoggerFactory.getLogger(BlockNode.class);
        var nodeAppender = new ListAppender<ILoggingEvent>();
        nodeAppender.start();
        nodeLogger.addAppender(nodeAppender);

        try {
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

            // The "inactive after 3 attempts" message is logged by BlockNode
            boolean markedInactive = nodeAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.contains("inactive after 3 attempts"));
            assertThat(markedInactive).isTrue();

            subscriberAppender.list.clear();

            // Attempt 4: should switch to B and successfully stream blocks 1 and 2
            assertThatCode(subscriber::get).doesNotThrowAnyException();

            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 1L));
            verify(streamFileNotifier, times(1)).verified(argThat(rf -> rf.getIndex() == 2L));

            // Confirm the log for block 1 references Node B’s port
            var startFor1 = subscriberAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.startsWith("Start streaming block 1 from "))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("Missing 'Start streaming block 1' log"));
            assertThat(startFor1).contains(":" + nodeBProperties.getPort());
        } finally {
            subscriberLogger.detachAppender(subscriberAppender);
            nodeLogger.detachAppender(nodeAppender);
        }
    }
}
