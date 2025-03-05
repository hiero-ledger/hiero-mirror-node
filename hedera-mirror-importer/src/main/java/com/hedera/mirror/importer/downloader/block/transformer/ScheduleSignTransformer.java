// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;

@Named
final class ScheduleSignTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var signSchedule =
                blockItem.getTransactionOutput(TransactionCase.SIGN_SCHEDULE).getSignSchedule();
        if (signSchedule.hasScheduledTransactionId()) {
            blockItemTransformation
                    .recordItemBuilder()
                    .transactionRecordBuilder()
                    .getReceiptBuilder()
                    .setScheduledTransactionID(signSchedule.getScheduledTransactionId());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULESIGN;
    }
}
