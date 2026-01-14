// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.LongStream;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class SingleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @Test
    void multipleBlocks() {
        // given
        var generator = new BlockGenerator(0);
        addSimulatorWithBlocks(generator.next(10)).withChunksPerBlock(2).withInProcessChannel();
        subscriber = getBlockNodeSubscriber();

        // when
        subscriber.get();

        // then
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(10)).verified(captor.capture());
        assertThat(captor.getAllValues())
                .map(RecordFile::getIndex)
                .containsExactlyElementsOf(LongStream.range(0, 10).boxed().toList());
    }

    @Test
    void outOfOrder() {
        // given
        var generator = new BlockGenerator(0);
        addSimulatorWithBlocks(generator.next(10)).withOutOfOrder();
        subscriber = getBlockNodeSubscriber();

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class);
    }

    @Test
    void missingBlock() {
        // given
        var generator = new BlockGenerator(0);
        addSimulatorWithBlocks(generator.next(10)).withMissingBlock();
        subscriber = getBlockNodeSubscriber();

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
        var itemsWithoutHeader = block0.block().getBlockItemsList().stream()
                .filter(it -> it.getItemCase() != BlockItem.ItemCase.BLOCK_HEADER)
                .toList();
        var block0WithoutHeader =
                BlockItemSet.newBuilder().addAllBlockItems(itemsWithoutHeader).build();
        blocks.set(0, new BlockGenerator.BlockRecord(block0WithoutHeader));

        addSimulatorWithBlocks(blocks);
        subscriber = getBlockNodeSubscriber();

        // when, then
        // Detect missing header and fail
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasNoCause()
                .hasMessageContaining("Incorrect first block item case")
                .hasMessageContaining("ROUND_HEADER");

        // nothing got verified since the first block failed
        verify(streamFileNotifier, never()).verified(any(RecordFile.class));
    }

    @Test
    void exceptionIsThrownWhenEventTxnItemIsBeforeEventHeaderItem() {
        // given
        var generator = new BlockGenerator(0);

        var blocks = new ArrayList<>(generator.next(2));
        var blockOne = blocks.get(1);
        var blockOneItems = blockOne.block().getBlockItemsList();
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
        blocks.add(new BlockGenerator.BlockRecord(builder.build()));

        addSimulatorWithBlocks(blocks).withChunksPerBlock(2);
        subscriber = getBlockNodeSubscriber();

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block footer in block");
    }
}
