// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.hiero.mirror.importer.downloader.block.transformer.Utils.bloomFor;
import static org.hiero.mirror.importer.downloader.block.transformer.Utils.bloomForAll;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
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

    protected void transformSmartContractResult(BlockTransactionTransformation transformation) {
        var blockTransaction = transformation.blockTransaction();
        var evmTransactionInfo = getEvmTransactionInfo(blockTransaction);
        if (evmTransactionInfo == null) {
            return;
        }

        var evmTransactionResult = evmTransactionInfo.evmTransactionResult();
        var transactionRecordBuilder = transformation.recordItemBuilder().transactionRecordBuilder();
        var builder = evmTransactionInfo.isContractCreate()
                ? transactionRecordBuilder.getContractCreateResultBuilder()
                : transactionRecordBuilder.getContractCallResultBuilder();

        builder.setContractCallResult(evmTransactionResult.getResultData())
                .setErrorMessage(evmTransactionResult.getErrorMessage())
                .setGasUsed(evmTransactionResult.getGasUsed());

        if (evmTransactionResult.hasContractId()) {
            builder.setContractID(evmTransactionResult.getContractId());
            transactionRecordBuilder.getReceiptBuilder().setContractID(evmTransactionResult.getContractId());
        }

        if (evmTransactionResult.hasSenderId()) {
            builder.setSenderId(evmTransactionResult.getSenderId());
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
                    .ifPresent(evmAddress -> builder.setEvmAddress(BytesValue.of(evmAddress)));
        }

        if (evmTransactionResult.hasInternalCallContext()) {
            var internalCallContext = evmTransactionResult.getInternalCallContext();
            builder.setAmount(internalCallContext.getValue())
                    .setFunctionParameters(internalCallContext.getCallData())
                    .setGas(internalCallContext.getGas());
        }

        var evmTraceData = blockTransaction.getEvmTraceData();
        transformEvmTransactionLogs(builder, evmTraceData);
        transformSidecarRecords(transformation.recordItemBuilder(), evmTraceData);
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

    private void transformEvmTransactionLogs(
            ContractFunctionResult.Builder contractResultBuilder, EvmTraceData evmTraceData) {
        if (evmTraceData == null || evmTraceData.getLogsList().isEmpty()) {
            return;
        }

        var bloomFilters = new ArrayList<LogsBloomFilter>(evmTraceData.getLogsCount());
        for (var evmTransactionLog : evmTraceData.getLogsList()) {
            var bloomFilter = bloomFor(evmTransactionLog);
            bloomFilters.add(bloomFilter);
            var logInfo = ContractLoginfo.newBuilder()
                    .setBloom(DomainUtils.fromBytes(bloomFilter.toArray()))
                    .setContractID(evmTransactionLog.getContractId())
                    .setData(evmTransactionLog.getData())
                    .addAllTopic(evmTransactionLog.getTopicsList())
                    .build();
            ;
            contractResultBuilder.addLogInfo(logInfo);
        }

        contractResultBuilder.setBloom(
                DomainUtils.fromBytes(bloomForAll(bloomFilters).toArray()));
    }

    private TransactionSidecarRecord transformContractActions(
            Timestamp consensusTimestamp, List<ContractAction> contractActions) {
        if (contractActions.isEmpty()) {
            return null;
        }

        return TransactionSidecarRecord.newBuilder()
                .setConsensusTimestamp(consensusTimestamp)
                .setActions(ContractActions.newBuilder()
                        .addAllContractActions(contractActions)
                        .build())
                .build();
    }

    private void transformSidecarRecords(RecordItem.RecordItemBuilder recordItemBuilder, EvmTraceData evmTraceData) {
        if (evmTraceData == null) {
            return;
        }

        var consensusTimestamp = recordItemBuilder.transactionRecordBuilder().getConsensusTimestamp();
        var sidecarRecords = new ArrayList<TransactionSidecarRecord>();
        var contractActions = transformContractActions(consensusTimestamp, evmTraceData.getContractActionsList());
        if (contractActions != null) {
            sidecarRecords.add(contractActions);
        }

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
