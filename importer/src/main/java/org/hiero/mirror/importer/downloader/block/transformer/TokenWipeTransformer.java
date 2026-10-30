// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import java.util.HashSet;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class TokenWipeTransformer extends AbstractTokenTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var body = blockTransactionTransformation.getTransactionBody().getTokenWipe();
        var tokenId = body.getToken();
        // Consensus nodes de-duplicate the serial numbers, so only distinct serials change the total supply
        long amount = body.getAmount() + new HashSet<>(body.getSerialNumbersList()).size();
        updateTotalSupply(
                amount,
                blockTransaction.getConsensusTimestamp(),
                blockTransactionTransformation.recordItemBuilder(),
                blockTransaction.getStateChangeContext(),
                tokenId);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENWIPE;
    }
}
