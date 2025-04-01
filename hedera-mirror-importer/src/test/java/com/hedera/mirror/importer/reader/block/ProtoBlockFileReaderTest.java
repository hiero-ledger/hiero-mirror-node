// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.block;

import static com.hedera.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.hapi.platform.event.legacy.EventTransaction;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProtoBlockFileReaderTest {

    public static final List<BlockFile> TEST_BLOCK_FILES = List.of(
            BlockFile.builder()
                    .consensusStart(1738953031945593129L)
                    .consensusEnd(1738953032202698150L)
                    .count(7L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "69a5b5a95d5adc867683b68f8bd5d73ff89ed72ea55f0c48a5a114a06c70406926534bb0077eac8e0797698a63a07207")
                    .index(981L)
                    .name(BlockFile.getBlockStreamFilename(981))
                    .previousHash(
                            "2b223b895e1a847579150a85a86e32e4aa42de0cc17a9f4e73f7f330e231515ece0fbf1818c29160a5f46da0268138d3")
                    .roundStart(982L)
                    .roundEnd(982L)
                    .version(ProtoBlockFileReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1738953032298721606L)
                    .consensusEnd(1738953032428026822L)
                    .count(6L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "47601ce3e082b876995d11542d3fc546c4dee3e824b5c945491f101d170636553ca750efacf891a89ef08e61e04a1295")
                    .index(982L)
                    .name(BlockFile.getBlockStreamFilename(982))
                    .previousHash(
                            "69a5b5a95d5adc867683b68f8bd5d73ff89ed72ea55f0c48a5a114a06c70406926534bb0077eac8e0797698a63a07207")
                    .roundStart(983L)
                    .roundEnd(983L)
                    .version(ProtoBlockFileReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1741033890694027337L)
                    .consensusEnd(1741033890694027337L)
                    .count(0L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "40ecba4f4134cf9e7a6fb643b54cda852ed4dcacee7d339a120165a6552169b52568dcdd921913df69b18074d6fd6cf0")
                    .index(0L)
                    .name(BlockFile.getBlockStreamFilename(0))
                    .previousHash(
                            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                    .roundStart(1L)
                    .roundEnd(1L)
                    .version(ProtoBlockFileReader.VERSION)
                    .build());
    private static final long TIMESTAMP = 1738889423L;

    private final ProtoBlockFileReader reader = new ProtoBlockFileReader();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readTestArgumentsProvider")
    void read(String filename, StreamFileData streamFileData, BlockFile expected) {
        var actual = reader.read(streamFileData);
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("blockHeader", "blockProof", "items")
                .isEqualTo(expected);
        var expectedPreviousItems = new ArrayList<>(actual.getItems());
        if (!expectedPreviousItems.isEmpty()) {
            expectedPreviousItems.addFirst(null);
            expectedPreviousItems.removeLast();
        }
        assertThat(actual)
                .returns(expected.getCount(), a -> (long) a.getItems().size())
                .satisfies(a -> assertThat(a.getBlockHeader()).isNotNull())
                .satisfies(a -> assertThat(a.getBlockProof()).isNotNull())
                .extracting(
                        BlockFile::getItems,
                        InstanceOfAssertFactories.collection(
                                com.hedera.mirror.common.domain.transaction.BlockItem.class))
                .map(com.hedera.mirror.common.domain.transaction.BlockItem::getPrevious)
                .containsExactlyElementsOf(expectedPreviousItems);
    }

    @Test
    void readRecordFileItem() {
        // given
        var block = Block.newBuilder()
                .addItems(BlockItem.newBuilder()
                        .setRecordFile(RecordFileItem.getDefaultInstance())
                        .build())
                .build();
        byte[] bytes = gzip(block);
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), bytes);
        var expected = BlockFile.builder()
                .loadStart(streamFileData.getStreamFilename().getTimestamp())
                .name(streamFileData.getFilename())
                .recordFileItem(RecordFileItem.getDefaultInstance())
                .version(ProtoBlockFileReader.VERSION)
                .build();

        // when
        var actual = reader.read(streamFileData);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void noEventTransactions() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(0), gzip(block));
        assertThat(reader.read(streamFileData))
                .returns(TIMESTAMP * NANOS_PER_SECOND, BlockFile::getConsensusEnd)
                .returns(TIMESTAMP * NANOS_PER_SECOND, BlockFile::getConsensusStart)
                .returns(0L, BlockFile::getCount)
                .returns(List.of(), BlockFile::getItems)
                .returns(ProtoBlockFileReader.VERSION, BlockFile::getVersion);
    }

    @Test
    void mixedStateChanges() {
        // given standalone state changes immediately follows transactional state changes
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var now = Instant.now();
        var transactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano());
        var transactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        var transactionStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        var nonTransactionStateChange = StateChanges.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(now.getEpochSecond() + 1, now.getNano()))
                .build();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(transactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(transactionStateChanges))
                .addItems(BlockItem.newBuilder().setStateChanges(nonTransactionStateChange))
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(0), gzip(block));

        // when
        var blockFile = reader.read(streamFileData);

        // then the block item should only have its own state changes
        assertThat(blockFile)
                .extracting(
                        BlockFile::getItems,
                        InstanceOfAssertFactories.collection(
                                com.hedera.mirror.common.domain.transaction.BlockItem.class))
                .hasSize(1)
                .first()
                .extracting(
                        com.hedera.mirror.common.domain.transaction.BlockItem::getStateChanges,
                        InstanceOfAssertFactories.collection(StateChanges.class))
                .hasSize(1)
                .first()
                .returns(transactionTimestamp, StateChanges::getConsensusTimestamp);
    }

    @Test
    void throwWhenMissingBlockHeader() {
        var block = Block.newBuilder().addItems(blockProof()).build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block header");
    }

    @Test
    void throwWhenMissingBlockProof() {
        var block = Block.newBuilder().addItems(blockHeader()).build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block proof");
    }

    @Test
    void throwWhenMissingTransactionResult() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing transaction result");
    }

    @Test
    void thrownWhenTransactionBytesCorrupted() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var eventTransaction = BlockItem.newBuilder()
                .setEventTransaction(EventTransaction.newBuilder()
                        .setApplicationTransaction(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(32))));
        var transactionResult = BlockItem.newBuilder().setTransactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction)
                .addItems(eventTransaction)
                .addItems(transactionResult)
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));
        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to deserialize Transaction");
    }

    private BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder()
                        .setFirstTransactionConsensusTime(Timestamp.newBuilder().setSeconds(TIMESTAMP)))
                .build();
    }

    private BlockItem blockProof() {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.newBuilder()
                        .setPreviousBlockRootHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48)))
                        .setStartOfBlockStateRootHash(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(48))))
                .build();
    }

    private BlockItem eventTransaction() {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteString();
        return BlockItem.newBuilder()
                .setEventTransaction(EventTransaction.newBuilder()
                        .setApplicationTransaction(transaction)
                        .build())
                .build();
    }

    private byte[] gzip(Block block) {
        return TestUtils.gzip(block.toByteArray());
    }

    @SneakyThrows
    private static Stream<Arguments> readTestArgumentsProvider() {
        return TEST_BLOCK_FILES.stream().map(blockFile -> {
            var file = TestUtils.getResource("data/blockstreams/" + blockFile.getName());
            var streamFileData = StreamFileData.from(file);
            blockFile.setBytes(streamFileData.getBytes());
            blockFile.setLoadStart(streamFileData.getStreamFilename().getTimestamp());
            blockFile.setSize(streamFileData.getBytes().length);
            return Arguments.of(blockFile.getName(), streamFileData, blockFile);
        });
    }
}
