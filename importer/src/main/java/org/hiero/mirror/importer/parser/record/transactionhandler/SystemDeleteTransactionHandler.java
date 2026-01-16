// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class SystemDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    SystemDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.SYSTEMDELETE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var systemDelete = recordItem.getTransactionBody().getSystemDelete();

        if (systemDelete.hasContractID()) {
            return entityIdService.lookup(systemDelete.getContractID()).orElse(EntityId.EMPTY);
        } else if (systemDelete.hasFileID()) {
            return EntityId.of(systemDelete.getFileID());
        }

        return null;
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getSystemDelete();
        EntityType entityType = null;

        if (transactionBody.hasContractID()) {
            entityType = EntityType.CONTRACT;
        } else if (transactionBody.hasFileID()) {
            entityType = EntityType.FILE;
        }

        entity.setType(entityType);
        entityListener.onEntity(entity);
    }
}
