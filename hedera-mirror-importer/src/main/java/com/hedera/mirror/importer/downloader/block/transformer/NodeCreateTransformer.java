// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class NodeCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockItemTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        blockItem
                .getStateChangeContext()
                .getNewNodeId()
                .map(receiptBuilder::setNodeId)
                .orElseThrow();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODECREATE;
    }
}
