// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class CryptoUpdateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var transactionBody = blockTransaction.getTransactionBody().getCryptoUpdateAccount();
        final var accountIdToUpdate = transactionBody.getAccountIDToUpdate();
        final var stateChangeContext = blockTransaction.getStateChangeContext();

        final var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();

        // Consensus node rejects an alias in accountIDToUpdate, so a successful update always has an account number
        if (!accountIdToUpdate.hasAccountNum()) {
            return;
        }

        receiptBuilder.setAccountID(accountIdToUpdate);
        if (transactionBody.getDelegationAddress().isEmpty()) {
            return;
        }

        final var ethereumNonce = stateChangeContext.getAccountEthereumNonce(accountIdToUpdate);
        if (ethereumNonce != null) {
            blockTransactionTransformation.recordItemBuilder().accountEthereumNonce(ethereumNonce);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOUPDATEACCOUNT;
    }
}
