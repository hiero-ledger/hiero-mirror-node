// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.domain;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ACCOUNTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_CONTRACT_BYTECODE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_CONTRACT_STORAGE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NFTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NODES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_SCHEDULES_BY_ID_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.ACCOUNT_CREATE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.CONTRACT_CALL;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.CONTRACT_CREATE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.CREATE_SCHEDULE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.ETHEREUM_CALL;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.SIGN_SCHEDULE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.UTIL_PRNG;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.UInt64Value;
import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateAccountOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.EthereumOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapDeleteChange;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.output.protoc.UtilPrngOutput;
import com.hedera.hapi.block.stream.trace.protoc.ContractSlotUsage;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.EvmTransactionLog;
import com.hedera.hapi.block.stream.trace.protoc.ExecutedInitcode;
import com.hedera.hapi.block.stream.trace.protoc.InitcodeBookends;
import com.hedera.hapi.block.stream.trace.protoc.SlotRead;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import com.hederahashgraph.api.proto.java.InternalCallContext;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Schedule;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.SlotValue;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Topic;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.util.CollectionUtils;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BlockTransactionBuilder {

    private final RecordItemBuilder recordItemBuilder;

    private static StateChanges buildFileIdStateChanges(RecordItem recordItem) {
        var fileId = recordItem.getTransactionRecord().getReceipt().getFileID();
        var key = MapChangeKey.newBuilder().setFileIdKey(fileId).build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();

        var firstChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .setStateId(1)
                .build();

        var secondChange = StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_FILES_VALUE)
                .build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_FILES_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourthChange = StateChange.newBuilder()
                .setMapUpdate(mapUpdate)
                .setStateId(StateIdentifier.STATE_ID_FILES_VALUE)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourthChange);

        return StateChanges.newBuilder().addAllStateChanges(changes).build();
    }

    private static StateChanges buildNodeIdStateChanges(RecordItem recordItem) {
        var nodeId = recordItem.getTransactionRecord().getReceipt().getNodeId();
        var key = MapChangeKey.newBuilder()
                .setEntityNumberKey(UInt64Value.of(nodeId))
                .build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();

        var firstChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .setStateId(1)
                .build();

        var secondChange =
                StateChange.newBuilder().setStateId(STATE_ID_NODES_VALUE).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_ID_NODES_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourthChange = StateChange.newBuilder()
                .setMapUpdate(mapUpdate)
                .setStateId(STATE_ID_NODES_VALUE)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourthChange);
        return StateChanges.newBuilder().addAllStateChanges(changes).build();
    }

    public BlockTransactionBuilder.Builder atomicBatch(RecordItem recordItem) {
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder contractCall(RecordItem recordItem, boolean skipStorageChange) {
        var contractCallResult = recordItem.getTransactionRecord().getContractCallResult();
        var evmTransactionResult = fromContractResult(contractCallResult);
        var transactionOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.newBuilder().setEvmTransactionResult(evmTransactionResult))
                .build();
        if (!contractCallResult.hasContractID()) {
            return new BlockTransactionBuilder.Builder(
                    recordItem.getTransaction(),
                    transactionResult(recordItem),
                    Map.of(CONTRACT_CALL, transactionOutput),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        var contractId = contractCallResult.getContractID();
        var accountId = toAccountId(contractId);
        var stateChangesBuilder = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_ACCOUNTS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setAccountValue(Account.newBuilder()
                                                .setAccountId(accountId)
                                                .setSmartContract(true)))));
        var evmTraceDataBuilder =
                new EvmTraceDataBuilder(contractCallResult.getLogInfoList(), recordItem.getSidecarRecords()).partial();
        if (!skipStorageChange) {
            convertContractStateChanges(evmTraceDataBuilder, recordItem.getSidecarRecords(), stateChangesBuilder);
        }
        var traceData =
                TraceData.newBuilder().setEvmTraceData(evmTraceDataBuilder).build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(CONTRACT_CALL, transactionOutput),
                List.of(stateChangesBuilder.build()),
                List.of(traceData));
    }

    public BlockTransactionBuilder.Builder contractCreate(RecordItem recordItem) {
        var contractCreateResult = recordItem.getTransactionRecord().getContractCreateResult();
        var createContract = CreateContractOutput.newBuilder();
        if (!ContractFunctionResult.getDefaultInstance().equals(contractCreateResult)) {
            var evmTransactionResult = fromContractResult(contractCreateResult);
            createContract.setEvmTransactionResult(evmTransactionResult);
        }
        var transactionOutput =
                TransactionOutput.newBuilder().setContractCreate(createContract).build();
        if (!contractCreateResult.hasContractID()) {
            return new BlockTransactionBuilder.Builder(
                    recordItem.getTransaction(),
                    transactionResult(recordItem),
                    Map.of(CONTRACT_CREATE, transactionOutput),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        var contractId = contractCreateResult.getContractID();
        var accountId = toAccountId(contractId);
        var evmAddress = contractCreateResult.hasEvmAddress()
                ? contractCreateResult.getEvmAddress().getValue()
                : ByteString.EMPTY;
        var stateChangesBuilder = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_ACCOUNTS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setAccountValue(Account.newBuilder()
                                                .setAccountId(accountId)
                                                .setAlias(evmAddress)
                                                .setSmartContract(true)))));
        var evmTraceDataBuilder = new EvmTraceDataBuilder(
                        contractCreateResult.getLogInfoList(), recordItem.getSidecarRecords())
                .partial();
        convertContractBytecode(
                evmTraceDataBuilder, recordItem.getSidecarRecords(), stateChangesBuilder, recordItem.isTopLevel());
        convertContractStateChanges(evmTraceDataBuilder, recordItem.getSidecarRecords(), stateChangesBuilder);
        var traceData =
                TraceData.newBuilder().setEvmTraceData(evmTraceDataBuilder).build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(CONTRACT_CREATE, transactionOutput),
                List.of(stateChangesBuilder.build()),
                List.of(traceData));
    }

    public BlockTransactionBuilder.Builder contractDeleteOrUpdate(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return unsuccessfulTransaction(recordItem);
        }

        var contractId = recordItem.getTransactionRecord().getReceipt().getContractID();
        ContractID contractIdInBody;
        boolean deleted = false;
        if (recordItem.getTransactionType() == TransactionType.CONTRACTDELETEINSTANCE.getProtoId()) {
            contractIdInBody =
                    recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
            deleted = true;
        } else {
            contractIdInBody =
                    recordItem.getTransactionBody().getContractUpdateInstance().getContractID();
        }

        var accountId = toAccountId(contractId);
        var evmAddress = getEvmAddress(contractIdInBody);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_ACCOUNTS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setAccountValue(Account.newBuilder()
                                                .setAccountId(accountId)
                                                .setAlias(evmAddress)
                                                .setDeleted(deleted)
                                                .setSmartContract(true)))))
                .build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder ethereum(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var ethereumOutput = EthereumOutput.newBuilder();
        var traceDataList = new ArrayList<TraceData>();
        var stateChangesList = new ArrayList<StateChanges>();

        if (transactionRecord.hasContractCallResult() || transactionRecord.hasContractCreateResult()) {
            var contractResult = transactionRecord.hasContractCallResult()
                    ? transactionRecord.getContractCallResult()
                    : transactionRecord.getContractCreateResult();
            var contractId = contractResult.getContractID();

            // TransactionOutput. Note InternalCallContext is populated since the fields not explicit in the transaction
            // body
            var evmTransactionResult = fromContractResult(contractResult).toBuilder()
                    .setInternalCallContext(InternalCallContext.newBuilder()
                            .setGas(contractResult.getGas())
                            .setValue(contractResult.getAmount())
                            .setCallData(contractResult.getFunctionParameters()))
                    .build();
            Function<EvmTransactionResult, EthereumOutput.Builder> setter = transactionRecord.hasContractCallResult()
                    ? ethereumOutput::setEvmCallTransactionResult
                    : ethereumOutput::setEvmCreateTransactionResult;
            setter.apply(evmTransactionResult);

            // TraceData
            var evmTraceDataBuilder =
                    new EvmTraceDataBuilder(contractResult.getLogInfoList(), recordItem.getSidecarRecords()).partial();
            var stateChangesBuilder = StateChanges.newBuilder();
            convertContractStateChanges(evmTraceDataBuilder, recordItem.getSidecarRecords(), stateChangesBuilder);
            traceDataList.add(
                    TraceData.newBuilder().setEvmTraceData(evmTraceDataBuilder).build());

            // StateChanges
            var accountId = toAccountId(contractId);
            var senderId = contractResult.getSenderId();
            var stateChanges = stateChangesBuilder
                    .addStateChanges(StateChange.newBuilder()
                            .setStateId(STATE_ID_ACCOUNTS_VALUE)
                            .setMapUpdate(MapUpdateChange.newBuilder()
                                    .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                                    .setValue(MapChangeValue.newBuilder()
                                            .setAccountValue(Account.newBuilder()
                                                    .setAccountId(accountId)
                                                    .setSmartContract(true)))))
                    .addStateChanges(StateChange.newBuilder()
                            .setStateId(STATE_ID_ACCOUNTS_VALUE)
                            .setMapUpdate(MapUpdateChange.newBuilder()
                                    .setKey(MapChangeKey.newBuilder().setAccountIdKey(senderId))
                                    .setValue(MapChangeValue.newBuilder()
                                            .setAccountValue(Account.newBuilder()
                                                    .setAccountId(senderId)
                                                    .setEthereumNonce(contractResult
                                                            .getSignerNonce()
                                                            .getValue())))))
                    .build();
            stateChangesList.add(stateChanges);
        }

        var transactionOutput =
                TransactionOutput.newBuilder().setEthereumCall(ethereumOutput).build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(ETHEREUM_CALL, transactionOutput),
                stateChangesList,
                traceDataList);
    }

    public BlockTransactionBuilder.Builder cryptoTransfer() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        return cryptoTransfer(recordItem);
    }

    public BlockTransactionBuilder.Builder cryptoTransfer(RecordItem recordItem) {
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder scheduleCreate(RecordItem recordItem) {
        if (!recordItem.isSuccessful()
                && recordItem.getTransactionStatus()
                        != ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED.getNumber()) {
            return unsuccessfulTransaction(recordItem);
        }

        var receipt = recordItem.getTransactionRecord().getReceipt();
        var scheduleId = receipt.getScheduleID();
        var createSchedule = CreateScheduleOutput.newBuilder().setScheduleId(receipt.getScheduleID());
        if (receipt.hasScheduledTransactionID()) {
            createSchedule.setScheduledTransactionId(receipt.getScheduledTransactionID());
        }
        var transactionOutput =
                TransactionOutput.newBuilder().setCreateSchedule(createSchedule).build();

        var stateChange = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChanges =
                StateChanges.newBuilder().addStateChanges(stateChange).build();

        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(CREATE_SCHEDULE, transactionOutput),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder scheduleDelete(RecordItem recordItem) {
        var scheduleId = recordItem.getTransactionBody().getScheduleDelete().getScheduleID();
        var stateChangeDelete = StateChange.newBuilder()
                .setMapDelete(MapDeleteChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChangeUpdate = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setValue(MapChangeValue.newBuilder()
                                .setScheduleValue(Schedule.newBuilder()
                                        .setScheduleId(scheduleId)
                                        .build())
                                .build()))
                .build();

        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(stateChangeDelete)
                .addStateChanges(stateChangeUpdate)
                .build();

        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder scheduleSign(RecordItem recordItem) {
        var receipt = recordItem.getTransactionRecord().getReceipt();
        var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
        if (receipt.hasScheduledTransactionID()) {
            transactionOutputs.put(
                    SIGN_SCHEDULE,
                    TransactionOutput.newBuilder()
                            .setSignSchedule(SignScheduleOutput.newBuilder()
                                    .setScheduledTransactionId(receipt.getScheduledTransactionID()))
                            .build());
        }
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                transactionOutputs,
                Collections.emptyList(),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder utilPrng(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var utilPrngOutputBuilder = UtilPrngOutput.newBuilder();
        if (transactionRecord.hasPrngNumber()) {
            utilPrngOutputBuilder.setPrngNumber(transactionRecord.getPrngNumber());
        } else if (transactionRecord.hasPrngBytes()) {
            utilPrngOutputBuilder.setPrngBytes(transactionRecord.getPrngBytes());
        }
        var transactionOutput = TransactionOutput.newBuilder()
                .setUtilPrng(utilPrngOutputBuilder)
                .build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(UTIL_PRNG, transactionOutput),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder nodeCreate(RecordItem recordItem) {
        var stateChanges = buildNodeIdStateChanges(recordItem);
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder unknown() {
        var recordItem = recordItemBuilder.unknown().build();
        return unknown(recordItem);
    }

    public BlockTransactionBuilder.Builder unknown(RecordItem recordItem) {
        return defaultBlockItem(recordItem);
    }

    public BlockTransactionBuilder.Builder defaultBlockItem(RecordItem recordItem) {
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public BlockTransactionBuilder.Builder cryptoCreate(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var accountId = transactionRecord.getReceipt().getAccountID();
        var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
        if (recordItem.isSuccessful()) {
            transactionOutputs.put(
                    ACCOUNT_CREATE,
                    TransactionOutput.newBuilder()
                            .setAccountCreate(CreateAccountOutput.newBuilder()
                                    .setCreatedAccountId(accountId)
                                    .build())
                            .build());
        }

        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                transactionOutputs,
                Collections.emptyList(),
                Collections.emptyList());
    }

    public Builder fileCreate(RecordItem recordItem) {
        var stateChanges = buildFileIdStateChanges(recordItem);

        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public Builder consensusCreateTopic(RecordItem recordItem) {
        var topicId = recordItem.getTransactionRecord().getReceipt().getTopicID();
        var key = MapChangeKey.newBuilder().setTopicIdKey(topicId).build();
        var value = MapChangeValue.newBuilder()
                .setTopicValue(Topic.newBuilder().setTopicId(topicId))
                .build();
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(StateIdentifier.STATE_ID_TOPICS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder().setKey(key).setValue(value)))
                .build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Map.of(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public Builder consensusSubmitMessage(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return new BlockTransactionBuilder.Builder(
                    recordItem.getTransaction(),
                    transactionResult(recordItem),
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        var transactionOutputs = new EnumMap<TransactionCase, TransactionOutput>(TransactionCase.class);
        var topicRunningHash = recordItem.getTransactionRecord().getReceipt().getTopicRunningHash();
        var sequenceNumber = recordItem.getTransactionRecord().getReceipt().getTopicSequenceNumber();
        var topicId =
                recordItem.getTransactionBody().getConsensusSubmitMessage().getTopicID();
        var topicValue = Topic.newBuilder()
                .setRunningHash(topicRunningHash)
                .setSequenceNumber(sequenceNumber)
                .setTopicId(topicId)
                .build();
        var key = MapChangeKey.newBuilder().setTopicIdKey(topicId).build();
        var value = MapChangeValue.newBuilder().setTopicValue(topicValue).build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).setValue(value).build();
        var change = StateChange.newBuilder()
                .setStateId(StateIdentifier.STATE_ID_TOPICS_VALUE)
                .setMapUpdate(mapUpdate)
                .build();
        var stateChanges = StateChanges.newBuilder().addStateChanges(change).build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                transactionOutputs,
                List.of(stateChanges),
                Collections.emptyList());
    }

    public Builder tokenAirdrop(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var pendingAirdrops = transactionRecord.getNewPendingAirdropsList();
        List<StateChange> changes = new ArrayList<>();
        for (var pendingAirdrop : pendingAirdrops) {
            var accountPendingAirdrop =
                    AccountPendingAirdrop.newBuilder().setPendingAirdropValue(pendingAirdrop.getPendingAirdropValue());
            changes.add(StateChange.newBuilder()
                    .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                    .setMapUpdate(MapUpdateChange.newBuilder()
                            .setKey(MapChangeKey.newBuilder()
                                    .setPendingAirdropIdKey(pendingAirdrop.getPendingAirdropId()))
                            .setValue(MapChangeValue.newBuilder()
                                    .setAccountPendingAirdropValue(accountPendingAirdrop)
                                    .build())
                            .build())
                    .build());
        }

        // Add state changes that are not reflected in the possible state changes from the transaction body
        changes.add(StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(PendingAirdropId.newBuilder()
                                        .setFungibleTokenType(recordItemBuilder.tokenId())
                                        .setReceiverId(recordItemBuilder.accountId())
                                        .setSenderId(recordItemBuilder.accountId())
                                        .build()))
                        .setValue(MapChangeValue.newBuilder()
                                .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                        .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                                                .setAmount(1)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        changes.add(StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(PendingAirdropId.newBuilder()
                                        .setNonFungibleToken(NftID.newBuilder()
                                                .setTokenID(recordItemBuilder.tokenId())
                                                .setSerialNumber(5000)
                                                .build())
                                        .setReceiverId(recordItemBuilder.accountId())
                                        .setSenderId(recordItemBuilder.accountId())
                                        .build()))
                        .build())
                .build());

        var stateChanges = StateChanges.newBuilder().addAllStateChanges(changes).build();

        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                List.of(stateChanges),
                Collections.emptyList());
    }

    public Builder tokenBurn(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return unsuccessfulTransaction(recordItem);
        }

        return tokenSupplyStateChanges(
                recordItem, recordItem.getTransactionBody().getTokenBurn().getToken());
    }

    public Builder tokenCreate(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return unsuccessfulTransaction(recordItem);
        }

        var receipt = recordItem.getTransactionRecord().getReceipt();
        var tokenId = receipt.getTokenID();
        var stateChange = StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder()
                                .setTokenValue(Token.newBuilder()
                                        .setTokenId(tokenId)
                                        .setTotalSupply(receipt.getNewTotalSupply())))
                        .build())
                .build();
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                List.of(StateChanges.newBuilder().addStateChanges(stateChange).build()),
                Collections.emptyList());
    }

    public Builder tokenMint(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return unsuccessfulTransaction(recordItem);
        }

        return tokenSupplyStateChanges(
                recordItem, recordItem.getTransactionBody().getTokenMint().getToken());
    }

    public Builder tokenWipe(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return unsuccessfulTransaction(recordItem);
        }

        return tokenSupplyStateChanges(
                recordItem, recordItem.getTransactionBody().getTokenWipe().getToken());
    }

    public Builder unsuccessfulTransaction(RecordItem recordItem) {
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    private void convertContractBytecode(
            EvmTraceData.Builder evmTraceDataBuilder,
            List<TransactionSidecarRecord> sidecarRecords,
            StateChanges.Builder stateChangesBuilder,
            boolean topLevel) {
        findFirstSidecarRecord(
                        sidecarRecords, TransactionSidecarRecord::hasBytecode, TransactionSidecarRecord::getBytecode)
                .ifPresent(bytecode -> {
                    stateChangesBuilder.addStateChanges(StateChange.newBuilder()
                            .setStateId(STATE_ID_CONTRACT_BYTECODE_VALUE)
                            .setMapUpdate(MapUpdateChange.newBuilder()
                                    .setKey(MapChangeKey.newBuilder().setContractIdKey(bytecode.getContractId()))
                                    .setValue(MapChangeValue.newBuilder()
                                            .setBytecodeValue(
                                                    Bytecode.newBuilder().setCode(bytecode.getRuntimeBytecode())))));
                    if (!topLevel) {
                        var initcode = Hex.encodeHexString(DomainUtils.toBytes(bytecode.getInitcode()));
                        var runtimeBytecode = Hex.encodeHexString(DomainUtils.toBytes(bytecode.getRuntimeBytecode()));

                        var executedInitcode = ExecutedInitcode.newBuilder();
                        int index = initcode.indexOf(runtimeBytecode);
                        if (index == -1) {
                            executedInitcode.setExplicitInitcode(bytecode.getInitcode());
                        } else {
                            executedInitcode.setInitcodeBookends(InitcodeBookends.newBuilder()
                                    .setDeployBytecode(ByteString.fromHex(initcode.substring(0, index)))
                                    .setMetadataBytecode(
                                            ByteString.fromHex(initcode.substring(index + runtimeBytecode.length()))));
                        }

                        evmTraceDataBuilder.setExecutedInitcode(executedInitcode);
                    }
                });
    }

    private void convertContractStateChanges(
            EvmTraceData.Builder evmTraceDataBuilder,
            List<TransactionSidecarRecord> sidecarRecords,
            StateChanges.Builder stateChangesBuilder) {
        var contractStateChanges = findFirstSidecarRecord(
                        sidecarRecords,
                        TransactionSidecarRecord::hasStateChanges,
                        TransactionSidecarRecord::getStateChanges)
                .map(ContractStateChanges::getContractStateChangesList)
                .orElse(null);
        if (CollectionUtils.isEmpty(contractStateChanges)) {
            return;
        }

        // add an identical MapUpdateChange
        stateChangesBuilder.addStateChanges(StateChange.newBuilder()
                .setStateId(STATE_ID_CONTRACT_STORAGE_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setIdentical(true)
                        .setKey(MapChangeKey.newBuilder()
                                .setSlotKeyKey(SlotKey.newBuilder()
                                        .setContractID(recordItemBuilder.contractId())
                                        .setKey(recordItemBuilder.slot())))
                        .setValue(MapChangeValue.newBuilder()
                                .setSlotValueValue(SlotValue.newBuilder().setValue(recordItemBuilder.bytes(8))))));
        var contractStorageSlotCounts = new HashMap<ContractID, Integer>();
        for (var contractStateChange : contractStateChanges) {
            var contractId = contractStateChange.getContractId();
            var slotUsage = ContractSlotUsage.newBuilder().setContractId(contractId);

            for (var storageChange : contractStateChange.getStorageChangesList()) {
                if (!storageChange.hasValueWritten()) {
                    // only read
                    slotUsage.addSlotReads(SlotRead.newBuilder()
                            .setKey(storageChange.getSlot())
                            .setReadValue(storageChange.getValueRead())
                            .build());
                } else {
                    int count = contractStorageSlotCounts.compute(contractId, (k, v) -> v == null ? 1 : v + 1);
                    slotUsage.addSlotReads(SlotRead.newBuilder()
                            .setIndex(count - 1)
                            .setReadValue(storageChange.getValueRead())
                            .build());
                    var valueWritten = storageChange.getValueWritten();
                    var mapChangeKey = MapChangeKey.newBuilder()
                            .setSlotKeyKey(SlotKey.newBuilder()
                                    .setContractID(contractId)
                                    .setKey(storageChange.getSlot())
                                    .build())
                            .build();
                    if (!BytesValue.getDefaultInstance().equals(storageChange.getValueWritten())) {
                        stateChangesBuilder.addStateChanges(StateChange.newBuilder()
                                .setStateId(STATE_ID_CONTRACT_STORAGE_VALUE)
                                .setMapUpdate(MapUpdateChange.newBuilder()
                                        .setKey(mapChangeKey)
                                        .setValue(MapChangeValue.newBuilder()
                                                .setSlotValueValue(
                                                        SlotValue.newBuilder().setValue(valueWritten.getValue())))
                                        .build()));
                    } else {
                        // deleted
                        stateChangesBuilder.addStateChanges(StateChange.newBuilder()
                                .setStateId(STATE_ID_CONTRACT_STORAGE_VALUE)
                                .setMapDelete(MapDeleteChange.newBuilder().setKey(mapChangeKey)));
                    }
                }
            }

            evmTraceDataBuilder.addContractSlotUsages(slotUsage.build());
        }
    }

    private <T> Optional<T> findFirstSidecarRecord(
            List<TransactionSidecarRecord> sidecarRecords,
            Predicate<TransactionSidecarRecord> predicate,
            Function<TransactionSidecarRecord, T> getter) {
        return sidecarRecords.stream().filter(predicate).findFirst().map(getter);
    }

    private List<StateChange> getSerialNumbersStateChanges(List<Long> serialNumbers, TokenID tokenId) {
        return serialNumbers.stream()
                .map(serialNumber -> MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setNftIdKey(NftID.newBuilder()
                                        .setTokenID(tokenId)
                                        .setSerialNumber(serialNumber)
                                        .build()))
                        .build())
                .map(mapUpdate -> StateChange.newBuilder()
                        .setStateId(STATE_ID_NFTS_VALUE)
                        .setMapUpdate(mapUpdate)
                        .build())
                .toList();
    }

    private StateChange getNewSupplyState(TokenID tokenId, long newTotalSupply) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder()
                                .setTokenValue(
                                        Token.newBuilder().setTokenId(tokenId).setTotalSupply(newTotalSupply)))
                        .build())
                .build();
    }

    private ByteString getEvmAddress(ContractID contractId) {
        if (contractId.hasEvmAddress()) {
            var entityId = DomainUtils.fromEvmAddress(DomainUtils.toBytes(contractId.getEvmAddress()));
            if (entityId == null || entityId.getShard() == contractId.getShardNum()) {
                return contractId.getEvmAddress();
            }
        }

        return ByteString.EMPTY;
    }

    private Timestamp timestamp(long consensusTimestamp) {
        var instant = Instant.ofEpochSecond(0, consensusTimestamp);
        return Utility.instantToTimestamp(instant);
    }

    private AccountID toAccountId(ContractID contractId) {
        return AccountID.newBuilder()
                .setShardNum(contractId.getShardNum())
                .setRealmNum(contractId.getRealmNum())
                .setAccountNum(contractId.getContractNum())
                .build();
    }

    private Builder tokenSupplyStateChanges(RecordItem recordItem, TokenID tokenId) {
        var receipt = recordItem.getTransactionRecord().getReceipt();
        var stateChangesBuilder =
                StateChanges.newBuilder().addStateChanges(getNewSupplyState(tokenId, receipt.getNewTotalSupply()));
        stateChangesBuilder.addAllStateChanges(getSerialNumbersStateChanges(receipt.getSerialNumbersList(), tokenId));
        return new BlockTransactionBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyMap(),
                List.of(stateChangesBuilder.build()),
                Collections.emptyList());
    }

    private TransactionResult transactionResult(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var timestamp = timestamp(recordItem.getConsensusTimestamp());
        return transactionResult(transactionRecord, timestamp).build();
    }

    private TransactionResult.Builder transactionResult(
            TransactionRecord transactionRecord, Timestamp consensusTimestamp) {
        var builder = TransactionResult.newBuilder();
        if (transactionRecord.hasParentConsensusTimestamp()) {
            builder.setParentConsensusTimestamp(transactionRecord.getParentConsensusTimestamp());
        }
        if (transactionRecord.hasScheduleRef()) {
            builder.setScheduleRef(transactionRecord.getScheduleRef());
        }

        return builder.addAllPaidStakingRewards(transactionRecord.getPaidStakingRewardsList())
                .addAllAutomaticTokenAssociations(transactionRecord.getAutomaticTokenAssociationsList())
                .addAllTokenTransferLists(transactionRecord.getTokenTransferListsList())
                .addAllAssessedCustomFees(transactionRecord.getAssessedCustomFeesList())
                .setConsensusTimestamp(consensusTimestamp)
                .setTransferList(transactionRecord.getTransferList())
                .setTransactionFeeCharged(transactionRecord.getTransactionFee())
                .setStatus(transactionRecord.getReceipt().getStatus());
    }

    private static EvmTransactionResult fromContractResult(ContractFunctionResult contractResult) {
        var builder = EvmTransactionResult.newBuilder()
                .setErrorMessage(contractResult.getErrorMessage())
                .setGasUsed(contractResult.getGasUsed())
                .setResultData(contractResult.getContractCallResult());

        if (contractResult.hasContractID()) {
            builder.setContractId(contractResult.getContractID());
        }

        if (contractResult.hasSenderId()) {
            builder.setSenderId(contractResult.getSenderId());
        }

        return builder.build();
    }

    public static class Builder {
        private final SignedTransaction signedTransaction;
        private final byte[] signedTransactionBytes;
        private final Map<TransactionCase, TransactionOutput> transactionOutputs;
        private final TransactionResult.Builder transactionResultBuilder;
        private final List<StateChanges> stateChanges;
        private final List<TraceData> traceDataList;
        private BlockTransaction previous;

        @SneakyThrows
        @SuppressWarnings({"java:S1640", "deprecation"})
        private Builder(
                Transaction transaction,
                TransactionResult transactionResult,
                @Nonnull Map<TransactionCase, TransactionOutput> transactionOutputs,
                @Nonnull List<StateChanges> stateChanges,
                @Nonnull List<TraceData> traceDataList) {
            if (transaction.hasSigMap()) {
                // legacy format
                this.signedTransaction = SignedTransaction.newBuilder()
                        .setBodyBytes(transaction.getBodyBytes())
                        .setSigMap(transaction.getSigMap())
                        .setUseSerializedTxMessageHashAlgorithm(true)
                        .build();
                this.signedTransactionBytes = signedTransaction.toByteArray();
            } else {
                var signedTransactionBytes = transaction.getSignedTransactionBytes();
                this.signedTransaction = SignedTransaction.parseFrom(signedTransactionBytes);
                this.signedTransactionBytes = DomainUtils.toBytes(signedTransactionBytes);
            }

            this.stateChanges = new ArrayList<>(stateChanges); // make it modifiable
            this.traceDataList = new ArrayList<>(traceDataList); // make it modifiable
            this.transactionOutputs = new HashMap<>(transactionOutputs); // make it modifiable
            this.transactionResultBuilder = transactionResult.toBuilder();
        }

        @SneakyThrows
        public BlockTransaction build() {
            return BlockTransaction.builder()
                    .previous(previous)
                    .signedTransaction(signedTransaction)
                    .signedTransactionBytes(signedTransactionBytes)
                    .stateChanges(stateChanges)
                    .traceData(traceDataList)
                    .transactionBody(TransactionBody.parseFrom(signedTransaction.getBodyBytes()))
                    .transactionResult(transactionResultBuilder.build())
                    .transactionOutputs(transactionOutputs)
                    .build();
        }

        public Builder traceData(Consumer<List<TraceData>> consumer) {
            consumer.accept(traceDataList);
            return this;
        }

        public Builder previous(BlockTransaction previous) {
            this.previous = previous;
            return this;
        }

        public Builder stateChanges(Consumer<List<StateChanges>> consumer) {
            consumer.accept(stateChanges);
            return this;
        }

        public Builder transactionOutputs(Consumer<Map<TransactionCase, TransactionOutput>> consumer) {
            consumer.accept(transactionOutputs);
            return this;
        }

        public Builder transactionResult(Consumer<TransactionResult.Builder> consumer) {
            consumer.accept(transactionResultBuilder);
            return this;
        }
    }

    private record EvmTraceDataBuilder(
            List<ContractLoginfo> contractLoginfos, List<TransactionSidecarRecord> transactionSidecarRecords) {

        EvmTraceData.Builder partial() {
            var builder = EvmTraceData.newBuilder();
            addContractActions(builder);
            addLogs(builder);
            return builder;
        }

        void addContractActions(EvmTraceData.Builder builder) {
            for (var sidecarRecord : transactionSidecarRecords) {
                if (sidecarRecord.hasActions()) {
                    builder.addAllContractActions(sidecarRecord.getActions().getContractActionsList());
                }
            }
        }

        void addLogs(EvmTraceData.Builder builder) {
            contractLoginfos.forEach(log -> builder.addLogs(EvmTransactionLog.newBuilder()
                    .setContractId(log.getContractID())
                    .setData(log.getData())
                    .addAllTopics(log.getTopicList())
                    .build()));
        }
    }
}
