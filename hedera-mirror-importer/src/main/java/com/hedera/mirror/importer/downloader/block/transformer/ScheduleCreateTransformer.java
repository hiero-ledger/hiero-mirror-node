// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_SCHEDULES_BY_ID;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@SuppressWarnings("java:S3776")
@Named
final class ScheduleCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();
        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasCreateSchedule()) {
                var output = transactionOutput.getCreateSchedule();
                if (output.hasScheduledTransactionId()) {
                    receiptBuilder.setScheduledTransactionID(output.getScheduledTransactionId());
                    break;
                }
            }
        }

        for (var stateChanges : blockItem.stateChanges()) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_SCHEDULES_BY_ID.getNumber() && stateChange.hasMapUpdate()) {
                    var key = stateChange.getMapUpdate().getKey();
                    if (key.hasScheduleIdKey()) {
                        receiptBuilder.setScheduleID(key.getScheduleIdKey());
                        return;
                    }
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULECREATE;
    }
}
