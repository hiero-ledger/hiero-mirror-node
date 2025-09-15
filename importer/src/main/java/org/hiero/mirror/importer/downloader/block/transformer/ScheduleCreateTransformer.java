// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ScheduleCreateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()
                && blockTransaction.getTransactionResult().getStatus() != IDENTICAL_SCHEDULE_ALREADY_CREATED) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        var createSchedule = blockTransaction
                .getTransactionOutput(TransactionCase.CREATE_SCHEDULE)
                .map(TransactionOutput::getCreateSchedule)
                .orElseThrow();
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
