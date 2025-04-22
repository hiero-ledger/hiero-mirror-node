// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
        var utilPrng = blockItem
                .getTransactionOutput(TransactionCase.UTIL_PRNG)
                .map(TransactionOutput::getUtilPrng)
                .orElseThrow();
        switch (utilPrng.getEntropyCase()) {
            case PRNG_NUMBER -> recordBuilder.setPrngNumber(utilPrng.getPrngNumber());
            case PRNG_BYTES -> recordBuilder.setPrngBytes(utilPrng.getPrngBytes());
            default ->
                log.warn(
                        "Unhandled entropy case {} for transaction at {}",
                        utilPrng.getEntropyCase(),
                        blockItem.getConsensusTimestamp());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }
}
