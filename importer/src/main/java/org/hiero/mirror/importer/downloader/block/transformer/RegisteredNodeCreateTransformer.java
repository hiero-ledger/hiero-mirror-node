// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class RegisteredNodeCreateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(final BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        receiptBuilder.setRegisteredNodeId(blockTransaction
                .getStateChangeContext()
                .getNewRegisteredNodeId()
                .orElseThrow());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.REGISTEREDNODECREATE;
    }
}
