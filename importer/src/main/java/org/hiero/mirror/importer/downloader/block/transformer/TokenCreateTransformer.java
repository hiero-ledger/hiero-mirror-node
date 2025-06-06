// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class TokenCreateTransformer extends AbstractBlockItemTransformer {

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
        receiptBuilder.setNewTotalSupply(
                blockItemTransformation.transactionBody().getTokenCreation().getInitialSupply());
        blockItem
                .getStateChangeContext()
                .getNewTokenId()
                .map(receiptBuilder::setTokenID)
                .orElseThrow();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENCREATION;
    }
}
