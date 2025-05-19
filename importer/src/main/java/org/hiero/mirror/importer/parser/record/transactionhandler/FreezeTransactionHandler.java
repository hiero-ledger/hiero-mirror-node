// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static java.time.ZoneOffset.UTC;

import jakarta.inject.Named;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.NetworkFreeze;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class FreezeTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getFreeze().getUpdateFile());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FREEZE;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long startTime;
        Long endTime = null;
        var body = recordItem.getTransactionBody().getFreeze();
        var fileId = body.hasUpdateFile() ? EntityId.of(body.getUpdateFile()) : null;

        if (body.hasStartTime()) {
            startTime = DomainUtils.timestampInNanosMax(body.getStartTime());
        } else {
            var consensusTime = Instant.ofEpochSecond(0L, recordItem.getConsensusTimestamp());
            var startOfDay = LocalDate.ofInstant(consensusTime, UTC).atStartOfDay();

            var startDateTime = startOfDay.withHour(body.getStartHour()).withMinute(body.getStartMin());
            startTime = DomainUtils.convertToNanosMax(startDateTime.toInstant(UTC));

            var endDateTime = startOfDay.withHour(body.getEndHour()).withMinute(body.getEndMin());

            // The freeze starts in one day, but ends in another
            if (body.getStartHour() > body.getEndHour()) {
                endDateTime = endDateTime.plusDays(1);
            }

            endTime = DomainUtils.convertToNanosMax(endDateTime.toInstant(UTC));
        }

        var networkFreeze = new NetworkFreeze();
        networkFreeze.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        networkFreeze.setEndTime(endTime);
        networkFreeze.setFileHash(DomainUtils.toBytes(body.getFileHash()));
        networkFreeze.setFileId(fileId);
        networkFreeze.setPayerAccountId(recordItem.getPayerAccountId());
        networkFreeze.setStartTime(startTime);
        networkFreeze.setType(body.getFreezeTypeValue());
        entityListener.onNetworkFreeze(networkFreeze);
    }
}
