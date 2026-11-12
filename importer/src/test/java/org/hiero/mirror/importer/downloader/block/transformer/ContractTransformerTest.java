// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ACCOUNTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_BYTECODE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_CLPR_CHANNELS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_STORAGE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapDeleteChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapUpdateChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.makeIdentical;

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
import com.hederahashgraph.api.proto.java.CreatedContractIds;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionReceipt.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.commons.collections4.ListUtils;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
final class ContractTransformerTest extends AbstractTransformerTest {

    // calculated in parser
    private static final Consumer<TransactionRecord.Builder> ETHEREUM_RECORD_CUSTOMIZER =
            TransactionRecord.Builder::clearEthereumHash;

    private static final Consumer<TransactionRecord.Builder> EXPLICIT_CONTRACT_RESULT_CUSTOMIZER =
            b -> contractResultBuilder(b)
                    .ifPresent(builder ->
                            builder.clearAmount().clearFunctionParameters().clearGas());

    @Resource
    private SystemEntity systemEntity;

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

    private static Stream<Arguments> provideContractIds(final boolean create2Resolved) {
        var contractId = recordItemBuilder.contractId();
        var encoded = contractId.toBuilder()
                .setEvmAddress(DomainUtils.fromBytes(DomainUtils.toEvmAddress(contractId)))
                .build();
        var create2EvmAddress = contractId.toBuilder()
                .setEvmAddress(recordItemBuilder.evmAddress().getValue())
                .build();

        // An unresolved non long-zero EVM address is left for the parser to resolve
        return Stream.of(
                Arguments.of("plain", contractId, contractId, true),
                Arguments.of("encoded evm address", encoded, contractId, true),
                Arguments.of("create2 evm address", create2EvmAddress, contractId, create2Resolved));
    }

    // Consensus node clears the alias of a deleted contract, so a create2 EVM address can't be resolved
    private static Stream<Arguments> provideContractIdsForDelete() {
        return provideContractIds(false);
    }

    private static Stream<Arguments> provideContractIdsForUpdate() {
        return provideContractIds(true);
    }

