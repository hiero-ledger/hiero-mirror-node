// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.TestUtils.findAllMatches;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.LongStream;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerType;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

final class MultipleBlockNodeLatencyTest extends AbstractBlockNodeIntegrationTest {

    @BeforeEach
    @Override
    void setup() {
        super.setup();
        schedulerProperties.setType(SchedulerType.LATENCY);
    }

    @Test
    void streamFromNodes(CapturedOutput output) {
        // given
        var interval = Duration.ofMillis(200);
        var generator = new BlockGenerator(interval, 0, Instant.now().minus(Duration.ofMinutes(10)));
        var blocks = generator.next(20);
        addSimulatorWithBlocks(blocks)
                .withHostPrefix("a")
                .withInProcessChannel()
                .withBlockInterval(interval)
                .withLatency(100)
                .withPriority(1);
        addSimulatorWithBlocks(blocks)
                .withHostPrefix("b")
                .withInProcessChannel()
                .withBlockInterval(interval)
                .withLatency(10)
                .withPriority(0);
        subscriber = getBlockNodeSubscriber();

        // when, then
        await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(1)).untilAsserted(() -> assertThatThrownBy(
                        subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("No block node can provide block 20"));
        assertVerifiedBlockFiles(LongStream.range(0, 20).boxed().toList());
        // it's non-deterministic that at exactly which block, based on latency, the scheduler will switch from one
        // block node server to the lower latency one. However, there should be exactly one switch
        assertThat(findAllMatches(output.getAll(), "from BlockNode\\(.+:-1\\)"))
                .containsExactly(
                        String.format("from BlockNode(%s)", endpoint(0)),
                        String.format("from BlockNode(%s)", endpoint(1)));
    }

    @Test
    void streamFromNodesWithSomeMissingBlocks(CapturedOutput output) {
        // given
        var interval = Duration.ofMillis(200);
        var generator = new BlockGenerator(interval, 0, Instant.now().minus(Duration.ofMinutes(10)));
        var blocks = generator.next(40);
        addSimulatorWithBlocks(blocks.subList(0, 15))
                .withHostPrefix("a")
                .withBlockInterval(interval)
                .withInProcessChannel()
                .withLatency(100)
                .withPriority(3);
        addSimulatorWithBlocks(blocks.subList(0, 15))
                .withHostPrefix("b")
                .withBlockInterval(interval)
                .withInProcessChannel()
                .withLatency(20)
                .withPriority(2);
        addSimulatorWithBlocks(blocks.subList(13, 30))
                .withBlockInterval(interval)
                .withHostPrefix("c")
                .withInProcessChannel()
                .withLatency(90)
                .withPriority(1);
        addSimulatorWithBlocks(blocks.subList(20, 40))
                .withBlockInterval(interval)
                .withHostPrefix("d")
                .withInProcessChannel()
                .withLatency(10)
                .withPriority(0);
        subscriber = getBlockNodeSubscriber();

        // when, then
        await().atMost(Duration.ofSeconds(20)).pollDelay(Duration.ofMillis(1)).untilAsserted(() -> assertThatThrownBy(
                        subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("No block node can provide block 40"));
        assertVerifiedBlockFiles(LongStream.range(0, 40).boxed().toList());

        // the following should happen in order
        // - start from node0
        // - switch to node1
        // - switch to node2
        // - rescheduling, stream from node2 again since it's the node with the lowest latency and can provide the block
        // - switch to node3
        // - rescheduling, stream from node3 again since only node3 has the blocks
        assertThat(findAllMatches(output.getAll(), "from BlockNode\\(.+:-1\\)"))
                .containsExactly(
                        String.format("from BlockNode(%s)", endpoint(0)),
                        String.format("from BlockNode(%s)", endpoint(1)),
                        String.format("from BlockNode(%s)", endpoint(2)),
                        String.format("from BlockNode(%s)", endpoint(2)),
                        String.format("from BlockNode(%s)", endpoint(3)),
                        String.format("from BlockNode(%s)", endpoint(3)));
    }
}
