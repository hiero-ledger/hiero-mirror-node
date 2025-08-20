// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.hiero.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;

@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        var topicMessage = blockTransaction
                .getStateChangeContext()
                .getTopicMessage(blockTransactionTransformation
                        .getTransactionBody()
                        .getConsensusSubmitMessage()
                        .getTopicID())
                .orElseThrow();
        recordBuilder
                .getReceiptBuilder()
                .setTopicRunningHash(DomainUtils.fromBytes(topicMessage.getRunningHash()))
                .setTopicRunningHashVersion(DEFAULT_RUNNING_HASH_VERSION)
                .setTopicSequenceNumber(topicMessage.getSequenceNumber());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
