// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class TokenWipeTransformer extends AbstractTokenTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var body = transactionBody.getTokenWipe();
        var tokenId = body.getToken();
        long amount = body.getAmount() + body.getSerialNumbersCount();
        updateTotalSupply(recordItemBuilder, blockItem.getStateChangeContext(), tokenId, amount);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENWIPE;
    }
}
