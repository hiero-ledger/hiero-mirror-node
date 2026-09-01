// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.transaction.RecordFile.GENESIS_BLOCK_NUMBER;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.EXPECTED_RECORD_FILES;
import static org.hiero.mirror.importer.reader.block.record.WrappedRecordBlockTestUtils.readWrappedRecordBlocks;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import com.hedera.hapi.platform.event.legacy.StateSignatureTransaction;
import com.hederahashgraph.api.proto.java.AtomicBatchTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.assertj.core.util.Lists;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.exception.ProtobufException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;
import org.hiero.mirror.importer.reader.block.record.CompositeRecordFileItemReader;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
@NullUnmarked
public final class BlockStreamReaderTest {

    public static final List<BlockFile> TEST_BLOCK_FILES = List.of(
            BlockFile.builder()
                    .consensusStart(1786397166192063895L)
                    .consensusEnd(1786397175204714106L)
                    .count(759L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "3c421aac698b04fccdd46acad435125ea53af91fe404e787c8ccf4c23a11aa69190468ea1dd797af11d75335f4840749")
                    .index(0L)
                    .name(BlockFile.getFilename(0, true))
                    .previousHash(
                            "bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717")
                    .rawHash(
                            Hex.decode(
                                    "3c421aac698b04fccdd46acad435125ea53af91fe404e787c8ccf4c23a11aa69190468ea1dd797af11d75335f4840749"))
                    .rawPreviousHash(
                            Hex.decode(
                                    "bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717"))
                    .roundStart(2L)
                    .roundEnd(140L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1786397175389467104L)
                    .consensusEnd(1786397177290324104L)
                    .count(75L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "832ec52cfbb467c8d1373afefe682b27132c31ebaf4846fbb6e437c582ce09ea2a59c1d7aadc26827e969efc79a45c89")
                    .index(1L)
                    .name(BlockFile.getFilename(1, true))
                    .previousHash(
                            "3c421aac698b04fccdd46acad435125ea53af91fe404e787c8ccf4c23a11aa69190468ea1dd797af11d75335f4840749")
                    .rawHash(
                            Hex.decode(
                                    "832ec52cfbb467c8d1373afefe682b27132c31ebaf4846fbb6e437c582ce09ea2a59c1d7aadc26827e969efc79a45c89"))
                    .rawPreviousHash(
                            Hex.decode(
                                    "3c421aac698b04fccdd46acad435125ea53af91fe404e787c8ccf4c23a11aa69190468ea1dd797af11d75335f4840749"))
                    .roundStart(141L)
                    .roundEnd(172L)
                    .version(BlockStreamReader.VERSION)
                    .build(),
            BlockFile.builder()
                    .consensusStart(1786398539530723104L)
                    .consensusEnd(1786398541005567104L)
                    .count(25L)
                    .digestAlgorithm(DigestAlgorithm.SHA_384)
                    .hash(
                            "1c417b370965dd85e274b568694d0a5b3325b39f619a840df0161339864a30e389ba3aec4c7763236d2f4c505d031921")
                    .index(5L)
                    .name(BlockFile.getFilename(5, true))
                    .previousHash(
                            "e313796875ef100613684c0ee4ef1a80a13b73aa9c97f5f9592f9b9a25c798b33e36e397fa0e1456b17cee2155b8d7ac")
                    .rawHash(
                            Hex.decode(
                                    "1c417b370965dd85e274b568694d0a5b3325b39f619a840df0161339864a30e389ba3aec4c7763236d2f4c505d031921"))
                    .rawPreviousHash(
                            Hex.decode(
                                    "e313796875ef100613684c0ee4ef1a80a13b73aa9c97f5f9592f9b9a25c798b33e36e397fa0e1456b17cee2155b8d7ac"))
                    .roundStart(282L)
                    .roundEnd(315L)
                    .version(BlockStreamReader.VERSION)
                    .build());

    private static final RecursiveComparisonConfiguration RECORD_FILE_COMPARISON_CONFIG =
            RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields(
                            "bytes",
                            "loadStart",
                            "initialState",
                            "items",
                            "previousWrappedRecordBlockHash",
                            "receiptsRoot",
                            "wrappedRecordBlockHash")
                    .build();

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @Mock
    private InitialStateReader initialStateReader;

    private BlockStreamReader reader;

