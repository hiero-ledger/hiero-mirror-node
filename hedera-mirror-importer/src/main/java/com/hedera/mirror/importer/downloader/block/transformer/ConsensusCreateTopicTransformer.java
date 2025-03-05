// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class ConsensusCreateTopicTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        blockItem
                .getStateChangeContext()
                .getNewTopicId()
                .ifPresentOrElse(
                        topicId -> blockItemTransformation
                                .recordItemBuilder()
                                .transactionRecordBuilder()
                                .getReceiptBuilder()
                                .setTopicID(topicId),
                        () -> log.warn(
                                "No new topic id found for ConsensusCreateTopic transaction at {}",
                                blockItem.getConsensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSCREATETOPIC;
    }
}
