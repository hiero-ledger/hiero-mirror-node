// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class ScheduleCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()
                && blockItem.getTransactionResult().getStatus() != IDENTICAL_SCHEDULE_ALREADY_CREATED) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        var createSchedule =
                blockItem.getTransactionOutput(TransactionCase.CREATE_SCHEDULE).getCreateSchedule();
        receiptBuilder.setScheduleID(createSchedule.getScheduleId());
        if (createSchedule.hasScheduledTransactionId()) {
            receiptBuilder.setScheduledTransactionID(createSchedule.getScheduledTransactionId());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULECREATE;
    }
}
