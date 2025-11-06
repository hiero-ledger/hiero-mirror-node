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

        final var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        final var accountId =
                blockTransaction.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate();

        if (accountId.hasAccountNum()) {
            receiptBuilder.setAccountID(accountId);
        } else if (accountId.hasAlias()) {
            blockTransaction
                    .getStateChangeContext()
                    .getAccountId(accountId.getAlias())
                    .ifPresent(receiptBuilder::setAccountID);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOUPDATEACCOUNT;
    }
}
