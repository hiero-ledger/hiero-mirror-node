// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;

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
