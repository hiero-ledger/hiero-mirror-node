// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
final class SyntheticContractLogServiceImpl implements SyntheticContractLogService {

    static final byte[] CONTRACT_LOG_MARKER = Bytes.of(1).toArray();
    static final Version HAPI_SYNTHETIC_LOG_VERSION = new Version(0, 71, 0);
    private static final byte[] EMPTY = Bytes.of(0).toArray();

    private final ParserContext parserContext;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public void create(SyntheticContractLog log) {
        if (!entityProperties.getPersist().isSyntheticContractLogs()) {
            return;
        }

        final var recordItem = log.getRecordItem();
        final var contractRelatedParentRecordItem = recordItem.getContractRelatedParent();

        // We will either backfill any EVM-related fungible token transfers that don't have synthetic events produced by
        // CN or create synthetic logs for HAPI-related transfer events
        if (shouldSkipLogCreation(log)) {
            return;
        }

        long consensusTimestamp;
        int logIndex;
        Integer transactionIndex;
        EntityId contractId;
        EntityId rootContractId;
        byte[] transactionHash;

        if (contractRelatedParentRecordItem != null) {
            consensusTimestamp = contractRelatedParentRecordItem.getConsensusTimestamp();
            logIndex = contractRelatedParentRecordItem.getAndIncrementLogIndex();
            transactionIndex = contractRelatedParentRecordItem.getEvmTransactionIndex();
            transactionHash = contractRelatedParentRecordItem.getTransactionHash();

            final var parentTransactionRecord = contractRelatedParentRecordItem.getTransactionRecord();
            contractId = EntityId.tryOf(
                    contractRelatedParentRecordItem.getContractResult().getContractID());
            rootContractId = EntityId.of(parentTransactionRecord.getReceipt().getContractID());
        } else {
            consensusTimestamp = recordItem.getConsensusTimestamp();
            logIndex = recordItem.getAndIncrementLogIndex();
            transactionIndex = recordItem.claimEvmTransactionIndex();
            transactionHash = recordItem.getTransactionHash();
            contractId = log.getEntityId();
            rootContractId = log.getEntityId();
        }

        final var contractLog = new ContractLog();
        contractLog.setBloom(isContract(recordItem) ? CONTRACT_LOG_MARKER : EMPTY);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(contractId);
        contractLog.setData(log.getData() != null ? log.getData() : EMPTY);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(rootContractId);
        contractLog.setPayerAccountId(recordItem.getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(transactionIndex);
        contractLog.setTransactionHash(transactionHash);
        contractLog.setSynthetic(true);

        // The current recordItem should always be set, so that we know which RecordItem/ContractResult bloom to update.
        // This field is set to be only used to calculate bloom aggregation for the RecordItem/ContractResult.
        if (contractRelatedParentRecordItem != null) {
            final var contractResult =
                    parserContext.get(ContractResult.class, contractRelatedParentRecordItem.getConsensusTimestamp());
            contractLog.setContractResult(contractResult);
        }

        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }

    private boolean shouldSkipLogCreation(SyntheticContractLog syntheticLog) {
        final var contractOrigin = isContract(syntheticLog.getRecordItem());
        if (contractOrigin && !(syntheticLog instanceof TransferContractLog)) {
            // Only TransferContractLog synthetic log creation is supported for an operation with contract origin
            return true;
        }

        final var recordItem = syntheticLog.getRecordItem();

        final var tokenTransfersCount = recordItem.getTransactionRecord().getTokenTransferListsCount();
        if (tokenTransfersCount > 2 && !entityProperties.getPersist().isSyntheticContractLogsMulti()) {
            // We have a multi-party fungible transfer scenario and synthetic event creation for
            // such transfers is disabled. We should skip this case no matter if the log is from HAPI or contract
            // origin.
            return true;
        }

        // Skip synthetic log creation for events with contract origin with HAPI versions >= 0.71.0 as the logs are
        // already imported by consensus nodes. We should create logs for events with HAPI origin for any HAPI version.
        return contractOrigin && recordItem.getHapiVersion().isGreaterThanOrEqualTo(HAPI_SYNTHETIC_LOG_VERSION);
    }
}
