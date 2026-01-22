// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.hiero.mirror.common.util.DomainUtils.normalize;
import static org.hiero.mirror.importer.downloader.block.transformer.Utils.asInitcode;
import static org.hiero.mirror.importer.downloader.block.transformer.Utils.bloomFor;
import static org.hiero.mirror.importer.downloader.block.transformer.Utils.bloomForAll;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.trace.protoc.ContractSlotUsage;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.SlotRead;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.ContractSlotId;
import org.hiero.mirror.common.domain.transaction.ContractSlotKey;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.SlotValue;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractBlockTransactionTransformer implements BlockTransactionTransformer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public void transform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        var transactionBody = blockTransactionTransformation.getTransactionBody();
        var transactionResult = blockTransaction.getTransactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var recordBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .addAllAssessedCustomFees(transactionResult.getAssessedCustomFeesList())
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setMemo(transactionBody.getMemo())
                .setReceipt(receiptBuilder)
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionHash(blockTransaction.getTransactionHash())
                .setTransactionID(transactionBody.getTransactionID())
                .setTransferList(transactionResult.getTransferList());

        if (transactionResult.hasParentConsensusTimestamp()) {
            recordBuilder.setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp());
        }

        if (transactionResult.hasScheduleRef()) {
            recordBuilder.setScheduleRef(transactionResult.getScheduleRef());
        }

        transformSmartContractResult(blockTransactionTransformation);
        doTransform(blockTransactionTransformation);
    }

    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        // do nothing
    }

    protected EvmTransactionInfo getEvmTransactionInfo(BlockTransaction blockTransaction) {
        return blockTransaction
                .getTransactionOutput(TransactionCase.CONTRACT_CALL)
                .map(TransactionOutput::getContractCall)
                .map(callContractOutput -> {
                    if (!callContractOutput.hasEvmTransactionResult()) {
                        log.warn(
                                "CallContractOutput has no EvmTransactionResult at {}",
                                blockTransaction.getConsensusTimestamp());
                        return null;
                    }

                    return EvmTransactionInfo.ofContractCall(callContractOutput.getEvmTransactionResult());
                })
                .orElse(null);
    }

    private SlotValue resolveIndexedSlotValue(
            BlockTransaction blockTransaction, ContractSlotId slotId, int index, List<ByteString> writtenSlotKeys) {
        if (index < 0) {
            return null;
        }

        if (!writtenSlotKeys.isEmpty()) {
            // key is explicitly stored in the writtenSlotKeys list
            if (index >= writtenSlotKeys.size()) {
                return null;
            }
            final var contractSlotKey = normalize(new ContractSlotKey(slotId, writtenSlotKeys.get(index)));
            return new SlotValue(contractSlotKey.key(), blockTransaction.getValueWritten(contractSlotKey));
        } else {
            // implicit, get it from statechanges
            return blockTransaction.getStateChangeContext().getContractStorageChange(slotId, index);
        }
    }

    private void transformEvmTraceData(
            BlockTransaction blockTransaction,
            ContractFunctionResult.Builder contractResultBuilder,
            RecordItem.RecordItemBuilder recordItemBuilder,
            EvmTransactionResult evmTransactionResult) {
        transformEvmTransactionLogs(contractResultBuilder, blockTransaction.getEvmTraceData());
        transformSidecarRecords(
                blockTransaction, contractResultBuilder.getContractID(), recordItemBuilder, evmTransactionResult);
    }

    private void transformSmartContractResult(BlockTransactionTransformation transformation) {
        var blockTransaction = transformation.blockTransaction();
        var evmTransactionInfo = getEvmTransactionInfo(blockTransaction);
        if (evmTransactionInfo == null) {
            return;
        }

        var evmTransactionResult = evmTransactionInfo.evmTransactionResult();
        var transactionRecordBuilder = transformation.recordItemBuilder().transactionRecordBuilder();
        var contractResultbuilder = evmTransactionInfo.isContractCreate()
                ? transactionRecordBuilder.getContractCreateResultBuilder()
                : transactionRecordBuilder.getContractCallResultBuilder();

        contractResultbuilder
                .setContractCallResult(evmTransactionResult.getResultData())
                .setErrorMessage(evmTransactionResult.getErrorMessage())
                .setGasUsed(evmTransactionResult.getGasUsed());

        if (evmTransactionResult.getContractNoncesCount() > 0) {
            contractResultbuilder.addAllContractNonces(evmTransactionResult.getContractNoncesList());
        }

        if (evmTransactionResult.hasContractId()) {
            contractResultbuilder.setContractID(evmTransactionResult.getContractId());
            transactionRecordBuilder.getReceiptBuilder().setContractID(evmTransactionResult.getContractId());
        }

        if (evmTransactionResult.hasSenderId()) {
            contractResultbuilder.setSenderId(evmTransactionResult.getSenderId());
        }

        if (evmTransactionResult.hasSignerNonce()) {
            contractResultbuilder.setSignerNonce(evmTransactionResult.getSignerNonce());
        }

        if (evmTransactionInfo.isContractCreate() && evmTransactionResult.hasContractId()) {
            var contractId = evmTransactionResult.getContractId();
            var accountId = AccountID.newBuilder()
                    .setShardNum(contractId.getShardNum())
                    .setRealmNum(contractId.getRealmNum())
                    .setAccountNum(contractId.getContractNum())
                    .build();
            blockTransaction
                    .getStateChangeContext()
                    .getAccount(accountId)
                    .map(Account::getAlias)
                    .filter(a -> a != ByteString.EMPTY)
                    .ifPresent(evmAddress -> contractResultbuilder.setEvmAddress(BytesValue.of(evmAddress)));
        }

        if (evmTransactionResult.hasInternalCallContext()) {
            var internalCallContext = evmTransactionResult.getInternalCallContext();
            contractResultbuilder
                    .setAmount(internalCallContext.getValue())
                    .setFunctionParameters(internalCallContext.getCallData())
                    .setGas(internalCallContext.getGas());
        }

        transformEvmTraceData(
                blockTransaction, contractResultbuilder, transformation.recordItemBuilder(), evmTransactionResult);
    }

    private void transformEvmTransactionLogs(
            ContractFunctionResult.Builder contractResultBuilder, EvmTraceData evmTraceData) {
        if (evmTraceData == null || evmTraceData.getLogsList().isEmpty()) {
            return;
        }

        var evmTransactionLogs = evmTraceData.getLogsList();
        var bloomFilters = new ArrayList<LogsBloomFilter>(evmTransactionLogs.size());
        for (var evmTransactionLog : evmTransactionLogs) {
            var bloomFilter = bloomFor(evmTransactionLog);
            bloomFilters.add(bloomFilter);
            var logInfo = ContractLoginfo.newBuilder()
                    .setBloom(DomainUtils.fromBytes(bloomFilter.toArray()))
                    .setContractID(evmTransactionLog.getContractId())
                    .setData(evmTransactionLog.getData())
                    .addAllTopic(evmTransactionLog.getTopicsList())
                    .build();
            contractResultBuilder.addLogInfo(logInfo);
        }

        contractResultBuilder.setBloom(
                DomainUtils.fromBytes(bloomForAll(bloomFilters).toArray()));
    }

    private void transformContractActions(
            Timestamp consensusTimestamp, EvmTraceData evmTraceData, List<TransactionSidecarRecord> sidecarRecords) {
        if (evmTraceData == null || evmTraceData.getContractActionsList().isEmpty()) {
            return;
        }

        var contractActionSidecarRecord = TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(consensusTimestamp)
                .setActions(ContractActions.newBuilder()
                        .addAllContractActions(evmTraceData.getContractActionsList())
                        .build())
                .build();
        sidecarRecords.add(contractActionSidecarRecord);
    }

    private void transformContractBytecode(
            BlockTransaction blockTransaction,
            Timestamp consnsusTimestamp,
            ContractID contractId,
            EvmTraceData evmTraceData,
            List<TransactionSidecarRecord> sidecarRecords) {
        if (!blockTransaction.getTransactionBody().hasContractCreateInstance()) {
            return;
        }

        blockTransaction.getStateChangeContext().getContractBytecode(contractId).ifPresent(runtimeBytecode -> {
            var contractBytecode =
                    ContractBytecode.newBuilder().setContractId(contractId).setRuntimeBytecode(runtimeBytecode);
            if (evmTraceData != null && evmTraceData.hasExecutedInitcode()) {
                var executedInitcode = evmTraceData.getExecutedInitcode();
                var initcodeCase = executedInitcode.getInitcodeCase();
                switch (initcodeCase) {
                    case EXPLICIT_INITCODE -> contractBytecode.setInitcode(executedInitcode.getExplicitInitcode());
                    case INITCODE_BOOKENDS ->
                        contractBytecode.setInitcode(
                                asInitcode(executedInitcode.getInitcodeBookends(), runtimeBytecode));
                    default ->
                        Utility.handleRecoverableError(
                                "Unknown initcode case {} at {}",
                                initcodeCase,
                                blockTransaction.getConsensusTimestamp());
                }
            }

            sidecarRecords.add(TransactionSidecarRecord.newBuilder()
                    .setConsensusTimestamp(consnsusTimestamp)
                    .setBytecode(contractBytecode)
                    .build());
        });
    }

    private void transformContractSlotUsage(
            BlockTransaction blockTransaction,
            ContractSlotUsage contractSlotUsage,
            List<ContractStateChange> contractStateChanges,
            Map<ContractSlotKey, ByteString> contractStorageReads,
            EvmTransactionResult evmTransactionResult) {
        var contractId = contractSlotUsage.getContractId();
        var writtenSlotKeys = contractSlotUsage.getWrittenSlotKeys().getKeysList();
        final var slotId = ContractSlotId.of(
                contractId,
                evmTransactionResult != null
                        ? evmTransactionResult.hasExecutedHookId() ? evmTransactionResult.getExecutedHookId() : null
                        : null);

        // Invalid state: hook system contract without executed hook
        if (slotId == null) {
            Utility.handleRecoverableError(
                    "Hook system contract {} storage referenced without executed hook at {}",
                    contractId,
                    blockTransaction.getConsensusTimestamp());
            return;
        }

        var missingIndices = new ArrayList<Integer>();
        boolean missingValueWritten = false;
        var storageChanges = new ArrayList<StorageChange>();

        for (var slotRead : contractSlotUsage.getSlotReadsList()) {
            ByteString slot = null;
            var valueRead = DomainUtils.trim(slotRead.getReadValue());
            var storageChangeBuilder = StorageChange.newBuilder().setValueRead(valueRead);
            if (slotRead.getIdentifierCase() == SlotRead.IdentifierCase.INDEX) {
                int index = slotRead.getIndex();
                var slotValue = resolveIndexedSlotValue(blockTransaction, slotId, index, writtenSlotKeys);
                if (slotValue != null) {
                    slot = slotValue.slot();
                    if (slotValue.valueWritten() != null) {
                        storageChanges.add(storageChangeBuilder
                                .setSlot(slotValue.slot())
                                .setValueWritten(slotValue.valueWritten())
                                .build());
                    } else {
                        missingValueWritten = true;
                    }
                } else {
                    missingIndices.add(index);
                }
            } else if (slotRead.getIdentifierCase() == SlotRead.IdentifierCase.KEY) {
                slot = slotRead.getKey();
                storageChanges.add(storageChangeBuilder.setSlot(slot).build());
            }

            if (slot != null) {
                final var contractSlotKey = new ContractSlotKey(slotId, slot);
                contractStorageReads.put(contractSlotKey, valueRead);
            }
        }

        if (!storageChanges.isEmpty()) {
            contractStateChanges.add(ContractStateChange.newBuilder()
                    .setContractId(contractId)
                    .addAllStorageChanges(storageChanges)
                    .build());
        }

        if (!missingIndices.isEmpty()) {
            Utility.handleRecoverableError(
                    "Unable to resolve the following storage slot indices for contract {} at {}: {}",
                    contractId,
                    blockTransaction.getConsensusTimestamp(),
                    missingIndices);
        }

        if (missingValueWritten) {
            Utility.handleRecoverableError(
                    "Unable to find value written for at least one storage slot for contract {} at {}",
                    contractId,
                    blockTransaction.getConsensusTimestamp());
        }
    }

    private void transformContractStateChanges(
            BlockTransaction blockTransaction,
            EvmTraceData evmTraceData,
            List<TransactionSidecarRecord> sidecarRecords,
            EvmTransactionResult evmTransactionResult) {
        if (evmTraceData == null || evmTraceData.getContractSlotUsagesList().isEmpty()) {
            return;
        }

        var contractStateChanges = new ArrayList<ContractStateChange>();
        // The contract storages read by this transaction. Note some reads may have its key pointed by SlotRead.index,
        // and the map stores the resolved slot key. The constructed map is stored in BlockTransaction and due to
        // the fact the transactions are processed in descending order by consensus timestamp, a preceding transaction
        // can resolve the value it writes to a storage slot by looking for the value read in subsequent transactions
        var contractStorageReads = new HashMap<ContractSlotKey, ByteString>();

        for (var contractSlotUsage : evmTraceData.getContractSlotUsagesList()) {
            transformContractSlotUsage(
                    blockTransaction,
                    contractSlotUsage,
                    contractStateChanges,
                    contractStorageReads,
                    evmTransactionResult);
        }

        if (!contractStateChanges.isEmpty()) {
            sidecarRecords.add(TransactionSidecarRecord.newBuilder()
                    .setConsensusTimestamp(
                            blockTransaction.getTransactionResult().getConsensusTimestamp())
                    .setStateChanges(ContractStateChanges.newBuilder()
                            .addAllContractStateChanges(contractStateChanges)
                            .build())
                    .build());
        }

        blockTransaction.setContractStorageReads(contractStorageReads);
    }

    private void transformSidecarRecords(
            BlockTransaction blockTransaction,
            ContractID contractId,
            RecordItem.RecordItemBuilder recordItemBuilder,
            EvmTransactionResult evmTransactionResult) {
        var consensusTimestamp = blockTransaction.getTransactionResult().getConsensusTimestamp();
        var evmTraceData = blockTransaction.getEvmTraceData();
        var sidecarRecords = new ArrayList<TransactionSidecarRecord>();

        transformContractActions(consensusTimestamp, evmTraceData, sidecarRecords);
        transformContractBytecode(blockTransaction, consensusTimestamp, contractId, evmTraceData, sidecarRecords);
        transformContractStateChanges(blockTransaction, evmTraceData, sidecarRecords, evmTransactionResult);

        recordItemBuilder.sidecarRecords(sidecarRecords);
    }

    protected record EvmTransactionInfo(EvmTransactionResult evmTransactionResult, boolean isContractCreate) {

        static EvmTransactionInfo ofContractCall(EvmTransactionResult evmTransactionResult) {
            return new EvmTransactionInfo(evmTransactionResult, false);
        }

        static EvmTransactionInfo ofContractCreate(EvmTransactionResult transactionResult) {
            return new EvmTransactionInfo(transactionResult, true);
        }
    }
}
