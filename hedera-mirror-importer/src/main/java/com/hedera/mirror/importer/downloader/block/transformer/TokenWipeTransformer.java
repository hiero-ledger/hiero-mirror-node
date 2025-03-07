// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class TokenWipeTransformer extends AbstractTokenTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var body = blockItemTransformation.transactionBody().getTokenWipe();
        var tokenId = body.getToken();
        long amount = body.getAmount() + body.getSerialNumbersCount();
        updateTotalSupply(
                blockItemTransformation.recordItemBuilder(), blockItem.getStateChangeContext(), tokenId, amount);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENWIPE;
    }
}
