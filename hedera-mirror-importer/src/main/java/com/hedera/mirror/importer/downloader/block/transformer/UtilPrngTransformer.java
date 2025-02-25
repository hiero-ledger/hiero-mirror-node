// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {

        if (!blockItem.successful()) {
            return;
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasUtilPrng()) {
                var utilPrng = transactionOutput.getUtilPrng();
                switch (utilPrng.getEntropyCase()) {
                    case PRNG_NUMBER -> transactionRecordBuilder.setPrngNumber(utilPrng.getPrngNumber());
                    case PRNG_BYTES -> transactionRecordBuilder.setPrngBytes(utilPrng.getPrngBytes());
                    default -> UtilPrngTransformer.log.warn("Unhandled entropy case: {}", utilPrng.getEntropyCase());
                }
                return;
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }
}
