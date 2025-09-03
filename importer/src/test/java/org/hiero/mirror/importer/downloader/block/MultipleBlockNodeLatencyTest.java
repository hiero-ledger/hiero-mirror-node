// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.TestUtils.findAllMatches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerType;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;

final class MultipleBlockNodeLatencyTest extends AbstractBlockNodeIntegrationTest {

    @BeforeEach
    @Override
    void setup() {
        super.setup();
        blockProperties.getScheduler().setType(SchedulerType.LATENCY);
    }

    @Test
    void streamFromNodes(CapturedOutput output) {
        // given
        var interval = Duration.ofMillis(200);
        var generator = new BlockGenerator(interval, 0, Instant.now().minus(Duration.ofMinutes(10)));
        var blocks = generator.next(20);
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks)
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(10)
                .withPriority(1)
                .start());
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks)
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(100)
                .withPriority(0)
                .start());
        var blockNodeProperties =
                simulators.stream().map(BlockNodeSimulator::toClientProperties).toList();
        subscriber = getBlockNodeSubscriber(blockNodeProperties);

        // when, then
        await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(1)).untilAsserted(() -> assertThatThrownBy(
                        subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("No block node can provide block 20"));

        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(20)).verified(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RecordFile::getIndex)
                .containsExactlyElementsOf(LongStream.range(0, 20).boxed().collect(Collectors.toList()));

        // it's non-deterministic that at exactly which block, based on latency, the scheduler will switch from one
        // block node server to the lower latency one. However, there should be two switches
        int port1 = simulators.getFirst().getPort();
        int port2 = simulators.get(1).getPort();
        assertThat(findAllMatches(output.getAll(), "from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("from BlockNode(localhost:%d)", port1),
                        String.format("from BlockNode(localhost:%d)", port2),
                        String.format("from BlockNode(localhost:%d)", port1));
    }

    @Test
    void streamFromNodesWithSomeMissingBlocks(CapturedOutput output) {
        // given
        var interval = Duration.ofMillis(200);
        var generator = new BlockGenerator(interval, 0, Instant.now().minus(Duration.ofMinutes(10)));
        var blocks = generator.next(40);
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks.subList(0, 15))
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(10)
                .withPriority(3)
                .start());
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks.subList(0, 15))
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(100)
                .withPriority(2)
                .start());
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks.subList(13, 30))
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(20)
                .withPriority(1)
                .start());
        simulators.add(new BlockNodeSimulator()
                .withBlocks(blocks.subList(20, 40))
                .withHttpChannel()
                .withBlockInterval(interval)
                .withLatency(100)
                .withPriority(0)
                .start());
        var blockNodeProperties =
                simulators.stream().map(BlockNodeSimulator::toClientProperties).toList();
        subscriber = getBlockNodeSubscriber(blockNodeProperties);

        // when, then
        await().atMost(Duration.ofSeconds(20)).pollDelay(Duration.ofMillis(1)).untilAsserted(() -> assertThatThrownBy(
                        subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("No block node can provide block 40"));

        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(40)).verified(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RecordFile::getIndex)
                .containsExactlyElementsOf(LongStream.range(0, 40).boxed().collect(Collectors.toList()));

        // the following should happen in order
        // - start from node0
        // - switch to node1
        // - switch to node0
        // - switch to node2
        // - switch to node3
        // - switch to node3
        // Note the last two subscriptions are both with node3, because the scheduler is triggered for a rescheduling
        // and find out lower latency nodes simply don't have the next block
        var ports = simulators.stream().map(BlockNodeSimulator::getPort).toList();
        assertThat(findAllMatches(output.getAll(), "from BlockNode\\(localhost:\\d+\\)"))
                .containsExactly(
                        String.format("from BlockNode(localhost:%d)", ports.get(0)),
                        String.format("from BlockNode(localhost:%d)", ports.get(1)),
                        String.format("from BlockNode(localhost:%d)", ports.get(0)),
                        String.format("from BlockNode(localhost:%d)", ports.get(2)),
                        String.format("from BlockNode(localhost:%d)", ports.get(3)),
                        String.format("from BlockNode(localhost:%d)", ports.get(3)));
    }
}
