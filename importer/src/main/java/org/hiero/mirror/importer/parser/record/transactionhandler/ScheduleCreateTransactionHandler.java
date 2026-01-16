// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.AccountID;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
class ScheduleCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;

    ScheduleCreateTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(entityIdService, entityListener, TransactionType.SCHEDULECREATE);
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getScheduleID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getScheduleCreate();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        entity.setMemo(transactionBody.getMemo());
        entity.setType(EntityType.SCHEDULE);
        entityListener.onEntity(entity);
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful() || !entityProperties.getPersist().isSchedules()) {
            return;
        }

        var body = recordItem.getTransactionBody().getScheduleCreate();
        long consensusTimestamp = recordItem.getConsensusTimestamp();

        var creatorAccount = recordItem.getPayerAccountId();

        var parentRecordItem = recordItem.getParent();
        if (parentRecordItem != null && parentRecordItem.getEthereumTransaction() != null) {
            var transactionRecord = parentRecordItem.getTransactionRecord();
            var functionResult = transactionRecord.hasContractCreateResult()
                    ? transactionRecord.getContractCreateResult()
                    : transactionRecord.getContractCallResult();

            if (!AccountID.getDefaultInstance().equals(functionResult.getSenderId())) {
                creatorAccount = EntityId.of(functionResult.getSenderId());
            }
        }

        var expirationTime =
                body.hasExpirationTime() ? DomainUtils.timestampInNanosMax(body.getExpirationTime()) : null;
        var payerAccount = body.hasPayerAccountID() ? EntityId.of(body.getPayerAccountID()) : creatorAccount;
        var scheduleId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getScheduleID());

        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(creatorAccount);
        schedule.setExpirationTime(expirationTime);
        schedule.setPayerAccountId(payerAccount);
        schedule.setScheduleId(scheduleId);
        schedule.setTransactionBody(body.getScheduledTransactionBody().toByteArray());
        schedule.setWaitForExpiry(body.getWaitForExpiry());
        entityListener.onSchedule(schedule);

        recordItem.addEntityId(payerAccount);
    }
}
