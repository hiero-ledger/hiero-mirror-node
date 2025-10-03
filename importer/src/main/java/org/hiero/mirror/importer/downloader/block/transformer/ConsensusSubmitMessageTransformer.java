// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.hiero.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;

@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder()
                .setTopicRunningHashVersion(DEFAULT_RUNNING_HASH_VERSION);

        var topicMessage = blockTransaction.getTopicMessage();
        if (topicMessage == null) {
            log.warn(
                    "Missing topic message runningHash and sequence number at {}",
                    blockTransaction.getConsensusTimestamp());
            return;
        }

        receiptBuilder
                .setTopicRunningHash(DomainUtils.fromBytes(topicMessage.getRunningHash()))
                .setTopicSequenceNumber(topicMessage.getSequenceNumber());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
