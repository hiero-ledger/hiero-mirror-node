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
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.util.Lists;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class BlockStreamReaderTest {

    public static final List<BlockFile> TEST_BLOCK_FILES = List.of(
            BlockFile.builder()
                    .consensusStart(1756926885008266625L)
                    .consensusEnd(1756926887048223043L)
                    .count(4L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "6865f367aa335585aef74c9714a2211408424d26b779bbb03967303b538935aced9ee2c47587b966d3384171ad5821f9")
                    .index(142L)
                    .name(BlockFile.getFilename(142, true))
                    .previousHash(
                            "b0c2f86aa1b71a90d636fdfe4cdb187c8140732c7e6818d9074a679fb6e00c3c6c530543bc2ccb85e19fbdb667fac71f")
                    .roundStart(4929L)
                    .roundEnd(4963L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1756926888008165376L)
                    .consensusEnd(1756926889028605543L)
                    .count(4L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "9874981fcf657cf4f42ecf8fd0f9dafcc2a9e49d212624631b31289f6fff1484775389f40d44a42596f2b52d56471c17")
                    .index(143L)
                    .name(BlockFile.getFilename(143, true))
                    .previousHash(
                            "6865f367aa335585aef74c9714a2211408424d26b779bbb03967303b538935aced9ee2c47587b966d3384171ad5821f9")
                    .roundStart(4964L)
                    .roundEnd(4998L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1755661847138340948L)
                    .consensusEnd(1755661847138341669L)
                    .count(722L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "46855633dbd4fcc466353127bce879856645cc2efed9b4b9f0227ab6619288d3075728ed594992a743de32fcd20efd4b")
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
                .extracting(BlockFile::getItems, InstanceOfAssertFactories.collection(BlockTransaction.class))
                .map(BlockTransaction::getPrevious)
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
        var innerTransactionTimestamp3 = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 4);
        var postBatchTransactionTimestamp = TestUtils.toTimestamp(now.getEpochSecond(), now.getNano() + 5);

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

        var innerTransactionResult3 = TransactionResult.newBuilder()
                .setConsensusTimestamp(innerTransactionTimestamp3)
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
                .addItems(signedTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(preBatchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(preBatchStateChanges))
                .addItems(eventHeader)
                .addItems(batchTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(batchTransactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(batchStateChanges))
                .addItems(signedTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(precedingChildTransactionResult))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult1))
                .addItems(signedTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(childTransactionResult))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult2))
                .addItems(BlockItem.newBuilder().setTransactionResult(innerTransactionResult3))
                .addItems(eventHeader)
                .addItems(signedTransaction())
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

        var expectedParents = Lists.newArrayList(
                null,
                null,
                batchParentItem,
                batchParentItem,
                innerTransaction1,
                batchParentItem,
                batchParentItem,
                null);
        var expectedPrevious = new ArrayList<>(items);
        expectedPrevious.addFirst(null);
        expectedPrevious.removeLast();

        assertThat(items).hasSize(8);
        assertThat(TestUtils.toTimestamp(batchParentItem.getConsensusTimestamp()))
                .isEqualTo(batchTransactionTimestamp);
        assertThat(items).map(BlockTransaction::getParent).containsExactlyElementsOf(expectedParents);
        assertThat(items).map(BlockTransaction::getPrevious).containsExactlyElementsOf(expectedPrevious);
        assertThat(batchParentItem.getStateChangeContext())
                .isEqualTo(precedingChild.getStateChangeContext())
                .isEqualTo(innerTransaction1.getStateChangeContext())
                .isEqualTo(child.getStateChangeContext())
                .isEqualTo(innerTransaction2.getStateChangeContext())
                .isNotEqualTo(items.getFirst().getStateChangeContext())
                .isNotEqualTo(items.getLast().getStateChangeContext());
        var batchInnerLinks =
                items.stream().map(BlockTransaction::getNextInBatch).toList();
        List<BlockTransaction> expected =
                Lists.newArrayList(null, null, null, items.get(5), null, items.get(6), null, null);
        assertThat(batchInnerLinks).containsExactlyElementsOf(expected);
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
                .addItems(batchTransaction())
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
    void noSignedTransactions() {
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
        // given non-transaction state changes
        // - appear after first round header and before the first even header in the round
        // - appear right before the next round header
        // - right before block proof
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var nonTransactionStateChangesType1 = StateChanges.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(domainBuilder.timestamp()))
                .build();
        var nonTransactionStateChangesType2 = StateChanges.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(domainBuilder.timestamp()))
                .build();
        var transactionTimestamp = TestUtils.toTimestamp(domainBuilder.timestamp());
        var transactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        var transactionStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        var nonTransactionStateChangeType3 = StateChanges.newBuilder()
                .setConsensusTimestamp(TestUtils.toTimestamp(domainBuilder.timestamp()))
                .build();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(BlockItem.newBuilder().setStateChanges(nonTransactionStateChangesType1))
                .addItems(eventHeader)
                .addItems(BlockItem.newBuilder().setStateChanges(nonTransactionStateChangesType2))
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(signedTransaction())
                .addItems(BlockItem.newBuilder().setTransactionResult(transactionResult))
                .addItems(BlockItem.newBuilder().setStateChanges(transactionStateChanges))
                .addItems(BlockItem.newBuilder().setStateChanges(nonTransactionStateChangeType3))
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        var blockFile = reader.read(blockStream);

        // then the block item should only have its own state changes
        assertThat(blockFile)
                .extracting(BlockFile::getItems, InstanceOfAssertFactories.collection(BlockTransaction.class))
                .hasSize(1)
                .first()
                .extracting(BlockTransaction::getStateChanges, InstanceOfAssertFactories.collection(StateChanges.class))
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
                .addItems(signedTransaction())
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing transaction result");
    }

    @Test
    void thrownWhenSignedTransactionBytesCorrupted() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var signedTransaction = BlockItem.newBuilder()
                .setSignedTransaction(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(64)))
                .build();
        var transactionResult = BlockItem.newBuilder().setTransactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(signedTransaction)
                .addItems(transactionResult)
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to deserialize Transaction");
    }

    @Test
    void thrownWhenTransactionBodyBytesCorrupted() {
        var roundHeader = BlockItem.newBuilder().setRoundHeader(RoundHeader.getDefaultInstance());
        var eventHeader = BlockItem.newBuilder().setEventHeader(EventHeader.getDefaultInstance());
        var signedTransaction = BlockItem.newBuilder()
                .setSignedTransaction(SignedTransaction.newBuilder()
                        .setBodyBytes(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(64)))
                        .build()
                        .toByteString())
                .build();
        var transactionResult = BlockItem.newBuilder().setTransactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader)
                .addItems(eventHeader)
                .addItems(signedTransaction)
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

    private BlockItem batchTransaction() {
        var cryptoTransferSignedBytes = SignedTransaction.newBuilder()
                .setBodyBytes(TransactionBody.newBuilder()
                        .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                        .build()
                        .toByteString())
                .build()
                .toByteString();
        return batchTransaction(
                List.of(cryptoTransferSignedBytes, cryptoTransferSignedBytes, cryptoTransferSignedBytes));
    }

    private BlockItem batchTransaction(List<ByteString> innerTransactions) {
        var transaction = TransactionBody.newBuilder()
                .setAtomicBatch(AtomicBatchTransactionBody.newBuilder()
                        .addAllTransactions(innerTransactions)
                        .build())
                .build();
        return signedTransaction(transaction);
    }

    private BlockItem signedTransaction() {
        return signedTransaction(TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .build());
    }

    private BlockItem signedTransaction(TransactionBody transactionBody) {
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .build();
        return BlockItem.newBuilder()
                .setSignedTransaction(signedTransaction.toByteString())
                .build();
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

        return new BlockStream(block.getItemsList(), bytes, filename, TestUtils.id(), TestUtils.id());
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
