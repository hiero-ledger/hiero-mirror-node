// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.block.simulator.BlockGenerator;
import org.hiero.mirror.importer.downloader.block.simulator.BlockNodeSimulator;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class SingleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    @AutoClose
    private BlockNodeSimulator simulator;

    @AutoClose
    private BlockNodeSubscriber subscriber;

    @Test
    void multipleBlocks() {
        // given
        var generator = new BlockGenerator(0);
        simulator = new BlockNodeSimulator()
                .withChunksPerBlock(2)
                .withBlocks(generator.next(10))
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

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
        simulator = new BlockNodeSimulator()
                .withBlocks(generator.next(10))
                .withHttpChannel()
                .withOutOrder()
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(InvalidStreamFileException.class);
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
                .filter(it -> it.getItemCase() == ItemCase.EVENT_TRANSACTION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The block is missing event transaction item"));
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
                .hasMessageContaining("Missing block proof in block");
    }

    @Test
    void exceptionIsThrownWhenTheLastEventTxnItemIsNotFollowedByTxnResultItem() {
        // given
        var generator = new BlockGenerator(0);

        var blocks = new ArrayList<>(generator.next(2));
        var blockOne = blocks.get(1);
        var blockOneItems = blockOne.getBlockItemsList();
        var blockOneLastEventTxn = blockOneItems.stream()
                .filter(it -> it.getItemCase() == ItemCase.EVENT_TRANSACTION)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("The block is missing event transaction item"));
        var blockOneTxnResult = blockOneItems.stream()
                .filter(it -> it.getItemCase() == ItemCase.TRANSACTION_RESULT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The block is missing transaction result item"));
        int blockOneEventTxnIndex = blockOneItems.indexOf(blockOneLastEventTxn);
        int blockOneTxnResultIndex = blockOneItems.indexOf(blockOneTxnResult);

        var builder = BlockItemSet.newBuilder();
        var wrongOrderBlockItems = new ArrayList<>(blockOneItems);
        Collections.swap(wrongOrderBlockItems, blockOneTxnResultIndex, blockOneEventTxnIndex);
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
                .hasMessageContaining("Missing transaction result in block");
    }
}
