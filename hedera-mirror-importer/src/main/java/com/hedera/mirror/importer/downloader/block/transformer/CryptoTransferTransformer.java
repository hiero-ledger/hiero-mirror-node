// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
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

        if(blockItem.hasTransactionOutput(TransactionCase.CRYPTO_TRANSFER)) {
            var cryptoTransfer =
                    blockItem.getTransactionOutput(TransactionCase.CRYPTO_TRANSFER).getCryptoTransfer();
            var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
            recordBuilder.addAllAssessedCustomFees(cryptoTransfer.getAssessedCustomFeesList());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
