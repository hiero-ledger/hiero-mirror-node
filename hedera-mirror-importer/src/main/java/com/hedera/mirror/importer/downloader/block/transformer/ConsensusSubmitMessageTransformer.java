// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.inject.Named;

@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();

        var topicMessage = blockItem
                .getStateChangeContext()
                .getTopicMessage(blockItemTransformation
                        .transactionBody()
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
