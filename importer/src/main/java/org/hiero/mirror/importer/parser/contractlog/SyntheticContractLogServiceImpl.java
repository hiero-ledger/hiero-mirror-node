// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import jakarta.inject.Named;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

@Named
@RequiredArgsConstructor
public class SyntheticContractLogServiceImpl implements SyntheticContractLogService {

    private static final int TOPIC_SIZE_BYTES = 32;

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final byte[] empty = Bytes.of(0).toArray();

    @Override
    public void create(SyntheticContractLog log) {
        if (!entityProperties.getPersist().isSyntheticContractLogs()) {
            return;
        }

        var recordItem = log.getRecordItem();
        var parentRecordItem = recordItem.getContractRelatedParent();

        // We will backfill any EVM-related fungible token transfers that don't have synthetic events produced by CN
        if (isContract(recordItem) && shouldSkipLogCreationForContractTransfer(log)) {
            return;
        }

        long consensusTimestamp = parentRecordItem != null
                ? parentRecordItem.getConsensusTimestamp()
                : recordItem.getConsensusTimestamp();
        int logIndex = recordItem.getAndIncrementLogIndex();

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(isContract(recordItem) ? createBloom(log) : empty);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(log.getEntityId());
        contractLog.setData(log.getData() != null ? log.getData() : empty);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(log.getEntityId());
        contractLog.setPayerAccountId(recordItem.getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(recordItem.getTransactionIndex());
        contractLog.setTransactionHash(recordItem.getTransactionHash());
        contractLog.setSyntheticTransfer(log instanceof TransferContractLog);

        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }

    private boolean shouldSkipLogCreationForContractTransfer(SyntheticContractLog syntheticLog) {
        if (!(syntheticLog instanceof TransferContractLog transferLog)) {
            // Only TransferContractLog synthetic event creation is supported for an operation with contract origin
            return true;
        }

        var tokenTransfersCount =
                syntheticLog.getRecordItem().getTransactionRecord().getTokenTransferListsCount();
        if (tokenTransfersCount > 2 && !entityProperties.getPersist().isSyntheticContractLogsMulti()) {
            // We have a multi-party fungible transfer scenario and synthetic event creation for
            // such transfers is disabled
            return true;
        }

        return logAlreadyImported(transferLog);
    }

    /**
     * Checks if the given TransferContractLog matches an existing contract log in the record itself or
     * in the parent record item and consumes one occurrence of the matching log. This handles the case where the
     * same contract log appears multiple times in the child records as being part of different operations.
     *
     * @param transferLog the TransferContractLog to check
     * @return true if a matching log is found and it is already persisted, false otherwise
     */
    private boolean logAlreadyImported(TransferContractLog transferLog) {
        var parentRecordItem = transferLog.getRecordItem().getContractRelatedParent();
        return transferLog.getRecordItem().consumeMatchingContractLog(transferLog::equalsContractLoginfo)
                || (parentRecordItem != null
                        && parentRecordItem.consumeMatchingContractLog(transferLog::equalsContractLoginfo));
    }

    /**
     * Creates a bloom filter for a synthetic contract log using the log's address, topics, and data.
     *
     * @param log the synthetic contract log
     * @return the bloom filter as a byte array
     */
    private byte[] createBloom(SyntheticContractLog log) {
        var logger = Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(log.getEntityId())));
        var topics = new ArrayList<LogTopic>();

        addTopicIfPresent(topics, log.getTopic0());
        addTopicIfPresent(topics, log.getTopic1());
        addTopicIfPresent(topics, log.getTopic2());
        addTopicIfPresent(topics, log.getTopic3());

        var data = log.getData() != null ? Bytes.wrap(log.getData()) : Bytes.EMPTY;
        var besuLog = new Log(logger, data, topics);

        return LogsBloomFilter.builder().insertLog(besuLog).build().toArray();
    }

    private void addTopicIfPresent(ArrayList<LogTopic> topics, byte[] topic) {
        if (topic != null) {
            topics.add(LogTopic.wrap(Bytes.wrap(DomainUtils.leftPadBytes(topic, TOPIC_SIZE_BYTES))));
        }
    }
}
