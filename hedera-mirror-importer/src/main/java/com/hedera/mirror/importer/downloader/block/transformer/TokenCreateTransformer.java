// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class TokenCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        receiptBuilder.setNewTotalSupply(transactionBody.getTokenCreation().getInitialSupply());
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
