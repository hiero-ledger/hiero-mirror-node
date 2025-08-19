// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class TokenMintTransformer extends AbstractTokenTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockItem = blockTransactionTransformation.blockTransaction();
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

        var recordItemBuilder = blockTransactionTransformation.recordItemBuilder();
        recordItemBuilder.transactionRecordBuilder().getReceiptBuilder().addAllSerialNumbers(serialNumbers);

        var body = blockTransactionTransformation.getTransactionBody().getTokenMint();
        var tokenId = body.getToken();
        long amount = body.getAmount() + body.getMetadataCount();
        updateTotalSupply(recordItemBuilder, blockItem.getStateChangeContext(), tokenId, -amount);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }
}