    private static void setReceiptContractId(
            final Builder receipt, final ContractID contractId, final boolean inReceipt) {
        if (inReceipt) {
            receipt.setContractID(contractId);
        } else {
            receipt.clearContractID();
        }
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

    @Test
    void contractCallWithClprSendMessage() {
        // given -- CLPR's sendMessage system contract runs synchronously inside ContractCall
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(recordItemBuilder.bytes(32))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        final var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                clprChannelValue.getChannelId(),
                                clprChannelMapUpdate(clprChannelValue).getMapUpdate()))))
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .contractCall(expectedRecordItem, false)
                .stateChanges(s -> {
                    final var stateChangesBuilder =
                            s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                    s.add(stateChangesBuilder
                            .addStateChanges(clprChannelMapUpdate(clprChannelValue))
                            .build());
                })
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallWithClprSendMessageUnsuccessful() {
        // given -- a failed transaction's StateChanges can't be trusted, so clprContext must stay unattached even
        // though the raw CLPR state change is present on the block transaction
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(recordItemBuilder.bytes(32))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        final var expectedRecordItem = recordItemBuilder
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
        final var blockTransaction = blockTransactionBuilder
                .contractCall(expectedRecordItem, false)
                .stateChanges(s -> {
                    final var stateChangesBuilder =
                            s.isEmpty() ? StateChanges.newBuilder() : s.removeFirst().toBuilder();
                    s.add(stateChangesBuilder
                            .addStateChanges(clprChannelMapUpdate(clprChannelValue))
                            .build());
                })
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallWithChildSystemContractCall() {
        // given
        final var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        final var parentConsensusTimestamp =
                parentRecordItem.getTransactionRecord().getConsensusTimestamp();
        // consensus node doesn't set the contract id in the receipt of a child dispatched by a system contract
        final var childContractCallResult = htsPrecompileContractCallResult();
        final var childRecordItem = recordItemBuilder
                .contractCall(htsSystemContractId())
                .transactionBodyWrapper(b -> b.getTransactionIDBuilder().setNonce(1))
                .receipt(Builder::clearContractID)
                .record(r -> r.setContractCallResult(childContractCallResult)
                        .setParentConsensusTimestamp(parentConsensusTimestamp))
                .sidecarRecords(List::clear)
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        final var parent =
                blockTransactionBuilder.contractCall(parentRecordItem, false).build();
        final var child = blockTransactionBuilder
                .contractCall(childRecordItem, true)
                .previous(parent)
                .stateChanges(List::clear)
                .build();
        final var blockFile = blockFileBuilder.items(List.of(parent, child)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(
                recordFile, blockFile, items -> assertRecordItems(items, List.of(parentRecordItem, childRecordItem)));
    }

    @ParameterizedTest(name = "created contract ids present empty: {0}")
    @ValueSource(booleans = {true, false})
    void contractCallNoCreatedContractIds(final boolean presentEmpty) {
        // given
        final var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.getContractCallResultBuilder().clearCreatedContractIDs())
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .contractCall(expectedRecordItem, false)
                .transactionOutputs(outputs -> {
                    final var output = outputs.get(TransactionCase.CONTRACT_CALL).toBuilder();
                    final var evmTransactionResult =
                            output.getContractCallBuilder().getEvmTransactionResultBuilder();
                    if (presentEmpty) {
                        evmTransactionResult.setCreatedContractIds(CreatedContractIds.getDefaultInstance());
                    } else {
                        evmTransactionResult.clearCreatedContractIds();
                    }
                    outputs.put(TransactionCase.CONTRACT_CALL, output.build());
                })
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallWithNestedScheduledContractCall() {
        // given
        // Contract call X runs scheduled contract call Y through HSS. Both scopes overlap, so each traces the
        // scope-final written values explicitly
        // - slot 1: X writes value 1, Y writes value 2, then X writes value 3
        // - slot 2: Y writes zero, which X reports as well
        // - slot 3: Y writes value 4, then X restores the original value, so there is no state change
        // - slot 4: a full-width key written by X before Y reads it
        final var contractId = recordItemBuilder.contractId();
        final var slot1 = recordItemBuilder.slot();
        final var slot1Values = List.of(
                recordItemBuilder.nonZeroBytes(8),
                recordItemBuilder.nonZeroBytes(8),
                recordItemBuilder.nonZeroBytes(8),
                recordItemBuilder.nonZeroBytes(8));
        final var slot2 = recordItemBuilder.slot();
        final var slot2Value = recordItemBuilder.nonZeroBytes(8);
        final var slot3 = recordItemBuilder.slot();
        final var slot3Values = List.of(recordItemBuilder.nonZeroBytes(8), recordItemBuilder.nonZeroBytes(8));
        final var slot4 = recordItemBuilder.nonZeroBytes(32);
        final var slot4Values = List.of(recordItemBuilder.nonZeroBytes(8), recordItemBuilder.nonZeroBytes(8));

        // state changes have the 32-byte words of the final values
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(contractStorageMapUpdateChange(contractId, slot1, leftPad32(slot1Values.getLast())))
                .addStateChanges(contractStorageMapDeleteChange(contractId, slot2))
                .addStateChanges(contractStorageMapUpdateChange(contractId, slot4, leftPad32(slot4Values.getLast())))
                .build();

        final var outerContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractId)
                        .addStorageChanges(storageChange(slot1, slot1Values.getFirst(), slot1Values.getLast()))
                        .addStorageChanges(storageChange(slot2, slot2Value, ByteString.EMPTY))
                        .addStorageChanges(storageChange(slot3, slot3Values.getFirst(), slot3Values.getFirst()))
                        .addStorageChanges(storageChange(slot4, slot4Values.getFirst(), slot4Values.getLast())))
                .build();
        final var outerRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records ->
                        customizeSidecarRecords(records, true, b -> b.setStateChanges(outerContractStateChanges)))
                .customize(this::finalize)
                .build();
        final var outer = blockTransactionBuilder
                .contractCall(outerRecordItem, true)
                .stateChanges(s -> s.add(stateChanges))
                .traceData(traceDataList -> updateEvmTraceData(
                        traceDataList,
                        b -> b.addContractSlotUsages(ContractSlotUsage.newBuilder()
                                .setContractId(contractId)
                                .setWrittenSlotKeys(WrittenSlotKeys.newBuilder()
                                        .addKeys(slot1)
                                        .addKeys(slot2)
                                        .addKeys(slot3)
                                        .addKeys(slot4))
                                .addSlotReads(writtenSlotRead(0, slot1Values.getFirst(), slot1Values.getLast()))
                                .addSlotReads(writtenSlotRead(1, slot2Value, ByteString.EMPTY))
                                .addSlotReads(writtenSlotRead(2, slot3Values.getFirst(), slot3Values.getFirst()))
                                .addSlotReads(writtenSlotRead(3, slot4Values.getFirst(), slot4Values.getLast())))))
                .build();

        final var innerContractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractId)
                        .addStorageChanges(storageChange(slot1, slot1Values.get(1), slot1Values.get(2)))
                        .addStorageChanges(storageChange(slot2, slot2Value, ByteString.EMPTY))
                        .addStorageChanges(storageChange(slot3, slot3Values.getFirst(), slot3Values.getLast()))
                        .addStorageChanges(
                                StorageChange.newBuilder().setSlot(slot4).setValueRead(slot4Values.getLast())))
                .build();
        // consensus node sets the contract id in the receipt of a scheduled contract call even with a nonzero nonce
        final var innerRecordItem = recordItemBuilder
                .contractCall()
                .transactionBodyWrapper(b -> b.getTransactionIDBuilder().setNonce(1))
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(
                                outerRecordItem.getTransactionRecord().getConsensusTimestamp())
                        .setScheduleRef(recordItemBuilder.scheduleId()))
                .sidecarRecords(records ->
                        customizeSidecarRecords(records, true, b -> b.setStateChanges(innerContractStateChanges)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        final var inner = blockTransactionBuilder
                .contractCall(innerRecordItem, true)
                .previous(outer)
                .stateChanges(List::clear)
                .traceData(traceDataList -> updateEvmTraceData(
                        traceDataList,
                        b -> b.addContractSlotUsages(ContractSlotUsage.newBuilder()
                                .setContractId(contractId)
                                .setWrittenSlotKeys(WrittenSlotKeys.newBuilder()
                                        .addKeys(slot1)
                                        .addKeys(slot2)
                                        .addKeys(slot3))
                                .addSlotReads(writtenSlotRead(0, slot1Values.get(1), slot1Values.get(2)))
                                .addSlotReads(writtenSlotRead(1, slot2Value, ByteString.EMPTY))
                                .addSlotReads(writtenSlotRead(2, slot3Values.getFirst(), slot3Values.getLast()))
                                .addSlotReads(
                                        SlotRead.newBuilder().setKey(slot4).setReadValue(slot4Values.getLast())))))
                .build();
        final var blockFile = blockFileBuilder.items(List.of(outer, inner)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(
                recordFile, blockFile, items -> assertRecordItems(items, List.of(outerRecordItem, innerRecordItem)));
    }

    @Test
    void scheduledContractCallTriggeredByScheduleSign() {
        // given
        // The scheduled contract call is the only EVM transaction in the unit, so its written slots are indexed into
        // the non-identical storage changes of the unit
        final var contractId = recordItemBuilder.contractId();
        final var readOnlySlot = recordItemBuilder.slot();
        final var readOnlyValue = recordItemBuilder.nonZeroBytes(8);
        final var updatedSlot = recordItemBuilder.slot();
        final var updatedValues = List.of(recordItemBuilder.nonZeroBytes(8), recordItemBuilder.nonZeroBytes(8));
        final var deletedSlot = recordItemBuilder.slot();
        final var deletedValue = recordItemBuilder.nonZeroBytes(8);
        // a pointer-only update of the read-only slot is identical, so it's not in the implicit list
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(makeIdentical(contractStorageMapUpdateChange(contractId, readOnlySlot, readOnlyValue)))
                .addStateChanges(contractStorageMapUpdateChange(contractId, updatedSlot, updatedValues.getLast()))
                .addStateChanges(contractStorageMapDeleteChange(contractId, deletedSlot))
                .build();
        final var scheduleSignRecordItem =
                recordItemBuilder.scheduleSign().customize(this::finalize).build();
        final var scheduleSign = blockTransactionBuilder
                .scheduleSign(scheduleSignRecordItem)
                .stateChanges(s -> s.add(stateChanges))
                .build();

        final var contractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractId)
                        .addStorageChanges(
                                StorageChange.newBuilder().setSlot(readOnlySlot).setValueRead(readOnlyValue))
                        .addStorageChanges(
                                storageChange(updatedSlot, updatedValues.getFirst(), updatedValues.getLast()))
                        .addStorageChanges(storageChange(deletedSlot, deletedValue, ByteString.EMPTY)))
                .build();
        final var scheduledRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(
                                scheduleSignRecordItem.getTransactionRecord().getConsensusTimestamp())
                        .setScheduleRef(recordItemBuilder.scheduleId()))
                .sidecarRecords(
                        records -> customizeSidecarRecords(records, true, b -> b.setStateChanges(contractStateChanges)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        final var scheduled = blockTransactionBuilder
                .contractCall(scheduledRecordItem, true)
                .previous(scheduleSign)
                .stateChanges(List::clear)
                .traceData(traceDataList -> updateEvmTraceData(
                        traceDataList,
                        b -> b.addContractSlotUsages(ContractSlotUsage.newBuilder()
                                .setContractId(contractId)
                                .addSlotReads(SlotRead.newBuilder()
                                        .setKey(readOnlySlot)
                                        .setReadValue(readOnlyValue))
                                .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(updatedValues.getFirst()))
                                .addSlotReads(SlotRead.newBuilder().setIndex(1).setReadValue(deletedValue)))))
                .build();
        final var blockFile =
                blockFileBuilder.items(List.of(scheduleSign, scheduled)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(
                recordFile,
                blockFile,
                items -> assertRecordItems(items, List.of(scheduleSignRecordItem, scheduledRecordItem)));
    }

    @Test
    void contractCallReadOnlySlotKeyWithLeadingZeros() {
        // given
        final var contractId = recordItemBuilder.contractId();
        final var slot = recordItemBuilder.nonZeroBytes(4);
        final var valueRead = recordItemBuilder.nonZeroBytes(8);
        final var contractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractId)
                        .addStorageChanges(
                                StorageChange.newBuilder().setSlot(slot).setValueRead(valueRead)))
                .build();
        final var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(
                        records -> customizeSidecarRecords(records, true, b -> b.setStateChanges(contractStateChanges)))
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .contractCall(expectedRecordItem, true)
                .traceData(traceDataList -> updateEvmTraceData(
                        traceDataList,
                        b -> b.addContractSlotUsages(ContractSlotUsage.newBuilder()
                                .setContractId(contractId)
                                .addSlotReads(SlotRead.newBuilder()
                                        .setKey(leftPad32(slot))
                                        .setReadValue(leftPad32(valueRead))))))
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void contractCallStorageValueWrittenAbsent() {
        // given
        final var valueRead = recordItemBuilder.nonZeroBytes(8);
        final var contractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(recordItemBuilder.contractId())
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(recordItemBuilder.slot())
                                .setValueRead(valueRead)
                                .setValueWritten(BytesValue.of(valueRead))))
                .build();
        final var expectedRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(
                        records -> customizeSidecarRecords(records, true, b -> b.setStateChanges(contractStateChanges)))
                .customize(this::finalize)
                .build();
        final var blockTransaction = blockTransactionBuilder
                .contractCall(expectedRecordItem, false)
                .stateChanges(ContractTransformerTest::omitStorageValuesWritten)
                .build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

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

    @MethodSource("provideContractIdsForDelete")
    @ParameterizedTest(name = "delete contract with {0} contract id")
    void contractDelete(String type, ContractID contractIdInBody, ContractID contractId, boolean inReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractDelete()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> setReceiptContractId(r, contractId, inReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem, contractId)
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

    @MethodSource("provideContractIdsForUpdate")
    @ParameterizedTest(name = "update contract with {0} contract id")
    void contractUpdate(String type, ContractID contractIdInBody, ContractID contractId, boolean inReceipt) {
        // given
        var expectedRecordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.setContractID(contractIdInBody))
                .receipt(r -> setReceiptContractId(r, contractId, inReceipt))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .contractDeleteOrUpdate(expectedRecordItem, contractId)
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ethereumNoUsedGas(boolean create) {
        // given
        var expectedRecordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(ETHEREUM_RECORD_CUSTOMIZER)
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
        var contractCallRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
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
                        // cancelled afterward, there is no state changes to tell the exact amount, so the record has
                        // the amount to transfer in the body and the parser accumulates it onto the existing amount
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(1200, receiver1, sender, tokenId1))
                        .addNewPendingAirdrops(fungiblePendingAirdropRecord(900, receiver1, sender, tokenId2)))
                .recordItem(r -> r.transactionIndex(1)
                        .pendingAirdropAmountAbsent(Set.of(PendingAirdropId.newBuilder()
                                .setReceiverId(receiver1)
                                .setSenderId(sender)
                                .setFungibleTokenType(tokenId1)
                                .build())))
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

    @Test
    void contractCallWithHTSPrecompileAndTrackTotalSupply() {
        // given
        var tokenId1 = recordItemBuilder.tokenId(); // a token created before contract call
        var contractCallRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .sidecarRecords(records -> customizeSidecarRecords(records, true, this::simpleContractStateChanges))
                .customize(this::finalize)
                .build();
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
        // CLPR's sendMessage system contract could run inside any of the inner contract calls; the resulting state
        // change still lands on the batch parent's shared StateChangeContext, same as the contract storage changes
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(recordItemBuilder.bytes(32))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        // statechanges only have the final values
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdA, contractASlot1, contractASlot1Values.getLast()))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Values.getLast()))
                .addStateChanges(clprChannelMapUpdate(clprChannelValue))
                .build();
        final var atomicBatchRecordItem = recordItemBuilder
                .atomicBatch()
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                clprChannelValue.getChannelId(),
                                clprChannelMapUpdate(clprChannelValue).getMapUpdate()))))
                .customize(this::finalize)
                .build();
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
    void contractCallInBatchStorageValueWrittenAbsent() {
        // given
        final var contractId = recordItemBuilder.contractId();
        final var slot = recordItemBuilder.slot();
        final var valueRead = recordItemBuilder.nonZeroBytes(8);
        // The slot is written without a net change in the batch, so its partial state change omits the value
        final var storageChange = contractStorageMapUpdateChange(contractId, slot, valueRead).toBuilder();
        storageChange
                .getMapUpdateBuilder()
                .setPartial(true)
                .getValueBuilder()
                .getSlotValueValueBuilder()
                .clearValue();
        final var stateChanges =
                StateChanges.newBuilder().addStateChanges(storageChange).build();
        final var atomicBatchRecordItem =
                recordItemBuilder.atomicBatch().customize(this::finalize).build();
        final var atomicBatchBlockTransaction = blockTransactionBuilder
                .atomicBatch(atomicBatchRecordItem)
                .stateChanges(s -> s.add(stateChanges))
                .build();
        final var contractStateChanges = ContractStateChanges.newBuilder()
                .addContractStateChanges(ContractStateChange.newBuilder()
                        .setContractId(contractId)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(slot)
                                .setValueRead(valueRead)
                                .setValueWritten(BytesValue.of(valueRead))))
                .build();
        final var contractCallRecordItem = recordItemBuilder
                .contractCall()
                .record(EXPLICIT_CONTRACT_RESULT_CUSTOMIZER)
                .record(r -> r.setParentConsensusTimestamp(
                        atomicBatchRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .sidecarRecords(
                        records -> customizeSidecarRecords(records, true, b -> b.setStateChanges(contractStateChanges)))
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        final var innerBlockTransaction = blockTransactionBuilder
                .contractCall(contractCallRecordItem, true)
                .previous(atomicBatchBlockTransaction)
                .traceData(traceDataList -> updateEvmTraceData(
                        traceDataList,
                        b -> b.addContractSlotUsages(ContractSlotUsage.newBuilder()
                                .setContractId(contractId)
                                .setWrittenSlotKeys(WrittenSlotKeys.newBuilder().addKeys(slot))
                                .addSlotReads(SlotRead.newBuilder().setIndex(0).setReadValue(valueRead)))))
                .build();
        final var blockFile = blockFileBuilder
                .items(List.of(atomicBatchBlockTransaction, innerBlockTransaction))
                .build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(
                recordFile,
                blockFile,
                items -> assertRecordItems(items, List.of(atomicBatchRecordItem, contractCallRecordItem)));
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

        // CLPR's sendMessage system contract could run inside any hook execution; the resulting state change still
        // lands on the parent's shared StateChangeContext, same as the contract storage changes
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(recordItemBuilder.bytes(32))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        // statechanges only have the final values
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdA, contractASlot1, contractASlot1Values.getLast()))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(
                        contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Values.getLast()))
                .addStateChanges(clprChannelMapUpdate(clprChannelValue))
                .build();

        // Parent transaction (CryptoTransfer that triggers hooks)
        final var parentRecordItem = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new ClprChannelEntry(
                                clprChannelValue.getChannelId(),
                                clprChannelMapUpdate(clprChannelValue).getMapUpdate()))))
                .customize(this::finalize)
                .build();
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
                .setContractCall(CallContractOutput.newBuilder()
                        .setEvmTransactionResult(BlockTransactionBuilder.fromContractResult(contractFunctionResult)))
                .build();
        return Map.of(TransactionCase.CONTRACT_CALL, output);
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

    private ContractID htsSystemContractId() {
        return systemEntity.hederaTokenServiceContract().toContractID();
    }

    private ContractFunctionResult htsPrecompileContractCallResult() {
        // only the fields externalized in EvmTransactionResult, the rest is not reconstructable from the block stream
        return recordItemBuilder
                .contractFunctionResult(htsSystemContractId())
                .clearAmount()
                .clearBloom()
                .clearCreatedContractIDs()
                .clearFunctionParameters()
                .clearGas()
                .clearLogInfo()
                .build();
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

    private static ByteString leftPad32(final ByteString value) {
        return DomainUtils.fromBytes(DomainUtils.leftPadBytes(DomainUtils.toBytes(value), 32));
    }

    private static StorageChange storageChange(
            final ByteString slot, final ByteString valueRead, final ByteString valueWritten) {
        return StorageChange.newBuilder()
                .setSlot(slot)
                .setValueRead(valueRead)
                .setValueWritten(BytesValue.of(valueWritten))
                .build();
    }

    private static SlotRead writtenSlotRead(
            final int index, final ByteString valueRead, final ByteString valueWritten) {
        return SlotRead.newBuilder()
                .setIndex(index)
                .setReadValue(valueRead)
                .setWrittenValue(BytesValue.of(valueWritten))
                .build();
    }

    private StateChange clprChannelMapUpdate(com.hederahashgraph.api.proto.java.ClprChannel value) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_CLPR_CHANNELS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setProtoBytesKey(BytesValue.of(value.getChannelId())))
                        .setValue(MapChangeValue.newBuilder().setClprChannelValue(value)))
                .build();
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

    // Make every storage update partial without the value, as if each slot is written without a net change
    private static void omitStorageValuesWritten(final List<StateChanges> stateChangesList) {
        final var updated = new ArrayList<StateChanges>();
        for (final var stateChanges : stateChangesList) {
            final var builder = stateChanges.toBuilder();
            for (final var stateChange : builder.getStateChangesBuilderList()) {
                if (stateChange.getStateId() == STATE_ID_STORAGE_VALUE
                        && stateChange.hasMapUpdate()
                        && !stateChange.getMapUpdate().getIdentical()) {
                    stateChange
                            .getMapUpdateBuilder()
                            .setPartial(true)
                            .getValueBuilder()
                            .getSlotValueValueBuilder()
                            .clearValue();
                }
            }
            updated.add(builder.build());
        }

        stateChangesList.clear();
        stateChangesList.addAll(updated);
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
