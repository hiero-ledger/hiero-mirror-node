// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static org.hiero.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class ConsensusSubmitMessageTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getConsensusSubmitMessage().getTopicID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }

    /**
     * Add common entity ids for a ConsensusSubmitMessage transaction. Note the main entity id (the topic id the message
     * is submitted to) is skipped since it's already tracked in topic_message table
     *
     * @param transaction
     * @param recordItem
     */
    @Override
    protected void addCommonEntityIds(Transaction transaction, RecordItem recordItem) {
        recordItem.addEntityId(transaction.getNodeAccountId());
        recordItem.addEntityId(transaction.getPayerAccountId());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTopics() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getConsensusSubmitMessage();
        var transactionRecord = recordItem.getTransactionRecord();
        var receipt = transactionRecord.getReceipt();
        var topicMessage = new TopicMessage();

        // Only persist the value if it is not the default
        if (receipt.getTopicRunningHashVersion() != DEFAULT_RUNNING_HASH_VERSION) {
            var runningHashVersion =
                    receipt.getTopicRunningHashVersion() == 0 ? 1 : (int) receipt.getTopicRunningHashVersion();
            topicMessage.setRunningHashVersion(runningHashVersion);
        }

        // Handle optional fragmented topic message
        if (transactionBody.hasChunkInfo()) {
            ConsensusMessageChunkInfo chunkInfo = transactionBody.getChunkInfo();
            topicMessage.setChunkNum(chunkInfo.getNumber());
            topicMessage.setChunkTotal(chunkInfo.getTotal());

            if (chunkInfo.hasInitialTransactionID()) {
                topicMessage.setInitialTransactionId(
                        chunkInfo.getInitialTransactionID().toByteArray());
            }
        }

        topicMessage.setConsensusTimestamp(transaction.getConsensusTimestamp());
        topicMessage.setMessage(toBytes(transactionBody.getMessage()));
        topicMessage.setPayerAccountId(recordItem.getPayerAccountId());
        topicMessage.setRunningHash(toBytes(receipt.getTopicRunningHash()));
        topicMessage.setSequenceNumber(receipt.getTopicSequenceNumber());
        topicMessage.setTopicId(transaction.getEntityId());
        entityListener.onTopicMessage(topicMessage);
    }
}
