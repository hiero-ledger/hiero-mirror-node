// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static java.time.ZoneOffset.UTC;
import static org.hiero.mirror.common.util.DomainUtils.logRecoverableError;

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
final class FreezeTransactionHandler extends AbstractTransactionHandler {

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

        long consensusTimeStamp = recordItem.getConsensusTimestamp();
        long startTime = 0L;
        Long endTime = null;
        final var body = recordItem.getTransactionBody().getFreeze();

        if (body.hasStartTime()) {
            startTime = DomainUtils.timestampInNanosMax(body.getStartTime());
        } else {
            final var consensusTime = Instant.ofEpochSecond(0L, consensusTimeStamp);
            final var startOfDay = LocalDate.ofInstant(consensusTime, UTC).atStartOfDay();

            int startHour = body.getStartHour();
            int startMinute = body.getStartMin();
            int endHour = body.getEndHour();
            int endMinute = body.getEndMin();

            if (startHour >= 0 && startHour <= 23 && startMinute >= 0 && startMinute <= 59) {
                final var startDateTime = startOfDay.withHour(startHour).withMinute(startMinute);
                startTime = DomainUtils.convertToNanosMax(startDateTime.toInstant(UTC));
            } else {
                logRecoverableError(
                        "Freeze transaction {} contains an invalid start time {}:{}",
                        consensusTimeStamp,
                        startHour,
                        startMinute);
            }

            if (endHour >= 0 && endHour <= 23 && endMinute >= 0 && endMinute <= 59) {
                var endDateTime = startOfDay.withHour(endHour).withMinute(endMinute);

                // The freeze starts in one day, but ends in another
                if (startTime > 0 && startHour > endHour) {
                    endDateTime = endDateTime.plusDays(1);
                }

                endTime = DomainUtils.convertToNanosMax(endDateTime.toInstant(UTC));
            } else {
                logRecoverableError(
                        "Freeze transaction {} contains an invalid end time {}:{}",
                        consensusTimeStamp,
                        endHour,
                        endMinute);
            }
        }

        final var networkFreeze = new NetworkFreeze();
        networkFreeze.setConsensusTimestamp(consensusTimeStamp);
        networkFreeze.setEndTime(endTime);
        networkFreeze.setFileHash(DomainUtils.toBytes(body.getFileHash()));
        networkFreeze.setFileId(transaction.getEntityId());
        networkFreeze.setPayerAccountId(recordItem.getPayerAccountId());
        networkFreeze.setStartTime(startTime);
        networkFreeze.setType(body.getFreezeTypeValue());
        entityListener.onNetworkFreeze(networkFreeze);
    }
}
