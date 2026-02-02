// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileItem;
import com.hedera.hapi.platform.event.legacy.StateSignatureTransaction;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.util.Lists;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public final class BlockStreamReaderTest {

    public static final List<BlockFile> TEST_BLOCK_FILES = List.of(
            BlockFile.builder()
                    .consensusStart(1764733132492007719L)
                    .consensusEnd(1764733132492007719L)
                    .count(0L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "82c86f04bd1fc71a4ce0d7cb942073e0425f2d4e9cfd2d099a1671b2d5a226a152c1bee9e458fdb09ba65a02c3607b84")
                    .index(25L)
                    .name(BlockFile.getFilename(25, true))
                    .previousHash(
                            "9cdd7fae144d0ba07b8a00a01d7056fb5e34c9507b8db520d41346f94e643f7d5524b4fd8ba4e5d05973d700e9835eee")
                    .roundStart(810L)
                    .roundEnd(844L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1764733134591915512L)
                    .consensusEnd(1764733134591915512L)
                    .count(0L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "a8ee79046f3184f124e05faf51c0d6f615f2551b5c9d2d42991a8a315cdd2ff7163b42b9c04d0d8f5631d075a78a7fd0")
                    .index(26L)
                    .name(BlockFile.getFilename(26, true))
                    .previousHash(
                            "82c86f04bd1fc71a4ce0d7cb942073e0425f2d4e9cfd2d099a1671b2d5a226a152c1bee9e458fdb09ba65a02c3607b84")
                    .roundStart(845L)
                    .roundEnd(879L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1764733073872562823L)
                    .consensusEnd(1764733073872563544L)
                    .count(722L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "87b30cd9634a24ab500b72fecba90bd51a6a1a6de5570b18aea4ccd22c8936c6b77dc7d9ab931de9e2558418945cb96d")
                    .index(0L)
                    .name(BlockFile.getFilename(0, true))
                    .previousHash(
                            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                    .roundStart(1L)
                    .roundEnd(1L)
                    .version(BlockStreamReader.VERSION)
                    .build());

    private final BlockStreamReader reader = new BlockStreamReaderImpl();
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

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
        var preBatchTransactionTimestamp = recordItemBuilder.timestamp();
        var batchTransactionTimestamp = recordItemBuilder.timestamp();
        var precedingChildTimestamp = recordItemBuilder.timestamp();
        var innerTransactionTimestamp1 = recordItemBuilder.timestamp();
        var childTimestamp = recordItemBuilder.timestamp();
        var innerTransactionTimestamp2 = recordItemBuilder.timestamp();
        var innerTransactionTimestamp3 = recordItemBuilder.timestamp();
        var postBatchTransactionTimestamp = recordItemBuilder.timestamp();

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
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction())
                .addItems(transactionResult(preBatchTransactionResult))
                .addItems(stateChanges(preBatchStateChanges))
                .addItems(eventHeader())
                .addItems(batchTransaction())
                .addItems(transactionResult(batchTransactionResult))
                .addItems(stateChanges(batchStateChanges))
                .addItems(signedTransaction())
                .addItems(transactionResult(precedingChildTransactionResult))
                .addItems(transactionResult(innerTransactionResult1))
                .addItems(signedTransaction())
                .addItems(transactionResult(childTransactionResult))
                .addItems(transactionResult(innerTransactionResult2))
                .addItems(transactionResult(innerTransactionResult3))
                .addItems(eventHeader())
                .addItems(signedTransaction())
                .addItems(transactionResult(postBatchTransactionResult))
                .addItems(stateChanges(postBatchStateChanges))
                .addItems(blockFooter())
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
    void readHookExecutionChildTransactions() {
        // given - Create timestamps for parent and child transactions
        var parentTransactionTimestamp = recordItemBuilder.timestamp();
        var hookExecution1Timestamp = recordItemBuilder.timestamp();
        var hookExecution2Timestamp = recordItemBuilder.timestamp();
        var hookExecution3Timestamp = recordItemBuilder.timestamp();
        var postParentTransactionTimestamp = recordItemBuilder.timestamp();

        // Parent transaction (e.g., CryptoTransfer that triggers hooks)
        var parentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(parentTransactionTimestamp)
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();
        var parentStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(parentTransactionTimestamp)
                .build();

        // Hook execution child transactions with same parent
        var hookExecution1Result = TransactionResult.newBuilder()
                .setConsensusTimestamp(hookExecution1Timestamp)
                .setParentConsensusTimestamp(parentTransactionTimestamp)
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        var hookExecution2Result = TransactionResult.newBuilder()
                .setConsensusTimestamp(hookExecution2Timestamp)
                .setParentConsensusTimestamp(parentTransactionTimestamp)
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        var hookExecution3Result = TransactionResult.newBuilder()
                .setConsensusTimestamp(hookExecution3Timestamp)
                .setParentConsensusTimestamp(parentTransactionTimestamp)
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        // Unrelated transaction after hook executions
        var postParentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(postParentTransactionTimestamp)
                .build();
        var postParentStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(postParentTransactionTimestamp)
                .build();

        // Build block with parent and hook execution children
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction()) // parent transaction
                .addItems(transactionResult(parentTransactionResult))
                .addItems(stateChanges(parentStateChanges))
                .addItems(eventHeader())
                .addItems(signedTransaction(TransactionBody.newBuilder()
                        .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                        .build())) // hook execution 1 - contract call
                .addItems(transactionResult(hookExecution1Result))
                .addItems(eventHeader())
                .addItems(signedTransaction(TransactionBody.newBuilder()
                        .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                        .build())) // hook execution 2 - contract call
                .addItems(transactionResult(hookExecution2Result))
                .addItems(eventHeader())
                .addItems(signedTransaction(TransactionBody.newBuilder()
                        .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                        .build())) // hook execution 3 - contract call
                .addItems(transactionResult(hookExecution3Result))
                .addItems(eventHeader())
                .addItems(signedTransaction()) // post-parent transaction
                .addItems(transactionResult(postParentTransactionResult))
                .addItems(stateChanges(postParentStateChanges))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        var blockFile = reader.read(blockStream);
        var items = blockFile.getItems();

        // then
        assertThat(items).hasSize(5);
        var parent = items.get(0);
        var hookExec1 = items.get(1);
        var hookExec2 = items.get(2);
        var hookExec3 = items.get(3);
        var postParent = items.get(4);

        // Verify parent relationships
        assertThat(items).extracting(BlockTransaction::getParent).containsExactly(null, parent, parent, parent, null);

        // Verify all hook executions share the same state change context from parent
        assertThat(parent.getStateChangeContext())
                .isEqualTo(hookExec1.getStateChangeContext())
                .isEqualTo(hookExec2.getStateChangeContext())
                .isEqualTo(hookExec3.getStateChangeContext())
                .isNotEqualTo(postParent.getStateChangeContext());

        // Verify hook execution children are linked via nextSibling
        assertThat(hookExec1.getNextSibling()).isEqualTo(hookExec2);
        assertThat(hookExec2.getNextSibling()).isEqualTo(hookExec3);
        assertThat(hookExec3.getNextSibling()).isNull();

        // Verify parent and unrelated transaction have no nextSibling
        assertThat(parent.getNextSibling()).isNull();
        assertThat(postParent.getNextSibling()).isNull();

        // Verify nextInBatch is not used for hook executions
        assertThat(items).extracting(BlockTransaction::getNextInBatch).containsOnly((BlockTransaction) null);
    }

    @Test
    void readBatchTransactionsNoTransactionResultForSkippedInnerTransactions() {
        // given
        var batchTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        var batchStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(batchTransactionResult.getConsensusTimestamp())
                .addStateChanges(StateChange.newBuilder())
                .build();
        var innerTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .setParentConsensusTimestamp(batchStateChanges.getConsensusTimestamp())
                .setStatus(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE)
                .build();
        var lastTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(batchTransaction())
                .addItems(transactionResult(batchTransactionResult))
                .addItems(stateChanges(batchStateChanges))
                .addItems(transactionResult(innerTransactionResult))
                .addItems(signedTransaction())
                .addItems(transactionResult(lastTransactionResult))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        var blockFile = reader.read(blockStream);

        // then
        long batchTransactionTimestamp =
                DomainUtils.timestampInNanosMax(batchTransactionResult.getConsensusTimestamp());
        long innerTransactionTimestamp =
                DomainUtils.timestampInNanosMax(innerTransactionResult.getConsensusTimestamp());
        long lastTransactionTimestamp = DomainUtils.timestampInNanosMax(lastTransactionResult.getConsensusTimestamp());
        assertThat(blockFile.getItems())
                .hasSize(3)
                .satisfies(
                        items -> assertThat(items.getFirst())
                                .returns(batchTransactionTimestamp, BlockTransaction::getConsensusTimestamp)
                                .returns(null, BlockTransaction::getParentConsensusTimestamp),
                        items -> assertThat(items.get(1))
                                .returns(innerTransactionTimestamp, BlockTransaction::getConsensusTimestamp)
                                .returns(batchTransactionTimestamp, BlockTransaction::getParentConsensusTimestamp),
                        items -> assertThat(items.getLast())
                                .returns(lastTransactionTimestamp, BlockTransaction::getConsensusTimestamp)
                                .returns(null, BlockTransaction::getParentConsensusTimestamp));
    }

    @Test
    void noSignedTransactions() {
        // A standalone state changes block item, with consensus timestamp
        var stateChanges = stateChanges();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(stateChanges)
                .addItems(blockFooter())
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mixedStateChanges(final boolean postConsensusNodeRelease68) {
        // given non-transaction state changes
        // - in a network's genesis block, between the first round header and the first event header
        // - at the end of a round, right before the next round header
        // - at the end of an event. Either there are no signed transactions, or the trailing statechanges don't belong
        //   to the preceding transaction unit
        // - right before block proof
        final var nonTransactionStateChangesType1 = StateChanges.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        final var nonTransactionStateChangesType2 = StateChanges.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        final var transactionTimestamp = recordItemBuilder.timestamp();
        final var transactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        final var transactionStateChanges = StateChanges.newBuilder()
                .setConsensusTimestamp(transactionTimestamp)
                .build();
        final var nonTransactionStateChangeType3 = StateChanges.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        final var nonTransactionStateChangeType4 = StateChanges.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build();
        final var blockBuilder = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(stateChanges(nonTransactionStateChangesType1))
                .addItems(eventHeader())
                .addItems(stateChanges(nonTransactionStateChangesType2))
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(stateChanges(nonTransactionStateChangeType3))
                .addItems(eventHeader())
                .addItems(signedTransaction())
                .addItems(transactionResult(transactionResult))
                .addItems(stateChanges(transactionStateChanges))
                .addItems(stateChanges(nonTransactionStateChangeType4));
        if (postConsensusNodeRelease68) {
            blockBuilder.addItems(blockFooter()).addItems(blockProof());
        }

        final var block =
                blockBuilder.addItems(blockFooter()).addItems(blockProof()).build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        final var blockFile = reader.read(blockStream);

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
    void systemTransactionWithoutTransactionResult() {
        // given
        final var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction(TransactionBody.newBuilder()
                        .setStateSignatureTransaction(StateSignatureTransaction.getDefaultInstance())
                        .build()))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        final var blockFile = reader.read(blockStream);

        // then
        assertThat(blockFile)
                .extracting(BlockFile::getItems, InstanceOfAssertFactories.collection(BlockTransaction.class))
                .isEmpty();
    }

    @Test
    void throwWhenMissingBlockFooter() {
        final var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(blockProof())
                .build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block footer");
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
        final var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(blockFooter())
                .build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block proof");
    }

    @Test
    void thrownWhenSignedTransactionBytesCorrupted() {
        var signedTransaction = BlockItem.newBuilder()
                .setSignedTransaction(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(64)))
                .build();
        var transactionResult = transactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
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
        var signedTransaction = BlockItem.newBuilder()
                .setSignedTransaction(SignedTransaction.newBuilder()
                        .setBodyBytes(DomainUtils.fromBytes(TestUtils.generateRandomByteArray(64)))
                        .build()
                        .toByteString())
                .build();
        var transactionResult = transactionResult(TransactionResult.getDefaultInstance());
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction)
                .addItems(transactionResult)
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Failed to deserialize Transaction");
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

    private BlockItem blockFooter() {
        return BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.getDefaultInstance())
                .build();
    }

    private BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setBlockTimestamp(recordItemBuilder.timestamp()))
                .build();
    }

    private BlockItem blockProof() {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.getDefaultInstance())
                .build();
    }

    private BlockItem eventHeader() {
        return BlockItem.newBuilder()
                .setEventHeader(EventHeader.getDefaultInstance())
                .build();
    }

    private BlockItem roundHeader() {
        return BlockItem.newBuilder()
                .setRoundHeader(RoundHeader.getDefaultInstance())
                .build();
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
        return stateChanges(StateChanges.newBuilder()
                .setConsensusTimestamp(recordItemBuilder.timestamp())
                .build());
    }

    private BlockItem stateChanges(StateChanges stateChanges) {
        return BlockItem.newBuilder().setStateChanges(stateChanges).build();
    }

    private BlockItem transactionOutput(TransactionOutput transactionOutput) {
        return BlockItem.newBuilder().setTransactionOutput(transactionOutput).build();
    }

    private BlockItem transactionResult(TransactionResult transactionResult) {
        return BlockItem.newBuilder().setTransactionResult(transactionResult).build();
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