    @BeforeEach
    void setup() {
        reader = new BlockStreamReaderImpl(
                initialStateReader, new CompositeRecordFileItemReader(new SidecarProperties()));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("readTestArgumentsProvider")
    void read(BlockStream blockStream, BlockFile expected) {
        var actual = reader.read(blockStream);
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("blockHeader", "blockProof", "items", "lastLedgerIdPublicationTransaction")
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

    @ParameterizedTest(name = "{1}")
    @MethodSource("readWrappedRecordBlocksArgumentsProvider")
    void readWrappedRecordBlock(final Block block, final long blockNumber, final RecordFile expectedRecordFile) {
        // given
        final RecordFile.InitialState expectedInitialState;
        if (blockNumber == GENESIS_BLOCK_NUMBER) {
            expectedInitialState = new RecordFile.InitialState();
            doReturn(expectedInitialState).when(initialStateReader).read(any());
        } else {
            expectedInitialState = null;
        }

        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(blockNumber, true));
        final byte[] bytes = Objects.requireNonNull(blockStream.bytes());
        final long loadStart = blockStream.loadStart();

        // when
        final var blockFile = reader.read(blockStream);

        // then
        assertThat(blockFile)
                .returns(bytes, BlockFile::getBytes)
                .returns(loadStart, BlockFile::getLoadStart)
                .returns(bytes.length, BlockFile::getSize)
                .returns(BlockStreamReader.VERSION, BlockFile::getVersion)
                .satisfies(b -> assertThat(b.getHash()).isNotNull(), b -> assertThat(b.getPreviousHash())
                        .isNotNull())
                .extracting(BlockFile::getRecordFile)
                .returns(expectedInitialState, RecordFile::getInitialState)
                .returns(loadStart, RecordFile::getLoadStart)
                .returns(blockFile.getRawPreviousHash(), RecordFile::getPreviousWrappedRecordBlockHash)
                .returns(blockFile.getRawHash(), RecordFile::getWrappedRecordBlockHash)
                .usingRecursiveComparison(RECORD_FILE_COMPARISON_CONFIG)
                .isEqualTo(expectedRecordFile);
        verify(initialStateReader, times(blockNumber == 0 ? 1 : 0)).read(any());
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
        verifyNoInteractions(initialStateReader);
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
        assertThat(items).extracting(BlockTransaction::getNextInBatch).containsOnlyNulls();
        verifyNoInteractions(initialStateReader);
    }

    @Test
    void readLedgerIdPublicationTransactions() {
        // given
        var defaultTransactionBody = TransactionBody.newBuilder()
                .setLedgerIdPublication(LedgerIdPublicationTransactionBody.getDefaultInstance())
                .build();
        var firstTimestamp = recordItemBuilder.timestamp();
        var secondTimestamp = recordItemBuilder.timestamp();
        var thirdTimestamp = recordItemBuilder.timestamp();
        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction(defaultTransactionBody))
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(firstTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build()))
                .addItems(signedTransaction(defaultTransactionBody))
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(secondTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build()))
                .addItems(signedTransaction(defaultTransactionBody))
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(thirdTimestamp)
                        .setStatus(ResponseCodeEnum.INVALID_SIGNATURE)
                        .build()))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(0, true));

        // when
        var actual = reader.read(blockStream);

        // then
        assertThat(actual.getLastLedgerIdPublicationTransaction())
                .returns(DomainUtils.timestampInNanosMax(secondTimestamp), BlockTransaction::getConsensusTimestamp);
        verifyNoInteractions(initialStateReader);
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
        verifyNoInteractions(initialStateReader);
    }

    @Test
    void readSignedTransactionsWithoutEventHeader() {
        // given
        final var firstTimestamp = recordItemBuilder.timestamp();
        final var secondTimestamp = recordItemBuilder.timestamp();
        final var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(signedTransaction())
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(firstTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build()))
                .addItems(signedTransaction())
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(secondTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build()))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(0, true));

        // when
        final var actual = reader.read(blockStream);

        // then
        assertThat(actual)
                .extracting(BlockFile::getItems, InstanceOfAssertFactories.list(BlockTransaction.class))
                .extracting(BlockTransaction::getConsensusTimestamp)
                .containsExactly(
                        DomainUtils.timestampInNanosMax(firstTimestamp),
                        DomainUtils.timestampInNanosMax(secondTimestamp));
        verifyNoInteractions(initialStateReader);
    }

    @Test
    void noSignedTransactions() {
        // A standalone state changes block item, with consensus timestamp
        final var stateChanges = stateChanges();
        final var blockHeader = blockHeader();
        final var block = Block.newBuilder()
                .addItems(blockHeader)
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(stateChanges)
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        final var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        final long timestamp =
                DomainUtils.timestampInNanosMax(blockHeader.getBlockHeader().getBlockTimestamp());
        assertThat(reader.read(blockStream))
                .returns(timestamp, BlockFile::getConsensusEnd)
                .returns(timestamp, BlockFile::getConsensusStart)
                .returns(0L, BlockFile::getCount)
                .returns(List.of(), BlockFile::getItems)
                .returns(BlockStreamReader.VERSION, BlockFile::getVersion);
        verifyNoInteractions(initialStateReader);
    }

    @Test
    void outOfOrderConsensusTimestampsUseMinMax() {
        // given - three transactions with out-of-order timestamps: middle < first < last
        // first transaction gets a later timestamp
        var firstTimestamp =
                Timestamp.newBuilder().setSeconds(1000).setNanos(500).build();
        // second transaction gets an earlier timestamp (out of order)
        var secondTimestamp =
                Timestamp.newBuilder().setSeconds(1000).setNanos(100).build();
        // third transaction gets the latest timestamp
        var thirdTimestamp =
                Timestamp.newBuilder().setSeconds(1000).setNanos(900).build();

        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction())
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(firstTimestamp)
                        .build()))
                .addItems(signedTransaction())
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(secondTimestamp)
                        .build()))
                .addItems(signedTransaction())
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(thirdTimestamp)
                        .build()))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));

        // when
        var blockFile = reader.read(blockStream);

        // then - consensusStart is the minimum (second tx), consensusEnd is the maximum (third tx)
        assertThat(blockFile)
                .returns(DomainUtils.timestampInNanosMax(secondTimestamp), BlockFile::getConsensusStart)
                .returns(DomainUtils.timestampInNanosMax(thirdTimestamp), BlockFile::getConsensusEnd)
                .returns(3L, BlockFile::getCount);
        verifyNoInteractions(initialStateReader);
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
        verifyNoInteractions(initialStateReader);
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
        verifyNoInteractions(initialStateReader);
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
        verifyNoInteractions(initialStateReader);
    }

    @Test
    void throwWhenMissingBlockHeader() {
        var block = Block.newBuilder().addItems(blockProof()).build();
        var blockStream = createBlockStream(block, null, BlockFile.getFilename(1, true));
        assertThatThrownBy(() -> reader.read(blockStream))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessageContaining("Missing block header");
        verifyNoInteractions(initialStateReader);
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
        verifyNoInteractions(initialStateReader);
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
        assertThatThrownBy(() -> reader.read(blockStream)).isInstanceOf(ProtobufException.class);
        verifyNoInteractions(initialStateReader);
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
        assertThatThrownBy(() -> reader.read(blockStream)).isInstanceOf(ProtobufException.class);
        verifyNoInteractions(initialStateReader);
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
                .setBlockFooter(BlockFooter.newBuilder()
                        .setPreviousBlockRootHash(recordItemBuilder.bytes(48))
                        .setRootHashOfAllBlockHashesTree(recordItemBuilder.bytes(48))
                        .setStartOfBlockStateRootHash(recordItemBuilder.bytes(48)))
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

    private BlockItem transactionResult(TransactionResult transactionResult) {
        return BlockItem.newBuilder().setTransactionResult(transactionResult).build();
    }

    private static BlockStream createBlockStream(Block block, byte @Nullable [] bytes, String filename) {
        if (bytes == null) {
            bytes = TestUtils.zstd(block.toByteArray());
        }

        long blockCompleteTime = System.currentTimeMillis();
        return new BlockStream(
                block.getItemsList(), blockCompleteTime, bytes, filename, blockCompleteTime - 1000, bytes.length);
    }

    @SneakyThrows
    private static Block getBlock(StreamFileData blockFileData) {
        try (var is = blockFileData.getInputStream()) {
            return Block.parseFrom(is);
        }
    }

    @SneakyThrows
    private static Stream<Arguments> readTestArgumentsProvider() {
        return TEST_BLOCK_FILES.stream().map(blockFile -> {
            final var bucketFilename = StreamType.BLOCK.toBucketFilename(blockFile.getName());
            final var file = TestUtils.getResource("data/blockstreams/" + bucketFilename);
            final var streamFileData = StreamFileData.from(file);
            final byte[] bytes = streamFileData.getBytes();
            final var blockStream = createBlockStream(getBlock(streamFileData), bytes, blockFile.getName());
            blockFile.setBytes(bytes);
            blockFile.setLoadStart(blockStream.loadStart());
            blockFile.setSize(bytes.length);
            return Arguments.of(blockStream, Named.of(blockFile.getName(), blockFile));
        });
    }

    private static Stream<Arguments> readWrappedRecordBlocksArgumentsProvider() {
        return readWrappedRecordBlocks().stream().map(block -> {
            final long blockNumber = block.getItems(0).getBlockHeader().getNumber();
            return Arguments.of(block, blockNumber, EXPECTED_RECORD_FILES.get(blockNumber));
        });
    }
}
