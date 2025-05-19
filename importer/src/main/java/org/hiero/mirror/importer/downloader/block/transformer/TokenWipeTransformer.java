// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

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
