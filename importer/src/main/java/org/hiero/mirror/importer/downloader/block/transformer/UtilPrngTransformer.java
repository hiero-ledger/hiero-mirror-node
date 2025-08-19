// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockItem = blockTransactionTransformation.blockTransaction();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
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
