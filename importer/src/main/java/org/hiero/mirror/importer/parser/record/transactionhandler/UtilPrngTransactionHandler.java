// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@CustomLog
@Named
@RequiredArgsConstructor
class UtilPrngTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var range = recordItem.getTransactionBody().getUtilPrng().getRange();
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionRecord = recordItem.getTransactionRecord();
        var prng = new Prng();
        prng.setConsensusTimestamp(consensusTimestamp);
        prng.setPayerAccountId(recordItem.getPayerAccountId().getId());
        prng.setRange(range);
        switch (transactionRecord.getEntropyCase()) {
            case PRNG_BYTES -> prng.setPrngBytes(DomainUtils.toBytes(transactionRecord.getPrngBytes()));
            case PRNG_NUMBER -> prng.setPrngNumber(transactionRecord.getPrngNumber());
            default -> {
                log.warn(
                        "Unsupported entropy case {} at consensus timestamp {}",
                        transactionRecord.getEntropyCase(),
                        consensusTimestamp);
                return;
            }
        }

        entityListener.onPrng(prng);
    }
}
