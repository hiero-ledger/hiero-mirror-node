// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class CryptoTransferTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var cryptoTransfer =
                blockItem.getTransactionOutput(TransactionCase.CRYPTO_TRANSFER).getCryptoTransfer();
        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        recordBuilder.addAllAssessedCustomFees(cryptoTransfer.getAssessedCustomFeesList());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
