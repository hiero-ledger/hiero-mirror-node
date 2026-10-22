// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ACCOUNTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_BYTECODE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapDeleteChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapUpdateChange;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.trace.protoc.ContractSlotUsage;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.SlotRead;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hedera.hapi.block.stream.trace.protoc.WrittenSlotKeys;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import com.hederahashgraph.api.proto.java.HookEntityId;
import com.hederahashgraph.api.proto.java.HookId;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
final class ContractTransformerTest extends AbstractTransformerTest {

    private static final ContractID HTS_PRECOMPILE_ADDRESS =
            ContractID.newBuilder().setContractNum(0x167).build();

    // createdContractIDs set by RecordItemBuilder has random contract ids, so the customizers clear it, and a test sets
    // the expected createdContractIDs with createdContractIds()
    private static final Consumer<TransactionRecord.Builder> ETHEREUM_RECORD_CUSTOMIZER = b -> {
        // calculated in parser
        b.clearEthereumHash();
        contractResultBuilder(b).ifPresent(ContractFunctionResult.Builder::clearCreatedContractIDs);
    };

    private static final Consumer<TransactionRecord.Builder> EXPLICIT_CONTRACT_RESULT_CUSTOMIZER =
            b -> contractResultBuilder(b)
                    .ifPresent(builder -> builder.clearAmount()
                            .clearCreatedContractIDs()
                            .clearFunctionParameters()
                            .clearGas());

    private static Optional<ContractFunctionResult.Builder> contractResultBuilder(
            TransactionRecord.Builder recordBuilder) {
        if (recordBuilder.hasContractCallResult()) {
            return Optional.of(recordBuilder.getContractCallResultBuilder());
        }

        if (recordBuilder.hasContractCreateResult()) {
            return Optional.of(recordBuilder.getContractCreateResultBuilder());
        }

        return Optional.empty();
    }

    private static Consumer<TransactionRecord.Builder> createdContractIds(ContractID... contractIds) {
        return b -> contractResultBuilder(b)
                .ifPresent(builder -> builder.clearCreatedContractIDs().addAllCreatedContractIDs(List.of(contractIds)));
    }

    private static Stream<Arguments> provideContractIds() {
        var contractId = recordItemBuilder.contractId();
        var encoded = contractId.toBuilder()
                .setEvmAddress(DomainUtils.fromBytes(DomainUtils.toEvmAddress(contractId)))
                .build();
        var create2EvmAddress = contractId.toBuilder()
                .setEvmAddress(recordItemBuilder.evmAddress().getValue())
                .build();

        return Stream.of(
                Arguments.of("plain", contractId, contractId),
                Arguments.of("encoded evm address", encoded, contractId),
                Arguments.of("create2 evm address", create2EvmAddress, contractId));
    }

