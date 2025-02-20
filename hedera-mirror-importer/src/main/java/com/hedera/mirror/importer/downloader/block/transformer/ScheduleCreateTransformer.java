// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class ScheduleCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItem.RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.successful()
                && blockItem.transactionResult().getStatus() != ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        var createSchedule = blockItem
                .transactionOutputs()
                .get(TransactionCase.CREATE_SCHEDULE)
                .getCreateSchedule();
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
