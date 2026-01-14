// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        var utilPrng = blockTransaction
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
                        blockTransaction.getConsensusTimestamp());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }
}
