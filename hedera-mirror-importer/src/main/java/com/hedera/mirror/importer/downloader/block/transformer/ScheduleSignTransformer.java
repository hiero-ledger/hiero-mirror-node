// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class ScheduleSignTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasSignSchedule()
                    && transactionOutput.getSignSchedule().hasScheduledTransactionId()) {
                transactionRecordBuilder
                        .getReceiptBuilder()
                        .setScheduledTransactionID(
                                transactionOutput.getSignSchedule().getScheduledTransactionId());
                return;
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULESIGN;
    }
}
