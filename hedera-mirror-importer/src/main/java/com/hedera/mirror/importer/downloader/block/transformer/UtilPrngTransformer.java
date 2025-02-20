// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.successful()) {
            return;
        }

        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        var utilPrng =
                blockItem.transactionOutputs().get(TransactionCase.UTIL_PRNG).getUtilPrng();
        switch (utilPrng.getEntropyCase()) {
            case PRNG_NUMBER -> recordBuilder.setPrngNumber(utilPrng.getPrngNumber());
            case PRNG_BYTES -> recordBuilder.setPrngBytes(utilPrng.getPrngBytes());
            default -> log.warn(
                    "Unhandled entropy case {} for transaction at {}",
                    utilPrng.getEntropyCase(),
                    blockItem.consensusTimestamp());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }
}
