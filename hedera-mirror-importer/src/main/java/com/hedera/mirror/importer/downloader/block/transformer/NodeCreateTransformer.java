// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class NodeCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        blockItem
                .getStateChangeContext()
                .getNewNodeId()
                .ifPresentOrElse(
                        receiptBuilder::setNodeId,
                        () -> log.warn(
                                "No new node id found for NodeCreate transaction at {}",
                                blockItem.getConsensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODECREATE;
    }
}