    @Test
    void contractCall() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                // will add back contract bytecode and contract state change sidecar records once support is added
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem, false).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contractCallWithChildContractCreate(boolean explicitInitcode) {
        // given
        var contractId = recordItemBuilder.contractId();
        var runtimeBytecode = recordItemBuilder.bytes(1024);
        var expectedContractCall = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(contractId))
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var contractCall = blockTransactionBuilder
                .contractCall(expectedContractCall, false)
                .stateChanges(stateChangesFromChildContractCreate(contractId, runtimeBytecode))
                .build();
        var transactionId = expectedContractCall.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(1)
                .build();
        var expectedContractCreate = recordItemBuilder
                .contractCreate(contractId)
                .record(r -> r.setParentConsensusTimestamp(
                        expectedContractCall.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.transactionIndex(1))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(
                        records, false, contractBytecode(contractId, explicitInitcode, runtimeBytecode, false)))
                .customize(this::finalize)
                .build();
        var contractCreate = blockTransactionBuilder
                .contractCreate(expectedContractCreate)
                .previous(contractCall)
                .build();
        var blockFile =
                blockFileBuilder.items(List.of(contractCall, contractCreate)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(expectedContractCall, expectedContractCreate);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items).map(RecordItem::getParent).containsExactly(null, items.getFirst());
        });
    }

    @Test
    void contractCallUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCallResultBuilder()
                        .clearBloom()
                        .clearLogInfo()
                        .clearContractNonces()
                        .clearSignerNonce())
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .status(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem, false).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallInvalidContractId() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCall()
                .transactionBody(b -> b.getContractIDBuilder().setEvmAddress(recordItemBuilder.bytes(20)))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCallResultBuilder()
                        .clearBloom()
                        .clearContractID()
                        .clearContractNonces()
                        .clearLogInfo()
                        .clearSignerNonce())
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INVALID_CONTRACT_ID)
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCall(expectedRecordItem, false).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contractCreate(boolean initcodeFromFile) {
        // given
        var contractId = recordItemBuilder.contractId();
        var expectedRecordItem = recordItemBuilder
                .contractCreate(contractId)
                .transactionBody(b -> {
                    if (!initcodeFromFile) {
                        b.setInitcode(recordItemBuilder.bytes(1024));
                    }
                })
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(contractId))
                .sidecarRecords(records -> customizeSidecarRecords(
                        records,
                        true,
                        contractBytecode(contractId, false, recordItemBuilder.bytes(1024), true),
                        this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCreateInBatchWithChildContractCreate() {
        // given
        var innerContractId = recordItemBuilder.contractId();
        var childContractId = recordItemBuilder.contractId();
        var atomicBatch =
                recordItemBuilder.atomicBatch().customize(this::finalize).build();
        var atomicBatchBlockTransaction =
                blockTransactionBuilder.atomicBatch(atomicBatch).build();
        var batchConsensusTimestamp = atomicBatch.getTransactionRecord().getConsensusTimestamp();
        var innerContractCreate = recordItemBuilder
                .contractCreate(innerContractId)
                .record(r -> r.setParentConsensusTimestamp(batchConsensusTimestamp))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(innerContractId, childContractId))
                .recordItem(r -> r.transactionIndex(1))
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var innerContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(innerContractCreate)
                .previous(atomicBatchBlockTransaction)
                .build();
        var childContractCreate = childContractCreate(childContractId, innerContractCreate, 1, 2);
        var childContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(childContractCreate)
                .previous(innerContractCreateBlockTransaction)
                .build();
        var innerContractCall = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(batchConsensusTimestamp))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .recordItem(r -> r.transactionIndex(3))
                .sidecarRecords(List::clear)
                .customize(this::finalize)
                .build();
        var innerContractCallBlockTransaction = blockTransactionBuilder
                .contractCall(innerContractCall, true)
                .previous(childContractCreateBlockTransaction)
                .build();
        innerContractCreateBlockTransaction.setNextInBatch(innerContractCallBlockTransaction);
        var blockFile = blockFileBuilder
                .items(List.of(
                        atomicBatchBlockTransaction,
                        innerContractCreateBlockTransaction,
                        childContractCreateBlockTransaction,
                        innerContractCallBlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(atomicBatch, innerContractCreate, childContractCreate, innerContractCall);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items)
                    .map(RecordItem::getParent)
                    .containsExactly(null, items.getFirst(), items.get(1), items.getFirst());
        });
    }

    @Test
    void contractCreateReverted() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCreate()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCreateResultBuilder()
                        .clearBloom()
                        .clearLogInfo()
                        .clearContractNonces()
                        .clearSignerNonce())
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .status(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCreateWithChildContractCreates() {
        // given
        // a hollow account finalized as a contract by CREATE2 keeps its existing, smaller, entity num
        var hollowContractId = recordItemBuilder.contractId();
        var contractId = recordItemBuilder.contractId();
        var childContractId = recordItemBuilder.contractId();
        var contractCreate = recordItemBuilder
                .contractCreate(contractId)
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(hollowContractId, contractId, childContractId))
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var contractCreateBlockTransaction =
                blockTransactionBuilder.contractCreate(contractCreate).build();
        var childContractCreate = childContractCreate(childContractId, contractCreate, 1, 1);
        var childContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(childContractCreate)
                .previous(contractCreateBlockTransaction)
                .build();
        var hollowContractCreate = childContractCreate(hollowContractId, contractCreate, 2, 2);
        var hollowContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(hollowContractCreate)
                .previous(childContractCreateBlockTransaction)
                .build();
        var blockFile = blockFileBuilder
                .items(List.of(
                        contractCreateBlockTransaction,
                        childContractCreateBlockTransaction,
                        hollowContractCreateBlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(contractCreate, childContractCreate, hollowContractCreate);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items).map(RecordItem::getParent).containsExactly(null, items.getFirst(), items.getFirst());
        });
    }

    @Test
    void contractCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractCreate()
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(Builder::clearContractID)
                .sidecarRecords(List::clear)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.contractCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @MethodSource("provideContractIds")
    @ParameterizedTest(name = "delete contract with {0} contract id")
    void contractDelete(String type, ContractID contractIdInBody, ContractID contractIdInReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractDelete()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> r.setContractID(contractIdInReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractDeleteUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractDelete()
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @MethodSource("provideContractIds")
    @ParameterizedTest(name = "update contract with {0} contract id")
    void contractUpdate(String type, ContractID contractIdInBody, ContractID contractIdInReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> r.setContractID(contractIdInReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractUpdateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractUpdate()
                .receipt(Builder::clearContractID)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void ethereumCall() {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction(false)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void ethereumCreateWithChild() {
        // given
        var contractId = recordItemBuilder.contractId();
        var runtimeBytecode = recordItemBuilder.bytes(1024);
        var ethereumCreate = recordItemBuilder
                .ethereumTransaction(true)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .record(r -> createdContractIds(
                                contractId, r.getContractCreateResult().getContractID())
                        .accept(r))
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var ethereumCreateBlockTransaction = blockTransactionBuilder
                .ethereum(ethereumCreate)
                .stateChanges(stateChangesFromChildContractCreate(contractId, runtimeBytecode))
                .build();
        var parentConsensusTimestamp = ethereumCreate.getTransactionRecord().getConsensusTimestamp();
        var transactionId = ethereumCreate.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(1)
                .build();
        var childCreate = recordItemBuilder
                .contractCreate(contractId)
                .recordItem(r -> r.transactionIndex(1))
                .sidecarRecords(records -> customizeSidecarRecords(
                        records, false, contractBytecode(contractId, false, runtimeBytecode, false)))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .record(r -> r.clearEvmAddress()
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .getContractCreateResultBuilder()
                        .clearBloom()
                        .clearLogInfo())
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .customize(this::finalize)
                .build();
        var childCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(childCreate)
                .previous(ethereumCreateBlockTransaction)
                .build();
        var blockFile = blockFileBuilder
                .items(List.of(ethereumCreateBlockTransaction, childCreateBlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(ethereumCreate, childCreate);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items).map(RecordItem::getParent).containsExactly(null, items.getFirst());
        });
    }

    @Test
    void ethereumCreateWithSelfAndNestedChildContractCreates() {
        // given
        // consensus nodes externalize a child contract create for the contract created by an ethereum transaction
        var nestedContractId = recordItemBuilder.contractId();
        var ethereumCreate = recordItemBuilder
                .ethereumTransaction(true)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .record(r -> createdContractIds(
                                nestedContractId, r.getContractCreateResult().getContractID())
                        .accept(r))
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var ethereumCreateBlockTransaction =
                blockTransactionBuilder.ethereum(ethereumCreate).build();
        var contractId =
                ethereumCreate.getTransactionRecord().getContractCreateResult().getContractID();
        var selfContractCreate = childContractCreate(contractId, ethereumCreate, 1, 1);
        var selfContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(selfContractCreate)
                .previous(ethereumCreateBlockTransaction)
                .build();
        var nestedContractCreate = childContractCreate(nestedContractId, ethereumCreate, 2, 2);
        var nestedContractCreateBlockTransaction = blockTransactionBuilder
                .contractCreate(nestedContractCreate)
                .previous(selfContractCreateBlockTransaction)
                .build();
        var blockFile = blockFileBuilder
                .items(List.of(
                        ethereumCreateBlockTransaction,
                        selfContractCreateBlockTransaction,
                        nestedContractCreateBlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(ethereumCreate, selfContractCreate, nestedContractCreate);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            assertThat(items).map(RecordItem::getParent).containsExactly(null, items.getFirst(), items.getFirst());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ethereumNoUsedGas(boolean create) {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .record(r -> {
                    if (create) {
                        createdContractIds(r.getContractCreateResult().getContractID())
                                .accept(r);
                    }
                })
                .record(r -> contractResultBuilder(r).ifPresent(ContractFunctionResult.Builder::clearGasUsed))
                .receipt(Builder::clearContractID)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void ethereumUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction()
                .receipt(Builder::clearContractID)
                .record(r -> r.clearContractCallResult().clearContractCreateResult())
                .record(ETHEREUM_RECORD_CUSTOMIZER)
                .sidecarRecords(List::clear)
                .status(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.ethereum(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Disabled("Fix and enable when EvmTraceData is fully supported")
    @Test
    void contractCallWithHTSPrecompileAndTrackPendingFungibleAirdropAmount() {
        // given
        // both tokens are fungible
        // receiver2 has unlimited auto token association slot, so she never gets pending airdrops
        var tokenId1 = recordItemBuilder.tokenId();
        var tokenId2 = recordItemBuilder.tokenId();
        var receiver1 = recordItemBuilder.accountId();
        var receiver2 = recordItemBuilder.accountId();
        var sender = recordItemBuilder.accountId();
        var contractCallRecordItem =
                recordItemBuilder.contractCall().customize(this::finalize).build();
        var parentConsensusTimestamp =
                contractCallRecordItem.getTransactionRecord().getConsensusTimestamp();
        // use the same precompile contract call result to work around the complexity to get the matching one
        // when building contract call output for child transactions
        var childPrecompileContractCallResult = htsPrecompileContractCallResult();
        var tokenAirdrop1RecordItem = recordItemBuilder
                .tokenAirdrop()
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId1)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-2000))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(1200))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver2)
                                        .setAmount(800)))
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId2)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-900))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(900))))
                .record(r -> r.clearTokenTransferLists()
                        .setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(tokenId1)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-800))
                                // receiver2 has unlimited auto association slot, resulting in immediate transfer
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver2)
                                        .setAmount(800)))
                        // there is preexisting pending airdrop, so amount should be accumulated. However since it's
                        // cancelled afterward, there is no state changes to tell the exact amount, the best shot
                        // would be the amount to transfer in the body
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId1))
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(900, receiver1, sender, tokenId2)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        // cancels pending airdrop <receiver1, sender, tokenId1>
        var tokenCancelAirdropRecordItem = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops()
                        .addPendingAirdrops(PendingAirdropId.newBuilder()
                                .setReceiverId(receiver1)
                                .setSenderId(sender)
                                .setFungibleTokenType(tokenId1)))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(2))
                .customize(this::finalize)
                .build();
        // airdrop again to accumulate the amount for <receiver1, sender, tokenId2>
        var tokenAirdrop2RecordItem = recordItemBuilder
                .tokenAirdrop()
                .transactionBody(b -> b.clearTokenTransfers()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(tokenId2)
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(-300))
                                .addTransfers(AccountAmount.newBuilder()
                                        .setAccountID(receiver1)
                                        .setAmount(300))))
                .record(r -> r.clearTokenTransferLists()
                        .setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId2)))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var builders = List.of(
                blockTransactionBuilder.contractCall(contractCallRecordItem, false),
                blockTransactionBuilder.tokenAirdrop(tokenAirdrop1RecordItem),
                blockTransactionBuilder.defaultBlockItem(tokenCancelAirdropRecordItem),
                blockTransactionBuilder.tokenAirdrop(tokenAirdrop2RecordItem));
        var blockItems = new ArrayList<BlockTransaction>();
        var childTransactionOutput = callContractOutput(childPrecompileContractCallResult);
        for (var builder : builders) {
            if (blockItems.isEmpty()) {
                // the parent
                blockItems.add(builder.stateChanges(s -> {
                            var stateChangesBuilder =
                                    s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                            var mapUpdate = pendingAirdropMapUpdate(
                                    fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId2));
                            s.add(stateChangesBuilder.addStateChanges(mapUpdate).build());
                        })
                        .build());
            } else {
                blockItems.add(builder
                        // link previous to get parent's state changes
                        .previous(blockItems.getLast())
                        .stateChanges(List::clear)
                        .transactionOutputs(outputs -> outputs.putAll(childTransactionOutput))
                        .build());
            }
        }
        var blockFile = blockFileBuilder.items(blockItems).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expectedItems = List.of(
                contractCallRecordItem, tokenAirdrop1RecordItem, tokenCancelAirdropRecordItem, tokenAirdrop2RecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expectedItems);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyElementsOf(expectedParentItems);
        });
    }

    @Disabled("Fix and enable when EvmTraceData is fully supported")
    @Test
    void contractCallWithHTSPrecompileAndTrackTotalSupply() {
        // given
        var tokenId1 = recordItemBuilder.tokenId(); // a token created before contract call
        var contractCallRecordItem =
                recordItemBuilder.contractCall().customize(this::finalize).build();
        var parentConsensusTimestamp =
                contractCallRecordItem.getTransactionRecord().getConsensusTimestamp();
        // use the same precompile contract call result to work around the complexity to get the matching one
        // when building contract call output for child transactions
        var childPrecompileContractCallResult = htsPrecompileContractCallResult();
        var token2CreateRecordItem = recordItemBuilder
                .tokenCreate()
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var tokenId2 =
                token2CreateRecordItem.getTransactionRecord().getReceipt().getTokenID();
        var token3CreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.setInitialSupply(1000))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(2))
                .receipt(r -> r.setNewTotalSupply(1000))
                .customize(this::finalize)
                .build();
        var tokenId3 =
                token3CreateRecordItem.getTransactionRecord().getReceipt().getTokenID();
        var token2MintRecordItem = recordItemBuilder
                .tokenMint(TokenType.FUNGIBLE_COMMON)
                .transactionBody(b -> b.setToken(tokenId2).setAmount(1000))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(1000))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var token2BurnRecordItem = recordItemBuilder
                .tokenBurn()
                .transactionBody(b -> b.setToken(tokenId2).setAmount(200).clearSerialNumbers())
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(800))
                .recordItem(r -> r.transactionIndex(4))
                .customize(this::finalize)
                .build();
        var token2WipeRecordItem = recordItemBuilder
                .tokenWipe()
                .transactionBody(b -> b.setToken(tokenId2).setAmount(300).clearSerialNumbers())
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(500))
                .recordItem(r -> r.transactionIndex(5))
                .customize(this::finalize)
                .build();
        var token1DeleteRecordItem = recordItemBuilder
                .tokenDelete()
                .transactionBody(b -> b.setToken(tokenId1))
                .record(r -> r.setContractCallResult(childPrecompileContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .recordItem(r -> r.transactionIndex(6))
                .customize(this::finalize)
                .build();
        var builders = List.of(
                blockTransactionBuilder.contractCall(contractCallRecordItem, false),
                blockTransactionBuilder.tokenCreate(token2CreateRecordItem),
                blockTransactionBuilder.tokenCreate(token3CreateRecordItem),
                blockTransactionBuilder.tokenMint(token2MintRecordItem),
                blockTransactionBuilder.tokenBurn(token2BurnRecordItem),
                blockTransactionBuilder.tokenWipe(token2WipeRecordItem),
                blockTransactionBuilder.defaultBlockItem(token1DeleteRecordItem));
        var blockItems = new ArrayList<BlockTransaction>();
        var otherStateChanges = List.of(
                tokenMapUpdate(true, tokenId1, 1555), // total supply for token1 isn't used
                tokenMapUpdate(false, tokenId2, 500), // total supply after all changes
                tokenMapUpdate(false, tokenId3, 1000));
        var childTransactionOutput = callContractOutput(childPrecompileContractCallResult);
        for (var builder : builders) {
            if (blockItems.isEmpty()) {
                // the parent
                blockItems.add(builder.stateChanges(s -> {
                            var stateChangesBuilder =
                                    s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                            s.add(stateChangesBuilder
                                    .addAllStateChanges(otherStateChanges)
                                    .build());
                        })
                        .build());
            } else {
                blockItems.add(builder
                        // link previous to get parent's state changes
                        .previous(blockItems.getLast())
                        .stateChanges(List::clear)
                        .transactionOutputs(outputs -> outputs.putAll(childTransactionOutput))
                        .build());
            }
        }
        var blockFile = blockFileBuilder.items(blockItems).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expectedItems = List.of(
                contractCallRecordItem,
                token2CreateRecordItem,
                token3CreateRecordItem,
                token2MintRecordItem,
                token2BurnRecordItem,
                token2WipeRecordItem,
                token1DeleteRecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expectedItems);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyElementsOf(expectedParentItems);
        });
    }

    @Test
    void multipleContractCallsInBatch() {
        // given
        // A batch of 3 inner contract call transactions
        // two contracts with storages changes
        // - contract A
        //   - slot 1 RW by the first inner contract call
        //   - slot 1 RW by the third inner contract call
        //   - slot 2 read then deleted by the second contract call
        // - contract B
        //   - slot 1 read by the first inner contract call
        //   - slot 1 read by the second inner contract call
        //   - slot 2 RW by the second inner contract call
        var contractIdA = recordItemBuilder.contractId();
        var contractASlot1 = recordItemBuilder.slot();
        var contractASlot1Values = List.of(
                recordItemBuilder.nonZeroBytes(8),
                recordItemBuilder.nonZeroBytes(10),
                recordItemBuilder.nonZeroBytes(12));
        var contractASlot2 = recordItemBuilder.slot();
        var contractASlot2Value = recordItemBuilder.nonZeroBytes(12);
        var contractIdB = recordItemBuilder.contractId();
        var contractBSlot1 = recordItemBuilder.slot();
        var contractBSlot1Value = recordItemBuilder.nonZeroBytes(16);
        var contractBSlot2 = recordItemBuilder.slot();
        var contractBSlot2Values = List.of(recordItemBuilder.nonZeroBytes(10), recordItemBuilder.nonZeroBytes(16));
        // statechanges only have the final values
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdA, contractASlot1, contractASlot1Values.getLast()))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Values.getLast()))
                .build();
        var atomicBatchRecordItem =
                recordItemBuilder.atomicBatch().customize(this::finalize).build();
        var atomicBatchBlockTransaction = blockTransactionBuilder
                .atomicBatch(atomicBatchRecordItem)
                .stateChanges(s -> s.add(stateChanges))
                .build();
        var parentConsensusTimestamp =
                atomicBatchRecordItem.getTransactionRecord().getConsensusTimestamp();

        // contract call 1
        var contractCall1ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot1)
                                .setValueRead(contractASlot1Values.get(0))
                                .setValueWritten(BytesValue.of(contractASlot1Values.get(1)))))
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdB)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot1)
                                .setValueRead(contractBSlot1Value)))
                .build();
        var contractCallRecordItem1 = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records -> customizeSidecarRecords(
                        records, true, b -> b.setStateChanges(contractCall1ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var innerBlockTransaction1 = blockTransactionBuilder
                .contractCall(contractCallRecordItem1, true)
                .previous(atomicBatchBlockTransaction)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder()
                                    .addKeys(contractASlot1)
                                    .build())
                            .addSlotReads(
                                    SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot1Values.getFirst()))
                            .build();
                    var slotUsage2 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdB)
                            .addSlotReads(
                                    SlotRead.newBuilder().setKey(contractBSlot1).setReadValue(contractBSlot1Value))
                            .build();
                    b.addContractSlotUsages(slotUsage1).addContractSlotUsages(slotUsage2);
                }))
                .build();

        // contract call 2
        var contractCall2ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot2)
                                .setValueRead(contractASlot2Value)
                                .setValueWritten(BytesValue.getDefaultInstance())))
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdB)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot1)
                                .setValueRead(contractBSlot1Value))
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot2)
                                .setValueRead(contractBSlot2Values.getFirst())
                                .setValueWritten(BytesValue.of(contractBSlot2Values.getLast()))))
                .build();
        var contractCallRecordItem2 = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records -> customizeSidecarRecords(
                        records, true, b -> b.setStateChanges(contractCall2ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(2))
                .customize(this::finalize)
                .build();
        var innerBlockTransaction2 = blockTransactionBuilder
                .contractCall(contractCallRecordItem2, true)
                .previous(innerBlockTransaction1)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractASlot2))
                            .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot2Value))
                            .build();
                    var slotUsage2 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdB)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractBSlot2))
                            .addSlotReads(
                                    SlotRead.newBuilder().setKey(contractBSlot1).setReadValue(contractBSlot1Value))
                            .addSlotReads(
                                    SlotRead.newBuilder().setIndex(0).setReadValue(contractBSlot2Values.getFirst()))
                            .build();
                    b.addContractSlotUsages(slotUsage1).addContractSlotUsages(slotUsage2);
                }))
                .build();

        // contract call 3
        var contractCall3ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot1)
                                .setValueRead(contractASlot1Values.get(1))
                                .setValueWritten(BytesValue.of(contractASlot1Values.getLast()))))
                .build();
        var contractCallRecordItem3 = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records -> customizeSidecarRecords(
                        records, true, b -> b.setStateChanges(contractCall3ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var innerBlockTransaction3 = blockTransactionBuilder
                .contractCall(contractCallRecordItem3, true)
                .previous(innerBlockTransaction2)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractASlot1))
                            .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot1Values.get(1)))
                            .build();
                    b.addContractSlotUsages(slotUsage1);
                }))
                .build();

        // set inner next
        innerBlockTransaction1.setNextInBatch(innerBlockTransaction2);
        innerBlockTransaction2.setNextInBatch(innerBlockTransaction3);

        var blockFile = blockFileBuilder
                .items(List.of(
                        atomicBatchBlockTransaction,
                        innerBlockTransaction1,
                        innerBlockTransaction2,
                        innerBlockTransaction3))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(
                atomicBatchRecordItem, contractCallRecordItem1, contractCallRecordItem2, contractCallRecordItem3);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyElementsOf(expectedParentItems);
        });
    }

    @Test
    void multipleHookExecutionChildTransactions() {
        // given
        // A parent transaction (e.g., CryptoTransfer) with 3 hook execution children
        // two contracts with storage changes
        // - contract A
        //   - slot 1 RW by the first hook execution
        //   - slot 1 RW by the third hook execution
        //   - slot 2 read then deleted by the second hook execution
        // - contract B
        //   - slot 1 read by the first hook execution
        //   - slot 1 read by the second hook execution
        //   - slot 2 RW by the second hook execution
        var contractIdA = recordItemBuilder.contractId();
        var contractASlot1 = recordItemBuilder.slot();
        var contractASlot1Values = List.of(
                recordItemBuilder.nonZeroBytes(8),
                recordItemBuilder.nonZeroBytes(10),
                recordItemBuilder.nonZeroBytes(12));
        var contractASlot2 = recordItemBuilder.slot();
        var contractASlot2Value = recordItemBuilder.nonZeroBytes(12);
        var contractIdB = recordItemBuilder.contractId();
        var contractBSlot1 = recordItemBuilder.slot();
        var contractBSlot1Value = recordItemBuilder.nonZeroBytes(16);
        var contractBSlot2 = recordItemBuilder.slot();
        var contractBSlot2Values = List.of(recordItemBuilder.nonZeroBytes(10), recordItemBuilder.nonZeroBytes(16));

        // statechanges only have the final values
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdA, contractASlot1, contractASlot1Values.getLast()))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Values.getLast()))
                .build();

        // Parent transaction (CryptoTransfer that triggers hooks)
        var parentRecordItem =
                recordItemBuilder.cryptoTransfer().customize(this::finalize).build();
        var parentBlockTransaction = blockTransactionBuilder
                .cryptoTransfer(parentRecordItem)
                .stateChanges(s -> s.add(stateChanges))
                .build();
        var parentConsensusTimestamp = parentRecordItem.getTransactionRecord().getConsensusTimestamp();

        // Hook execution 1
        var hookExec1ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot1)
                                .setValueRead(contractASlot1Values.get(0))
                                .setValueWritten(BytesValue.of(contractASlot1Values.get(1)))))
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdB)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot1)
                                .setValueRead(contractBSlot1Value)))
                .build();
        var hookExec1RecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records ->
                        customizeSidecarRecords(records, true, b -> b.setStateChanges(hookExec1ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var hookExec1BlockTransaction = blockTransactionBuilder
                .contractCall(hookExec1RecordItem, true)
                .previous(parentBlockTransaction)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder()
                                    .addKeys(contractASlot1)
                                    .build())
                            .addSlotReads(
                                    SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot1Values.getFirst()))
                            .build();
                    var slotUsage2 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdB)
                            .addSlotReads(
                                    SlotRead.newBuilder().setKey(contractBSlot1).setReadValue(contractBSlot1Value))
                            .build();
                    b.addContractSlotUsages(slotUsage1).addContractSlotUsages(slotUsage2);
                }))
                .build();

        // Hook execution 2
        var hookExec2ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot2)
                                .setValueRead(contractASlot2Value)
                                .setValueWritten(BytesValue.getDefaultInstance())))
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdB)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot1)
                                .setValueRead(contractBSlot1Value))
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractBSlot2)
                                .setValueRead(contractBSlot2Values.getFirst())
                                .setValueWritten(BytesValue.of(contractBSlot2Values.getLast()))))
                .build();
        var hookExec2RecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records ->
                        customizeSidecarRecords(records, true, b -> b.setStateChanges(hookExec2ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(2))
                .customize(this::finalize)
                .build();
        var hookExec2BlockTransaction = blockTransactionBuilder
                .contractCall(hookExec2RecordItem, true)
                .previous(hookExec1BlockTransaction)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractASlot2))
                            .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot2Value))
                            .build();
                    var slotUsage2 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdB)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractBSlot2))
                            .addSlotReads(
                                    SlotRead.newBuilder().setKey(contractBSlot1).setReadValue(contractBSlot1Value))
                            .addSlotReads(
                                    SlotRead.newBuilder().setIndex(0).setReadValue(contractBSlot2Values.getFirst()))
                            .build();
                    b.addContractSlotUsages(slotUsage1).addContractSlotUsages(slotUsage2);
                }))
                .build();

        // Hook execution 3
        var hookExec3ContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractIdA)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(contractASlot1)
                                .setValueRead(contractASlot1Values.get(1))
                                .setValueWritten(BytesValue.of(contractASlot1Values.getLast()))))
                .build();
        var hookExec3RecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(records ->
                        customizeSidecarRecords(records, true, b -> b.setStateChanges(hookExec3ContractStateChanges)))
                .recordItem(r -> r.transactionIndex(3))
                .customize(this::finalize)
                .build();
        var hookExec3BlockTransaction = blockTransactionBuilder
                .contractCall(hookExec3RecordItem, true)
                .previous(hookExec2BlockTransaction)
                .traceData(traceDataList -> updateEvmTraceData(traceDataList, b -> {
                    var slotUsage1 = ContractSlotUsage.newBuilder()
                            .setContractId(contractIdA)
                            .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(contractASlot1))
                            .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(contractASlot1Values.get(1)))
                            .build();
                    b.addContractSlotUsages(slotUsage1);
                }))
                .build();

        // Link hook execution siblings via nextSibling
        hookExec1BlockTransaction.setNextSibling(hookExec2BlockTransaction);
        hookExec2BlockTransaction.setNextSibling(hookExec3BlockTransaction);

        var blockFile = blockFileBuilder
                .items(List.of(
                        parentBlockTransaction,
                        hookExec1BlockTransaction,
                        hookExec2BlockTransaction,
                        hookExec3BlockTransaction))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(parentRecordItem, hookExec1RecordItem, hookExec2RecordItem, hookExec3RecordItem);
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            var expectedParentItems = ListUtils.union(
                    Collections.nCopies(1, null), Collections.nCopies(items.size() - 1, items.getFirst()));
            assertThat(items).map(RecordItem::getParent).containsExactlyElementsOf(expectedParentItems);
        });
    }

    @ParameterizedTest(name = "executed at expiry: {0}")
    @ValueSource(booleans = {false, true})
    void scheduledContractCallWithChildContractCreate(boolean executedAtExpiry) {
        // given
        var childContractId = recordItemBuilder.contractId();
        var expected = new ArrayList<RecordItem>();
        var blockTransactions = new ArrayList<BlockTransaction>();
        var scheduleSign = addScheduleSign(executedAtExpiry, expected, blockTransactions);
        var scheduledContractCall = recordItemBuilder
                .contractCall()
                .customize(b -> scheduled(b, scheduleSign, expected.size()))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(childContractId))
                .customize(this::finalize)
                .build();
        addBlockTransaction(
                blockTransactionBuilder.contractCall(scheduledContractCall, true),
                scheduledContractCall,
                expected,
                blockTransactions);
        var childParent = scheduleSign != null ? scheduleSign : scheduledContractCall;
        // the synthetic child transaction dispatched by the HTS system contract has an EVM transaction result
        var precompileCryptoTransfer = systemContractCryptoTransfer(childParent, 1, expected.size());
        addBlockTransaction(
                systemContractCryptoTransferBlockTransaction(precompileCryptoTransfer),
                precompileCryptoTransfer,
                expected,
                blockTransactions);
        var childContractCreate = childContractCreate(childContractId, childParent, 2, expected.size());
        addBlockTransaction(
                blockTransactionBuilder.contractCreate(childContractCreate),
                childContractCreate,
                expected,
                blockTransactions);
        var blockFile = blockFileBuilder.items(blockTransactions).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, expected));
    }

    @Test
    void hookExecutionWithChildContractCreate() {
        // given
        // in block stream, the parent of both the hook executions and their child transactions is the user transaction
        var childContractId = recordItemBuilder.contractId();
        var expected = new ArrayList<RecordItem>();
        var blockTransactions = new ArrayList<BlockTransaction>();
        var cryptoTransfer =
                recordItemBuilder.cryptoTransfer().customize(this::finalize).build();
        addBlockTransaction(
                blockTransactionBuilder.cryptoTransfer(cryptoTransfer), cryptoTransfer, expected, blockTransactions);
        // the first hook execution creates no contract, the second one creates one after calling system contracts
        var hookExecution1 = childContractCall(cryptoTransfer, 1, expected.size());
        addBlockTransaction(hookExecutionBlockTransaction(hookExecution1), hookExecution1, expected, blockTransactions);
        var hookExecution2 = childContractCall(cryptoTransfer, 2, expected.size(), childContractId);
        addBlockTransaction(hookExecutionBlockTransaction(hookExecution2), hookExecution2, expected, blockTransactions);
        // the synthetic ContractCall externalized for a system contract view call has an EVM transaction result
        var systemContractCall = childContractCall(cryptoTransfer, 3, expected.size());
        addBlockTransaction(
                blockTransactionBuilder.contractCall(systemContractCall, true),
                systemContractCall,
                expected,
                blockTransactions);
        var precompileCryptoTransfer = systemContractCryptoTransfer(cryptoTransfer, 4, expected.size());
        addBlockTransaction(
                systemContractCryptoTransferBlockTransaction(precompileCryptoTransfer),
                precompileCryptoTransfer,
                expected,
                blockTransactions);
        var childContractCreate = childContractCreate(childContractId, cryptoTransfer, 5, expected.size());
        addBlockTransaction(
                blockTransactionBuilder.contractCreate(childContractCreate),
                childContractCreate,
                expected,
                blockTransactions);
        var blockFile = blockFileBuilder.items(blockTransactions).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, expected);
            var parent = items.getFirst();
            assertThat(items).map(RecordItem::getParent).containsExactly(null, parent, parent, parent, parent, parent);
        });
    }

    @ParameterizedTest(name = "executed at expiry: {0}, child contract creates: {1}")
    @CsvSource({"false, 0", "false, 2", "true, 0", "true, 2"})
    void scheduledContractCreate(boolean executedAtExpiry, int childCount) {
        // given
        var contractId = recordItemBuilder.contractId();
        var childContractIds = new ArrayList<ContractID>();
        for (int i = 0; i < childCount; i++) {
            childContractIds.add(recordItemBuilder.contractId());
        }

        var createdContractIds = new ArrayList<ContractID>();
        createdContractIds.add(contractId);
        createdContractIds.addAll(childContractIds);
        var expected = new ArrayList<RecordItem>();
        var blockTransactions = new ArrayList<BlockTransaction>();
        var scheduleSign = addScheduleSign(executedAtExpiry, expected, blockTransactions);
        var scheduledContractCreate = recordItemBuilder
                .contractCreate(contractId)
                .customize(b -> scheduled(b, scheduleSign, expected.size()))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(createdContractIds.toArray(ContractID[]::new)))
                .customize(this::finalize)
                .build();
        addBlockTransaction(
                blockTransactionBuilder.contractCreate(scheduledContractCreate),
                scheduledContractCreate,
                expected,
                blockTransactions);
        var childParent = scheduleSign != null ? scheduleSign : scheduledContractCreate;
        for (int i = 0; i < childCount; i++) {
            var childContractCreate = childContractCreate(childContractIds.get(i), childParent, i + 1, expected.size());
            addBlockTransaction(
                    blockTransactionBuilder.contractCreate(childContractCreate),
                    childContractCreate,
                    expected,
                    blockTransactions);
        }

        var blockFile = blockFileBuilder.items(blockTransactions).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, expected));
    }

    private void addBlockTransaction(
            BlockTransactionBuilder.Builder builder,
            RecordItem recordItem,
            List<RecordItem> recordItems,
            List<BlockTransaction> blockTransactions) {
        var transactionRecord = recordItem.getTransactionRecord();
        if (transactionRecord.hasScheduleRef()) {
            builder.transactionResult(r -> r.setScheduleRef(transactionRecord.getScheduleRef()));
        }

        if (!blockTransactions.isEmpty()) {
            builder.previous(blockTransactions.getLast());
        }

        recordItems.add(recordItem);
        blockTransactions.add(builder.build());
    }

    // Adds a ScheduleSign which triggers the scheduled transaction, unless the scheduled transaction is executed at
    // expiry
    private RecordItem addScheduleSign(
            boolean executedAtExpiry, List<RecordItem> recordItems, List<BlockTransaction> blockTransactions) {
        if (executedAtExpiry) {
            return null;
        }

        var scheduleSign =
                recordItemBuilder.scheduleSign().customize(this::finalize).build();
        addBlockTransaction(
                blockTransactionBuilder.scheduleSign(scheduleSign), scheduleSign, recordItems, blockTransactions);
        return scheduleSign;
    }

    @SafeVarargs
    private void customizeSidecarRecords(
            List<TransactionSidecarRecord.Builder> sidecarRecords,
            boolean topLevel,
            Consumer<TransactionSidecarRecord.Builder>... customizers) {
        Timestamp consensusTimestamp = null;
        var copy = List.copyOf(sidecarRecords);
        sidecarRecords.clear();
        for (var sidecarRecord : copy) {
            if (sidecarRecord.hasActions()) {
                if (topLevel) {
                    sidecarRecords.add(sidecarRecord);
                }
                consensusTimestamp = sidecarRecord.getConsensusTimestamp();
                break;
            }
        }

        for (var customizer : customizers) {
            var builder = TransactionSidecarRecord.newBuilder().setConsensusTimestamp(consensusTimestamp);
            customizer.accept(builder);
            sidecarRecords.add(builder);
        }
    }

    private Map<TransactionCase, TransactionOutput> callContractOutput(ContractFunctionResult contractFunctionResult) {
        var output = TransactionOutput.newBuilder()
                //
                // .setContractCall(CallContractOutput.newBuilder().setContractCallResult(contractFunctionResult))
                .build();
        return Map.of(TransactionCase.CONTRACT_CALL, output);
    }

    private RecordItem childContractCall(
            RecordItem parent, int nonce, int transactionIndex, ContractID... createdContractIds) {
        var transactionId = parent.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(nonce)
                .build();
        return recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(
                        parent.getTransactionRecord().getConsensusTimestamp()))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(createdContractIds(createdContractIds))
                .recordItem(r -> r.transactionIndex(transactionIndex))
                .sidecarRecords(List::clear)
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .customize(this::finalize)
                .build();
    }

    // A contract create externalized by consensus nodes for a contract created by the EVM in the parent transaction
    private RecordItem childContractCreate(ContractID contractId, RecordItem parent, int nonce, int transactionIndex) {
        var transactionId = parent.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(nonce)
                .build();
        return recordItemBuilder
                .contractCreate(contractId)
                .record(r -> r.setParentConsensusTimestamp(
                        parent.getTransactionRecord().getConsensusTimestamp()))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .recordItem(r -> r.transactionIndex(transactionIndex))
                .sidecarRecords(List::clear)
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .customize(this::finalize)
                .build();
    }

    private Consumer<TransactionSidecarRecord.Builder> contractBytecode(
            ContractID contractId, boolean explicitInitcode, ByteString runtimeBytecode, boolean topLevel) {
        return b -> {
            // no initcode for top level contract creates since when initcode source is
            // - in the transaction body, it's not set in the sidecar
            // - in a file, it's loaded later in parser
            var initcode = ByteString.EMPTY;
            if (!topLevel) {
                if (explicitInitcode) {
                    initcode = recordItemBuilder.bytes(1536);
                } else {
                    // runtime bytecode is a subsequence of initcode
                    var deployBytecode = recordItemBuilder.bytes(128);
                    var metadataBytecode = recordItemBuilder.bytes(128);
                    initcode = deployBytecode.concat(runtimeBytecode).concat(metadataBytecode);
                }
            }

            b.setBytecode(ContractBytecode.newBuilder()
                    .setContractId(contractId)
                    .setInitcode(initcode)
                    .setRuntimeBytecode(runtimeBytecode));
        };
    }

    private PendingAirdropRecord fungiblePendingAirdropRecord(
            long amount, AccountID receiver, AccountID sender, TokenID tokenId) {
        return PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver)
                        .setSenderId(sender)
                        .setFungibleTokenType(tokenId))
                .setPendingAirdropValue(PendingAirdropValue.newBuilder().setAmount(amount))
                .build();
    }

    // In block stream, a hook execution is a child ContractCall of the user transaction, with the executed hook id set
    // in its EVM transaction result
    private BlockTransactionBuilder.Builder hookExecutionBlockTransaction(RecordItem hookExecution) {
        var hookId = HookId.newBuilder()
                .setEntityId(HookEntityId.newBuilder().setAccountId(recordItemBuilder.accountId()))
                .setHookId(1L)
                .build();
        return blockTransactionBuilder.contractCall(hookExecution, true).transactionOutputs(outputs -> {
            var callContractOutput = outputs.get(TransactionCase.CONTRACT_CALL).getContractCall();
            var evmTransactionResult =
                    callContractOutput.getEvmTransactionResult().toBuilder().setExecutedHookId(hookId);
            outputs.put(
                    TransactionCase.CONTRACT_CALL,
                    TransactionOutput.newBuilder()
                            .setContractCall(
                                    callContractOutput.toBuilder().setEvmTransactionResult(evmTransactionResult))
                            .build());
        });
    }

    private ContractFunctionResult htsPrecompileContractCallResult() {
        return recordItemBuilder.contractFunctionResult(HTS_PRECOMPILE_ADDRESS).build();
    }

    // In block stream, a scheduled transaction triggered by a ScheduleSign has the ScheduleSign as its parent, so do
    // its child transactions. A scheduled transaction executed at expiry is a top-level transaction, and the
    // transaction ids of its child transactions have the scheduled flag set.
    private void scheduled(RecordItemBuilder.Builder<?> builder, RecordItem scheduleSign, int transactionIndex) {
        var transactionId = scheduleSign != null
                ? scheduleSign.getTransactionBody().getTransactionID()
                : TransactionID.newBuilder()
                        .setAccountID(recordItemBuilder.accountId())
                        .setTransactionValidStart(recordItemBuilder.timestamp())
                        .build();
        var scheduleId = recordItemBuilder.scheduleId();
        builder.record(r -> {
                    r.setScheduleRef(scheduleId);
                    if (scheduleSign != null) {
                        r.setParentConsensusTimestamp(
                                scheduleSign.getTransactionRecord().getConsensusTimestamp());
                    }
                })
                .recordItem(r -> r.transactionIndex(transactionIndex))
                .sidecarRecords(List::clear)
                .transactionBodyWrapper(w -> w.setTransactionID(
                        transactionId.toBuilder().setScheduled(true).build()));
    }

    private void simpleContractStateChanges(TransactionSidecarRecord.Builder builder) {
        // contract state changes
        // - contract 1, 3 storage changes, one read-only, one read-write, one deleted
        // - contract 2, 2 storage changes, one read-only, one read-write
        var contractStateChange1 = ContractStateChange.newBuilder()
                .setContractId(recordItemBuilder.contractId())
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(recordItemBuilder.slot())
                        .setValueRead(recordItemBuilder.nonZeroBytes(8)))
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(recordItemBuilder.slot())
                        .setValueRead(recordItemBuilder.nonZeroBytes(8))
                        .setValueWritten(BytesValue.of(recordItemBuilder.nonZeroBytes(4))))
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(recordItemBuilder.slot())
                        .setValueRead(recordItemBuilder.nonZeroBytes(8))
                        .setValueWritten(BytesValue.getDefaultInstance()))
                .build();
        var contractStateChange2 = ContractStateChange.newBuilder()
                .setContractId(recordItemBuilder.contractId())
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(recordItemBuilder.slot())
                        .setValueRead(recordItemBuilder.nonZeroBytes(8)))
                .addStorageChanges(StorageChange.newBuilder()
                        .setSlot(recordItemBuilder.slot())
                        .setValueRead(recordItemBuilder.nonZeroBytes(8))
                        .setValueWritten(BytesValue.of(recordItemBuilder.nonZeroBytes(4))))
                .build();
        var contractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(contractStateChange1)
                .addContractStateChanges(contractStateChange2)
                .build();
        builder.setStateChanges(contractStateChanges);
    }

    private Consumer<List<StateChanges>> stateChangesFromChildContractCreate(
            ContractID contractId, ByteString runtimeBytecode) {
        return stateChangesList -> {
            var stateChanges = stateChangesList.getFirst();
            stateChangesList.clear();

            var accountId = EntityId.of(contractId).toAccountID();
            var updated = stateChanges.toBuilder()
                    .addStateChanges(StateChange.newBuilder()
                            .setStateId(STATE_ID_ACCOUNTS_VALUE)
                            .setMapUpdate(MapUpdateChange.newBuilder()
                                    .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                                    .setValue(MapChangeValue.newBuilder()
                                            .setAccountValue(Account.newBuilder()
                                                    .setAccountId(accountId)
                                                    .setSmartContract(true)))))
                    .addStateChanges(StateChange.newBuilder()
                            .setStateId(STATE_ID_BYTECODE_VALUE)
                            .setMapUpdate(MapUpdateChange.newBuilder()
                                    .setKey(MapChangeKey.newBuilder().setContractIdKey(contractId))
                                    .setValue(MapChangeValue.newBuilder()
                                            .setBytecodeValue(
                                                    Bytecode.newBuilder().setCode(runtimeBytecode)))))
                    .build();
            stateChangesList.add(updated);
        };
    }

    // A synthetic CryptoTransfer dispatched by the HTS system contract, e.g., to transfer tokens. Same as any other
    // transaction dispatched by a system contract, it has an EVM transaction result in block stream
    private RecordItem systemContractCryptoTransfer(RecordItem parent, int nonce, int transactionIndex) {
        var contractCallResult = ContractFunctionResult.newBuilder()
                .setContractCallResult(recordItemBuilder.bytes(32))
                .setContractID(HTS_PRECOMPILE_ADDRESS)
                .setGasUsed(100L)
                .build();
        var transactionId = parent.getTransactionBody().getTransactionID().toBuilder()
                .setNonce(nonce)
                .build();
        return recordItemBuilder
                .cryptoTransfer()
                .receipt(r -> r.setContractID(HTS_PRECOMPILE_ADDRESS))
                .record(r -> r.setContractCallResult(contractCallResult)
                        .setParentConsensusTimestamp(
                                parent.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.transactionIndex(transactionIndex))
                .transactionBodyWrapper(w -> w.setTransactionID(transactionId))
                .customize(this::finalize)
                .build();
    }

    private BlockTransactionBuilder.Builder systemContractCryptoTransferBlockTransaction(RecordItem recordItem) {
        var contractCallResult = recordItem.getTransactionRecord().getContractCallResult();
        var evmTransactionResult = EvmTransactionResult.newBuilder()
                .setContractId(contractCallResult.getContractID())
                .setGasUsed(contractCallResult.getGasUsed())
                .setResultData(contractCallResult.getContractCallResult());
        return blockTransactionBuilder
                .cryptoTransfer(recordItem)
                .transactionOutputs(outputs -> outputs.put(
                        TransactionCase.CONTRACT_CALL,
                        TransactionOutput.newBuilder()
                                .setContractCall(
                                        CallContractOutput.newBuilder().setEvmTransactionResult(evmTransactionResult))
                                .build()));
    }

    private StateChange tokenMapUpdate(boolean deleted, TokenID tokenId, long totalSupply) {
        var token = Token.newBuilder()
                .setDeleted(deleted)
                .setTokenId(tokenId)
                .setTotalSupply(totalSupply)
                .build();
        return StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder().setTokenValue(token)))
                .build();
    }

    private StateChange pendingAirdropMapUpdate(PendingAirdropRecord pendingAirdropRecord) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(pendingAirdropRecord.getPendingAirdropId()))
                        .setValue(MapChangeValue.newBuilder()
                                .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                        .setPendingAirdropValue(pendingAirdropRecord.getPendingAirdropValue()))))
                .build();
    }

    private void updateEvmTraceData(
            List<TraceData> traceDataList, Consumer<EvmTraceData.Builder> evmTraceDataCustomizer) {
        var copy = List.copyOf(traceDataList);
        traceDataList.clear();

        EvmTraceData.Builder builder = EvmTraceData.newBuilder();
        for (var traceData : copy) {
            if (traceData.hasEvmTraceData()) {
                builder = traceData.getEvmTraceData().toBuilder();
                continue;
            }

            traceDataList.add(traceData);
        }

        evmTraceDataCustomizer.accept(builder);
        traceDataList.add(
                TraceData.newBuilder().setEvmTraceData(builder.build()).build());
    }
}
