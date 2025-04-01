// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class CryptoTransferTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }
        var customFees = blockItem.getTransactionResult().getAssessedCustomFeesList();
        if (!customFees.isEmpty()) {
            var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
            recordBuilder.addAllAssessedCustomFees(customFees);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
