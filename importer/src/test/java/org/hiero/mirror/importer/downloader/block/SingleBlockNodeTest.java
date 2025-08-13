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

final class SingleBlockNodeTest extends AbstractBlockNodeIntegrationTest {

    private BlockNodeSimulator simulator;
    private BlockNodeSubscriber subscriber;

    @AfterEach
    void cleanup() {
        if (subscriber != null) {
            subscriber.close();
            subscriber = null;
        }

        if (simulator != null) {
            simulator.close();
            simulator = null;
        }
    }

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

    //OK
    @Test
    void missingBlock() {
        // given
        var generator = new BlockGenerator(0);
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
                .hasMessageContaining("Non-consecutive block number");;
    }

    //BlockNode hands a block to the reader after it sees a proof. If proof is missing the partially buffered block is never flushed
    @Test
    void missingBlockProof() {
        // given
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(2));
        var block1 = blocks.get(1);
        var proofIdx = block1.getBlockItemsCount() - 1;
        var block1NoProof = block1.toBuilder().removeBlockItems(proofIdx).build();
        blocks.set(1, block1NoProof);

        simulator = new BlockNodeSimulator()
                .withBlocks(blocks)
                .withHttpChannel()
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        assertThatCode(subscriber::get).doesNotThrowAnyException();

        // when, then
        var captor = ArgumentCaptor.forClass(RecordFile.class);
        verify(streamFileNotifier, times(1)).verified(captor.capture());

        // explicit guard that index 1 was NOT verified
        verify(streamFileNotifier, never()).verified(argThat(rf -> rf.getIndex() == 1));
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
        var block0WithoutHeader = BlockItemSet.newBuilder()
                .addAllBlockItems(itemsWithoutHeader)
                .build();
        blocks.set(0, block0WithoutHeader);

        simulator = new BlockNodeSimulator()
                .withBlocks(blocks)
                .withHttpChannel()
                .start();

        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

       //  reader should detect missing header and fail
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasNoCause()
                .hasMessageContaining("Incorrect first block item case")
                .hasMessageContaining("ROUND_HEADER");
        //nothing got verified since the first block failed
        verify(streamFileNotifier, never()).verified(any(RecordFile.class));
    }

    //OK
    @Test
    void corruptedBlockProof() {
        // given
        var generator = new BlockGenerator(0);
        var blocks = new ArrayList<>(generator.next(2));

        var block1 = blocks.get(1);
        var proofIdx = block1.getBlockItemsCount() - 1;
        var proofItem = block1.getBlockItems(proofIdx);
        var blockProof = proofItem.getBlockProof();

        //corrupt the BlockProof by flipping the first byte of the previous block root hash
        byte[] prev = org.hiero.mirror.common.util.DomainUtils.toBytes(blockProof.getPreviousBlockRootHash());
        prev[0] ^= 0x01;

        var badProof = blockProof.toBuilder().setPreviousBlockRootHash(org.hiero.mirror.common.util.DomainUtils.fromBytes(prev)).build();

        var badProofItem = proofItem.toBuilder().setBlockProof(badProof).build();
        var corruptedB1 = block1.toBuilder().setBlockItems(proofIdx, badProofItem).build();
        blocks.set(1, corruptedB1);

        simulator = new BlockNodeSimulator()
                .withBlocks(blocks)
                .withHttpChannel()
                .start();
        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));

        subscriber.get();

        // when, then
        assertThatThrownBy(subscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(HashMismatchException.class)
                .hasMessageContaining("Previous hash mismatch");;
    }

//    @Test
//    void missingBlockProof_blockStreamException() {
//        // given: two blocks where the second is missing its BlockProof
//        var generator = new BlockGenerator(0);
//
//        var blocks = new ArrayList<>(generator.next(2));
//
//        // Remove the final item (BlockProof) from the second block
//        var second = blocks.get(1);
//        var invalidSecond = BlockItemSet.newBuilder()
//                .addAllBlockItems(second.getBlockItemsList().subList(0, second.getBlockItemsCount() - 1))
//                .build();
//        blocks.set(1, invalidSecond);
//
//        simulator = new BlockNodeSimulator()
//                .withBlocks(blocks)
//                .start();
//        subscriber = getBlockNodeSubscriber(List.of(simulator.toClientProperties()));
//
//        // when / then: streaming should fail when it hits block #1 without a proof
//        assertThatThrownBy(subscriber::get)
//                .isInstanceOf(BlockStreamException.class);
//
//        // and: only the first (valid) block should have been verified
//        var captor = ArgumentCaptor.forClass(RecordFile.class);
//        verify(streamFileNotifier, times(1)).verified(captor.capture());
//    }

}
