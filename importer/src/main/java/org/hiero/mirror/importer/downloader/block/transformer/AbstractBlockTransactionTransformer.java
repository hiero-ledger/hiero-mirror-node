// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.trace.protoc.ContractSlotUsage;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.InitcodeBookends;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.ContractSlotId;
import org.hiero.mirror.common.domain.transaction.ContractSlotKey;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.SlotValue;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractBlockTransactionTransformer implements BlockTransactionTransformer {

    // consensus nodes sort createdContractIDs by contract num only
    private static final Comparator<ContractID> CONTRACT_NUM_COMPARATOR =
            Comparator.comparingLong(ContractID::getContractNum);

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public void transform(final BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        final var transactionBody = blockTransactionTransformation.getTransactionBody();
        final var transactionResult = blockTransaction.getTransactionResult();
        final var recordItemBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .congestionPricingMultiplier(transactionResult.getCongestionPricingMultiplier());
        final var receiptBuilder = TransactionReceipt.newBuilder().setStatusValue(transactionResult.getStatusValue());
        var recordBuilder = recordItemBuilder
                .transactionRecordBuilder()
                .addAllAssessedCustomFees(transactionResult.getAssessedCustomFeesList())
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setHighVolumePricingMultiplier(transactionResult.getHighVolumePricingMultiplier())
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

    /**
     * Finds the EVM transaction which created the contract in the child ContractCreate transaction. It's the parent if
     * the parent has an EVM transaction result. Otherwise, the parent is the scheduling transaction of a triggered
     * scheduled transaction, or the user transaction of a hook execution, and the creator is the closest preceding
     * such scheduled transaction or hook execution under the parent. Note a synthetic child transaction of any type
     * dispatched by a system contract also has an EVM transaction result, so it alone doesn't identify the creator.
     *
     * @param childContractCreate The child ContractCreate transaction
     * @return The creator, or null if not found
     */
    private BlockTransaction findCreator(final BlockTransaction childContractCreate) {
        final var parent = childContractCreate.getParent();
        if (parent == null || getEvmTransactionInfo(parent) != null) {
            return parent;
        }

        for (var previous = childContractCreate.getPrevious();
                previous != null && previous != parent;
                previous = previous.getPrevious()) {
            final var evmTransactionInfo = getEvmTransactionInfo(previous);
            if (evmTransactionInfo != null
                    && (evmTransactionInfo.evmTransactionResult().hasExecutedHookId()
                            || previous.getTransactionResult().hasScheduleRef())) {
                return previous;
            }
        }

        return null;
    }

    private EvmTransactionInfo getContractCallInfo(final BlockTransaction blockTransaction) {
        final var callContractOutput = blockTransaction
                .getTransactionOutput(TransactionCase.CONTRACT_CALL)
                .map(TransactionOutput::getContractCall)
                .orElse(null);
        if (callContractOutput == null) {
            return null;
        }

        if (!callContractOutput.hasEvmTransactionResult()) {
            log.warn("CallContractOutput has no EvmTransactionResult at {}", blockTransaction.getConsensusTimestamp());
            return null;
        }

        return EvmTransactionInfo.ofContractCall(callContractOutput.getEvmTransactionResult());
    }

    private EvmTransactionInfo getContractCreateInfo(final BlockTransaction blockTransaction) {
        final var createContractOutput = blockTransaction
                .getTransactionOutput(TransactionCase.CONTRACT_CREATE)
                .map(TransactionOutput::getContractCreate)
                .orElse(null);
        if (createContractOutput == null) {
            return null;
        }

        if (!createContractOutput.hasEvmTransactionResult()) {
            log.warn(
                    "CreateContractOutput has no EvmTransactionResult at {}", blockTransaction.getConsensusTimestamp());
            return null;
        }

        return EvmTransactionInfo.ofContractCreate(createContractOutput.getEvmTransactionResult());
    }

    private EvmTransactionInfo getEthereumTransactionInfo(final BlockTransaction blockTransaction) {
        final var ethereumOutput = blockTransaction
                .getTransactionOutput(TransactionCase.ETHEREUM_CALL)
                .map(TransactionOutput::getEthereumCall)
                .orElse(null);
        if (ethereumOutput == null) {
            return null;
        }

        final var transactionResultCase = ethereumOutput.getTransactionResultCase();
        return switch (transactionResultCase) {
            case EVM_CALL_TRANSACTION_RESULT ->
                EvmTransactionInfo.ofContractCall(ethereumOutput.getEvmCallTransactionResult());
            case EVM_CREATE_TRANSACTION_RESULT ->
                EvmTransactionInfo.ofContractCreate(ethereumOutput.getEvmCreateTransactionResult());
            default -> {
                log.warn(
                        "Unhandled transaction result case {} in EthereumOutput at {}",
                        transactionResultCase,
                        blockTransaction.getConsensusTimestamp());
                yield null;
            }
        };
    }

    /**
     * Gets the EVM transaction info from the transaction output of the transaction's type. Note besides a ContractCall,
     * a synthetic child transaction of any other type dispatched by a system contract also has a CallContractOutput.
     *
     * @param blockTransaction The block transaction
     * @return The EVM transaction info, or null if the transaction has no EVM transaction result
     */
    private EvmTransactionInfo getEvmTransactionInfo(final BlockTransaction blockTransaction) {
        return switch (blockTransaction.getTransactionBody().getDataCase()) {
            case CONTRACTCREATEINSTANCE -> getContractCreateInfo(blockTransaction);
            case ETHEREUMTRANSACTION -> getEthereumTransactionInfo(blockTransaction);
            default -> getContractCallInfo(blockTransaction);
        };
    }

    /**
     * Whether the transaction is a ContractCreate dispatched by the EVM for a CREATE / CREATE2 operation or a hollow
     * account finalization. Such a transaction is always a child transaction with a nonzero nonce. Note the scheduled
     * flag of its transaction id can't be relied on, since the child transactions of a scheduled transaction executed
     * at expiry inherit the flag from it. Instead, a triggered scheduled ContractCreate is told apart by its schedule
     * ref, which is only set for the scheduled transaction itself.
     *
     * @param blockTransaction The block transaction
     * @return Whether the transaction is a child ContractCreate
     */
    private static boolean isChildContractCreate(final BlockTransaction blockTransaction) {
        final var transactionBody = blockTransaction.getTransactionBody();
        return transactionBody.hasContractCreateInstance()
                && blockTransaction.getParentConsensusTimestamp() != null
                && transactionBody.getTransactionID().getNonce() > 0
                && !blockTransaction.getTransactionResult().hasScheduleRef();
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

        transformCreatedContractIds(blockTransaction, evmTransactionInfo, contractResultbuilder);
        transformEvmTraceData(
                blockTransaction, contractResultbuilder, transformation.recordItemBuilder(), evmTransactionResult);
    }

    private void transformEvmTransactionLogs(
            ContractFunctionResult.Builder contractResultBuilder, EvmTraceData evmTraceData) {
        if (evmTraceData == null || evmTraceData.getLogsList().isEmpty()) {
            return;
        }

        var evmTransactionLogs = evmTraceData.getLogsList();
        final var logsBloomFilter = new LogsBloomFilter();

        for (final var evmTransactionLog : evmTransactionLogs) {
            final var logsBloomFilterInner = new LogsBloomFilter();
            final var evmAddress = DomainUtils.toEvmAddress(evmTransactionLog.getContractId());
            logsBloomFilterInner.insertAddress(evmAddress);
            evmTransactionLog.getTopicsList().forEach(logsBloomFilterInner::insertTopic);
            logsBloomFilter.or(logsBloomFilterInner);

            var logInfo = ContractLoginfo.newBuilder()
                    .setBloom(logsBloomFilterInner.toByteString())
                    .setContractID(evmTransactionLog.getContractId())
                    .setData(evmTransactionLog.getData())
                    .addAllTopic(evmTransactionLog.getTopicsList())
                    .build();
            contractResultBuilder.addLogInfo(logInfo);
        }

        contractResultBuilder.setBloom(logsBloomFilter.toByteString());
    }

    ByteString asInitcode(InitcodeBookends initcodeBookends, ByteString runtimeBytecode) {
        var initcode = runtimeBytecode;

        if (!initcodeBookends.getDeployBytecode().isEmpty()) {
            initcode = initcodeBookends.getDeployBytecode().concat(initcode);
        }

        if (!initcodeBookends.getMetadataBytecode().isEmpty()) {
            initcode = initcode.concat(initcodeBookends.getMetadataBytecode());
        }

        return initcode;
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
                contractId, evmTransactionResult.hasExecutedHookId() ? evmTransactionResult.getExecutedHookId() : null);

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

    /**
     * Rebuilds the deprecated createdContractIDs, which isn't in block stream. Same as consensus nodes, the list has
     * the contract created by the transaction itself and the contracts created by its child ContractCreate
     * transactions, sorted by contract num, and it's only set for a successful transaction. Since transactions are
     * transformed in descending order by consensus timestamp, a child ContractCreate registers its contract with its
     * creator, and the creator collects the registered contracts when it's transformed later.
     */
    @SuppressWarnings("deprecation")
    private void transformCreatedContractIds(
            final BlockTransaction blockTransaction,
            final EvmTransactionInfo evmTransactionInfo,
            final ContractFunctionResult.Builder contractResultBuilder) {
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var evmTransactionResult = evmTransactionInfo.evmTransactionResult();
        final var contractId = evmTransactionInfo.isContractCreate() && evmTransactionResult.hasContractId()
                ? evmTransactionResult.getContractId()
                : null;
        if (isChildContractCreate(blockTransaction)) {
            // A child ContractCreate has no createdContractIDs of its own in record stream
            final var creator = findCreator(blockTransaction);
            if (creator != null) {
                creator.addCreatedContractId(contractId);
            } else {
                Utility.handleRecoverableError(
                        "Unable to find creator for child contract create at {}",
                        blockTransaction.getConsensusTimestamp());
            }

            return;
        }

        if (blockTransaction.getCreatedContractIds().isEmpty()) {
            if (contractId != null) {
                contractResultBuilder.addCreatedContractIDs(contractId);
            }

            return;
        }

        blockTransaction.addCreatedContractId(contractId);
        final var createdContractIds = new ArrayList<>(blockTransaction.getCreatedContractIds());
        createdContractIds.sort(CONTRACT_NUM_COMPARATOR);
        contractResultBuilder.addAllCreatedContractIDs(createdContractIds);
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

    private record EvmTransactionInfo(EvmTransactionResult evmTransactionResult, boolean isContractCreate) {

        static EvmTransactionInfo ofContractCall(EvmTransactionResult evmTransactionResult) {
            return new EvmTransactionInfo(evmTransactionResult, false);
        }

        static EvmTransactionInfo ofContractCreate(EvmTransactionResult transactionResult) {
            return new EvmTransactionInfo(transactionResult, true);
        }
    }
}
