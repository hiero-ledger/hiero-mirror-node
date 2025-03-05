// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;

@Named
final class TokenMintTransformer extends AbstractTokenTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var tokenTransferLists = blockItem.getTransactionResult().getTokenTransferListsList();
        var serialNumbers = new ArrayList<Long>();
        for (var tokenTransferList : tokenTransferLists) {
            for (var nftTransfer : tokenTransferList.getNftTransfersList()) {
                serialNumbers.add(nftTransfer.getSerialNumber());
            }
        }
        Collections.sort(serialNumbers);
        recordItemBuilder.transactionRecordBuilder().getReceiptBuilder().addAllSerialNumbers(serialNumbers);

        var body = transactionBody.getTokenMint();
        var tokenId = body.getToken();
        long amount = body.getAmount() + body.getMetadataCount();
        updateTotalSupply(recordItemBuilder, blockItem.getStateChangeContext(), tokenId, -amount);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }
}
