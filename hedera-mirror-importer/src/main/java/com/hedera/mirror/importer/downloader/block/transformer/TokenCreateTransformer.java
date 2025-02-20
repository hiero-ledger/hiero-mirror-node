// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class TokenCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.successful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        receiptBuilder.setNewTotalSupply(transactionBody.getTokenCreation().getInitialSupply());
        stateChangeContext
                .getNewTokenId()
                .ifPresentOrElse(
                        receiptBuilder::setTokenID,
                        () -> log.warn(
                                "No token id found for TokenCreate transaction at {}", blockItem.consensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENCREATION;
    }
}
