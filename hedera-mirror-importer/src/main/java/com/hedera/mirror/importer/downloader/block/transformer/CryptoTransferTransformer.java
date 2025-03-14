// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
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

        blockItem
                .getTransactionOutput(TransactionCase.CRYPTO_TRANSFER)
                .map(TransactionOutput::getCryptoTransfer)
                .ifPresent(cryptoTransfer -> {
                    var recordBuilder =
                            blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
                    recordBuilder.addAllAssessedCustomFees(cryptoTransfer.getAssessedCustomFeesList());
                });
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
