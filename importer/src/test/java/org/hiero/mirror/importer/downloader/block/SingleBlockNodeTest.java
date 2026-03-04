// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class SingleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @AutoClose
    private BlockNodeSimulator simulator;

    @AutoClose
    private BlockNodeSubscriber subscriber;

    @Test
    void multipleBlocks() {
        // given
        final var generator = new BlockGenerator(0);
        simulator = new BlockNodeSimulator()
                .withChunksPerBlock(2)
                .withBlocks(generator.next(10))
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when
        subscriber.get();

        // then
        assertVerifiedBlockFiles(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    @Test
    void outOfOrder() {
        // given
        var generator = new BlockGenerator(0);
        simulator = new BlockNodeSimulator()
                .withBlocks(generator.next(10))
                .withHttpChannel()
                .withOutOfOrder()
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class);
    }

    @Test
    void missingBlock() {
        // given
        final var generator = new BlockGenerator(0);
        simulator = new BlockNodeSimulator()
                .withBlocks(generator.next(10))
                .withHttpChannel()
                .withMissingBlock()
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Non-consecutive block number");
    }

    @Test
    void missingBlockHeader() {
        // given
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(2));

        var block0 = blocks.getFirst();
        var itemsWithoutHeader = block0.getBlockItemsList().stream()
                .filter(it -> it.getItemCase() != BlockItem.ItemCase.BLOCK_HEADER)
                .toList();
        var block0WithoutHeader =
                BlockItemSet.newBuilder().addAllBlockItems(itemsWithoutHeader).build();
        blocks.set(0, block0WithoutHeader);

        simulator =
                new BlockNodeSimulator().withBlocks(blocks).withHttpChannel().start();

        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when, then
        // Detect missing header and fail
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasNoCause()
                .hasMessageContaining("Incorrect first block item case")
                .hasMessageContaining("ROUND_HEADER");

        // nothing got verified since the first block failed
        assertThat(streamFileNotifier.getVerifiedStreamFiles()).isEmpty();
    }

    @Test
    void exceptionIsThrownWhenEventTxnItemIsBeforeEventHeaderItem() {
        // given
        var generator = new BlockGenerator(0);

        var blocks = new ArrayList<>(generator.next(2));
        var blockOne = blocks.get(1);
        var blockOneItems = blockOne.getBlockItemsList();
        var blockOneEventHeaderItem = blockOneItems.stream()
                .filter(it -> it.getItemCase() == ItemCase.EVENT_HEADER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The block is missing event header item"));
        var blockOneEventTxnItem = blockOneItems.stream()
                .filter(it -> it.getItemCase() == ItemCase.SIGNED_TRANSACTION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The block is missing signed transaction item"));
        int blockOneEventHeaderItemIndex = blockOneItems.indexOf(blockOneEventHeaderItem);
        int blockOneEventTxnItemIndex = blockOneItems.indexOf(blockOneEventTxnItem);

        var builder = BlockItemSet.newBuilder();
        var wrongOrderBlockItems = new ArrayList<>(blockOneItems);
        Collections.swap(wrongOrderBlockItems, blockOneEventHeaderItemIndex, blockOneEventTxnItemIndex);
        builder.addAllBlockItems(wrongOrderBlockItems);
        blocks.remove(1);
        blocks.add(builder.build());

        simulator = new BlockNodeSimulator()
                .withChunksPerBlock(2)
                .withBlocks(blocks)
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block footer in block");
    }
}
