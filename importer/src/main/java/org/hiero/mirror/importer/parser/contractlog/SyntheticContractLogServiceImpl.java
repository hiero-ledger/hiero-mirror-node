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

        if (isContract(log.getRecordItem())) {
            if (!(log instanceof TransferContractLog transferLog)) {
                return;
            }
            var contractParent = log.getRecordItem().parseContractParent();
            if (contractParent != null && matchesExistingContractLog(transferLog, contractParent)) {
                return;
            }
        }

        long consensusTimestamp = log.getRecordItem().getConsensusTimestamp();
        int logIndex = log.getRecordItem().getAndIncrementLogIndex();

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(isContract(log.getRecordItem()) ? createBloom(log) : empty);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(log.getEntityId());
        contractLog.setData(log.getData() != null ? log.getData() : empty);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(log.getEntityId());
        contractLog.setPayerAccountId(log.getRecordItem().getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(log.getRecordItem().getTransactionIndex());
        contractLog.setTransactionHash(log.getRecordItem().getTransactionHash());
        contractLog.setSyntheticTransfer(log instanceof TransferContractLog);

        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }

    private boolean matchesExistingContractLog(TransferContractLog transferLog, RecordItem contractParent) {
        var contractLogs = contractParent.getContractLogs();
        if (contractLogs == null || contractLogs.isEmpty()) {
            return false;
        }

        for (var contractLoginfo : contractLogs) {
            if (transferLog.equalsContractLoginfo(contractLoginfo)) {
                return true;
            }
        }
        return false;
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
