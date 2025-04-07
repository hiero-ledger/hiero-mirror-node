// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.block;

import static com.hedera.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
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
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
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
                    .consensusStart(1743543388185101630L)
                    .consensusEnd(1743543388372187000L)
                    .count(5L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "449e750e4da69fecb92234a8dede0ac3e7b53141aecc1a30cba050ec44a729c9b5e5a06607ac6ac8a17110fb99c202ef")
                    .index(2301160L)
                    .name(BlockFile.getBlockStreamFilename(2301160))
                    .previousHash(
                            "aeb9db588e53e6b3d1d39f1a75dbf7123e95c18cea102380989bdda89079809e2217bfb230eda982e37e36f40e87988a")
                    .roundStart(2301161L)
                    .roundEnd(2301161L)
                    .version(ProtoBlockFileReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1743543388490143524L)
                    .consensusEnd(1743543388704490000L)
                    .count(4L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "0d2523cc3f44a0ffebd9bcd1950c685deb932e53b7af3e8cd202397ae2d110c3452797eb0ff05cca1ffe9780e31bec34")
                    .index(2301161L)
                    .name(BlockFile.getBlockStreamFilename(2301161))
                    .previousHash(
                            "449e750e4da69fecb92234a8dede0ac3e7b53141aecc1a30cba050ec44a729c9b5e5a06607ac6ac8a17110fb99c202ef")
                    .roundStart(2301162L)
                    .roundEnd(2301162L)
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
    void readBatchTransactions() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var now = Instant.now();
        var batchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano());
        var preBatchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() - 1);
        var innerTransactionTimestamp1 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 1);
        var innerTransactionTimestamp2 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 2);
        var postBatchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 3);

        var preBatchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(preBatchTransactionTimestamp)
                .build();
        var preBatchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(preBatchTransactionTimestamp)
                .build();

        var batchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .build();
        var batchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var innerTransactionResult1 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp1)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();
        var innerTransactionResult2 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp2)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var postBatchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(postBatchTransactionTimestamp)
                .build();
        var postBatchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(postBatchTransactionTimestamp)
                .build();

        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(preBatchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(preBatchStateChanges))
                .addItems(eventHeader)
                .addItems(batchEventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(batchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(batchStateChanges))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult2))
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(postBatchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(postBatchStateChanges))
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));

        var blockFile = reader.read(streamFileData);
        var items = blockFile.getItems();
        var batchParentItem = blockFile.getItems().get(1);
        var innerTransaction1 = blockFile.getItems().get(2);
        var innerTransaction2 = blockFile.getItems().get(3);

        var expectedParents = new ArrayList<com.hedera.mirror.common.domain.transaction.BlockItem>();
        var expectedPrevious = new ArrayList<>(items);
        expectedPrevious.addFirst(null);
        expectedPrevious.removeLast();

        expectedParents.add(null);
        expectedParents.add(null);
        expectedParents.add(batchParentItem);
        expectedParents.add(batchParentItem);
        expectedParents.add(null);

        assertThat(items).hasSize(5);
        assertThat(TestUtils.toTimestamp(batchParentItem.getConsensusTimestamp()))
                .isEqualTo(batchTransactionTimestamp);
        assertThat(items)
                .map(com.hedera.mirror.common.domain.transaction.BlockItem::getParent)
                .containsExactlyElementsOf(expectedParents);
        assertThat(items)
                .map(com.hedera.mirror.common.domain.transaction.BlockItem::getPrevious)
                .containsExactlyElementsOf(expectedPrevious);
        assertThat(batchParentItem.getStateChangeContext())
                .isEqualTo(innerTransaction1.getStateChangeContext())
                .isEqualTo(innerTransaction2.getStateChangeContext());
    }

    @Test
    void readBatchTransactionsMissingInnerTransactionResult() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var now = Instant.now();
        var batchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano());
        var innerTransactionTimestamp1 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 1);

        var batchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .build();
        var batchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .addStateChanges(StateChange.newBuilder())
                .build();

        var innerTransactionResult1 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp1)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(batchEventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(batchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(batchStateChanges))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));

        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Missing transaction result in block file 000000000000000000000000000000000001.blk.gz");
    }

    @Test
    void readBatchTransactionsMissingInnerTransaction() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var now = Instant.now();
        var batchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano());
        var innerTransactionTimestamp1 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 1);

        var batchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .build();
        var batchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(batchTransactionTimestamp)
                .addStateChanges(StateChange.newBuilder())
                .build();

        var innerTransactionResult1 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp1)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(batchEventTransaction(List.of(Transaction.newBuilder().build().toByteString())))
                .addItems(BlockItem.newBuilder().setTransactionResult(batchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(batchStateChanges))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(blockProof())
                .build();
        var streamFileData = StreamFileData.from(BlockFile.getBlockStreamFilename(1), gzip(block));

        assertThatThrownBy(() -> reader.read(streamFileData))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage(
                        "Failed to parse inner transaction from atomic batch in block file 000000000000000000000000000000000001.blk.gz");
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
        return eventTransaction(TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .build());
    }

    private BlockItem eventTransaction(TransactionBody transactionBody) {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBody.toByteString())
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

    private BlockItem batchEventTransaction() {
        var cryptoTransfer = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteString();
        var cryptoTransfer2 = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build()
                                .toByteString())
                        .build()
                        .toByteString())
                .build()
                .toByteString();
        return batchEventTransaction(List.of(cryptoTransfer, cryptoTransfer2));
    }

    private BlockItem batchEventTransaction(List<ByteString> innerTransactions) {
        var transaction = TransactionBody.newBuilder()
                .setAtomicBatch(AtomicBatchTransactionBody.newBuilder()
                        .addAllTransactions(innerTransactions)
                        .build())
                .build();
        return eventTransaction(transaction);
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
