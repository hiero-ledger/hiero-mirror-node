// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

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
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BlockStreamReaderTest {

    public static final List<BlockFile> TEST_BLOCK_FILES = List.of(
            BlockFile.builder()
                    .consensusStart(1746477299499963486L)
                    .consensusEnd(1746477301609588787L)
                    .count(6L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "fb31381a223175f1f8730df52be31c318eb093f8029440da2d3f0ed19f29e58af111b5c1600412eed02be1e92b4befb4")
                    .index(76L)
                    .name(BlockFile.getFilename(76, true))
                    .previousHash(
                            "47ad177417a4e6a85c67660dc9abcd5e735ae689f5a3096c68bbdff6b330e7951ddd545cd16445d19118975464380a3b")
                    .roundStart(521L)
                    .roundEnd(527L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1746477301948765000L)
                    .consensusEnd(1746477303380786221L)
                    .count(3L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "c198f382ba69796c805450842de7f97c91d2a8a7f88f43c977247d15b898b5b09ac7f857fa13fe627f8d15bb68879bb2")
                    .index(77L)
                    .name(BlockFile.getFilename(77, true))
                    .previousHash(
                            "fb31381a223175f1f8730df52be31c318eb093f8029440da2d3f0ed19f29e58af111b5c1600412eed02be1e92b4befb4")
                    .roundStart(528L)
                    .roundEnd(534L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1746477093416982857L)
                    .consensusEnd(1746477093416983584L)
                    .count(728L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "958e5fba01f066cdbe2733059b33bf0ebec0fbac5eac5b0806ebc2c792187650e1b0b34a6a4c83025a31c2da787f510d")
                    .index(0L)
                    .name(BlockFile.getFilename(0, true))
                    .previousHash(
                            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                    .roundStart(1L)
                    .roundEnd(1L)
                    .version(BlockStreamReader.VERSION)
                    .build());

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final BlockStreamReader reader = new BlockStreamReaderImpl();

    @ParameterizedTest(name = "{0}")
    @MethodSource("readTestArgumentsProvider")
    void read(BlockStream blockStream, BlockFile expected) {
        var actual = reader.read(blockStream);
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
                                org.hiero.mirror.common.domain.transaction.BlockItem.class))
                .map(org.hiero.mirror.common.domain.transaction.BlockItem::getPrevious)
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
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        var expected = BlockFile.builder()
                .bytes(blockStream.bytes())
                .loadStart(blockStream.loadStart())
                .name(blockStream.filename())
                .nodeId(blockStream.nodeId())
                .recordFileItem(RecordFileItem.getDefaultInstance())
                .size(blockStream.bytes().length)
                .version(BlockStreamReader.VERSION)
                .build();

        // when
        var actual = reader.read(blockStream);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void readBatchTransactions() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var now = Instant.now();
        var batchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano());
        var preBatchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() - 2);
        var precedingChildTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() - 1);
        var innerTransactionTimestamp1 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 1);
        var childTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 2);
        var innerTransactionTimestamp2 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 3);
        var postBatchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 4);

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

        var precedingChildTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(precedingChildTimestamp)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var innerTransactionResult1 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp1)
                .setParentConsensusTimestamp(batchTransactionTimestamp)
                .build();

        var childTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(childTimestamp)
                .setParentConsensusTimestamp(innerTransactionTimestamp1)
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
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(precedingChildTransactionResult))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(childTransactionResult))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult2))
                .addItems(eventHeader)
                .addItems(eventTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(postBatchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(postBatchStateChanges))
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        var blockFile = reader.read(blockStream);
        var items = blockFile.getItems();
        var batchParentItem = blockFile.getItems().get(1);
        var precedingChild = blockFile.getItems().get(2);
        var innerTransaction1 = blockFile.getItems().get(3);
        var child = blockFile.getItems().get(4);
        var innerTransaction2 = blockFile.getItems().get(5);

        var expectedParents = new ArrayList<org.hiero.mirror.common.domain.transaction.BlockItem>();
        var expectedPrevious = new ArrayList<>(items);

        expectedPrevious.addFirst(null);
        expectedPrevious.removeLast();

        expectedParents.add(null);
        expectedParents.add(null);
        expectedParents.add(batchParentItem);
        expectedParents.add(batchParentItem);
        expectedParents.add(innerTransaction1);
        expectedParents.add(batchParentItem);
        expectedParents.add(null);

        assertThat(items).hasSize(7);
        assertThat(TestUtils.toTimestamp(batchParentItem.getConsensusTimestamp()))
                .isEqualTo(batchTransactionTimestamp);
        assertThat(items)
                .map(org.hiero.mirror.common.domain.transaction.BlockItem::getParent)
                .containsExactlyElementsOf(expectedParents);
        assertThat(items)
                .map(org.hiero.mirror.common.domain.transaction.BlockItem::getPrevious)
                .containsExactlyElementsOf(expectedPrevious);
        assertThat(batchParentItem.getStateChangeContext())
                .isEqualTo(precedingChild.getStateChangeContext())
                .isEqualTo(innerTransaction1.getStateChangeContext())
                .isEqualTo(child.getStateChangeContext())
                .isEqualTo(innerTransaction2.getStateChangeContext())
                .isNotEqualTo(items.getFirst().getStateChangeContext())
                .isNotEqualTo(items.getLast().getStateChangeContext());
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
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage("Missing transaction result in block 000000000000000000000000000000000001.blk.gz");
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
                .addItems(batchEventTransaction(
                        List.of(Transaction.newBuilder().build().toByteString())))
                .addItems(BlockItem.newBuilder().setTransactionResult(batchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(batchStateChanges))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage(
                        "Failed to parse inner transaction from atomic batch in block 000000000000000000000000000000000001.blk.gz");
    }

    @Test
    void noEventTransactions() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        // A standalone state changes block item, with consensus timestamp
        var stateChanges = stateChanges();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(stateChanges)
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        long timestamp =
                DomainUtils.timestampInNanosMax(stateChanges.getStateChanges().getConsensusTimestamp());
        assertThat(reader.read(blockStream))
                .returns(timestamp, BlockFile::getConsensusEnd)
                .returns(timestamp, BlockFile::getConsensusStart)
                .returns(0L, BlockFile::getCount)
                .returns(List.of(), BlockFile::getItems)
                .returns(BlockStreamReader.VERSION, BlockFile::getVersion);
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
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        var blockFile = reader.read(blockStream);

        // then the block item should only have its own state changes
        assertThat(blockFile)
                .extracting(
                        BlockFile::getItems,
                        InstanceOfAssertFactories.collection(
                                org.hiero.mirror.common.domain.transaction.BlockItem.class))
                .hasSize(1)
                .first()
                .extracting(
                        org.hiero.mirror.common.domain.transaction.BlockItem::getStateChanges,
                        InstanceOfAssertFactories.collection(StateChanges.class))
                .hasSize(1)
                .first()
                .returns(transactionTimestamp, StateChanges::getConsensusTimestamp);
    }

    @Test
    void throwWhenMissingBlockHeader() {
        var block = Block.newBuilder().addItems(blockProof()).build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block header");
    }

    @Test
    void throwWhenMissingBlockProof() {
        var block = Block.newBuilder().addItems(blockHeader()).build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
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
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
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
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to deserialize Transaction");
    }

    private BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setBlockTimestamp(domainBuilder.protoTimestamp()))
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

    private BlockItem stateChanges() {
        return BlockItem.newBuilder()
                .setStateChanges(StateChanges.newBuilder().setConsensusTimestamp(domainBuilder.protoTimestamp()))
                .build();
    }

    private static BlockStream createBlockStream(Block block, byte[] bytes, String filename) {
        if (bytes == null) {
            bytes = TestUtils.gzip(block.toByteArray());
        }

        long blockCompleteTime = System.currentTimeMillis();
        return new BlockStream(
                block.getItemsList(), blockCompleteTime, bytes, filename, blockCompleteTime - 1000, TestUtils.id());
    }

    @SneakyThrows
    private static Block getBlock(StreamFileData blockFileData) {
        try (var is = blockFileData.getInputStream()) {
            return Block.parseFrom(is.readAllBytes());
        }
    }

    @SneakyThrows
    private static Stream<Arguments> readTestArgumentsProvider() {
        return TEST_BLOCK_FILES.stream().map(blockFile -> {
            var file = TestUtils.getResource("data/blockstreams/" + blockFile.getName());
            var streamFileData = StreamFileData.from(file);
            byte[] bytes = streamFileData.getBytes();
            var blockStream = createBlockStream(getBlock(streamFileData), bytes, blockFile.getName());
            blockFile.setBytes(bytes);
            blockFile.setLoadStart(blockStream.loadStart());
            blockFile.setNodeId(blockStream.nodeId());
            blockFile.setSize(bytes.length);
            return Arguments.of(blockStream, blockFile);
        });
    }
}
