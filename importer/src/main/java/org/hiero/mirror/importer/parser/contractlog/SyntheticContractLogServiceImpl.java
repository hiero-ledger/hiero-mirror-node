// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.springframework.data.util.Version;

@Named
@RequiredArgsConstructor
public class SyntheticContractLogServiceImpl implements SyntheticContractLogService {

    protected static final Version HAPI_SYNTHETIC_LOG_VERSION = new Version(0, 71, 0);

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final byte[] empty = Bytes.of(0).toArray();
    protected static final byte[] CONTRACT_LOG_MARKER = Bytes.of(1).toArray();

    @Override
    public void create(SyntheticContractLog log) {
        if (!entityProperties.getPersist().isSyntheticContractLogs()) {
            return;
        }

        var recordItem = log.getRecordItem();
        var contractRelatedParentRecordItem = recordItem.getContractRelatedParent();

        // We will backfill any EVM-related fungible token transfers that don't have synthetic events produced by CN
        if (isContract(recordItem) && shouldSkipLogCreationForContractTransfer(log)) {
            return;
        }

        long consensusTimestamp;
        int logIndex;
        int transactionIndex;
        EntityId contractId;
        EntityId rootContractId;
        byte[] transactionHash;
        if (contractRelatedParentRecordItem != null) {
            consensusTimestamp = contractRelatedParentRecordItem.getConsensusTimestamp();
            logIndex = contractRelatedParentRecordItem.getAndIncrementLogIndex();
            transactionIndex = contractRelatedParentRecordItem.getTransactionIndex();
            transactionHash = contractRelatedParentRecordItem.getTransactionHash();

            final var parentTransactionRecord = contractRelatedParentRecordItem.getTransactionRecord();
            if (parentTransactionRecord.hasContractCallResult()) {
                contractId = EntityId.of(
                        parentTransactionRecord.getContractCallResult().getContractID());
            } else {
                contractId = EntityId.of(
                        parentTransactionRecord.getContractCreateResult().getContractID());
            }

            final var parentTransactionBody = contractRelatedParentRecordItem.getTransactionBody();
            if (parentTransactionBody.hasContractCall()) {
                final var contractIdReceipt =
                        parentTransactionRecord.getReceipt().getContractID();

                rootContractId = EntityId.of(contractIdReceipt);
            } else {
                rootContractId =
                        EntityId.of(parentTransactionRecord.getReceipt().getContractID());
            }
        } else {
            consensusTimestamp = recordItem.getConsensusTimestamp();
            logIndex = recordItem.getAndIncrementLogIndex();
            transactionIndex = recordItem.getTransactionIndex();
            transactionHash = recordItem.getTransactionHash();
            contractId = log.getEntityId();
            rootContractId = log.getEntityId();
        }

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(isContract(recordItem) ? CONTRACT_LOG_MARKER : empty);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(contractId);
        contractLog.setData(log.getData() != null ? log.getData() : empty);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(rootContractId);
        contractLog.setPayerAccountId(recordItem.getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(transactionIndex);
        contractLog.setTransactionHash(transactionHash);
        contractLog.setSynthetic(log instanceof TransferContractLog);

        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }

    private boolean shouldSkipLogCreationForContractTransfer(SyntheticContractLog syntheticLog) {
        if (!(syntheticLog instanceof TransferContractLog)) {
            // Only TransferContractLog synthetic event creation is supported for an operation with contract origin
            return true;
        }

        var recordItem = syntheticLog.getRecordItem();

        int tokenTransfersCount = recordItem.getTransactionRecord().getTokenTransferListsCount();
        if (tokenTransfersCount > 2 && !entityProperties.getPersist().isSyntheticContractLogsMulti()) {
            // We have a multi-party fungible transfer scenario and synthetic event creation for
            // such transfers is disabled
            return true;
        }

        // Skip synthetic log creation for HAPI versions >= 0.71.0 as the logs are already imported by consensus nodes
        return recordItem.getHapiVersion().isGreaterThanOrEqualTo(HAPI_SYNTHETIC_LOG_VERSION);
    }
}
