// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class FileCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockItem = blockTransactionTransformation.blockTransaction();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        receiptBuilder.setFileID(
                blockItem.getStateChangeContext().getNewFileId().orElseThrow());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FILECREATE;
    }
}
