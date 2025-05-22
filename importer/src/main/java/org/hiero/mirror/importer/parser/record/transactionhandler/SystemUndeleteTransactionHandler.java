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
class SystemUndeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    SystemUndeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.SYSTEMUNDELETE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var systemUndelete = recordItem.getTransactionBody().getSystemUndelete();

        if (systemUndelete.hasContractID()) {
            return entityIdService.lookup(systemUndelete.getContractID()).orElse(EntityId.EMPTY);
        } else if (systemUndelete.hasFileID()) {
            return EntityId.of(systemUndelete.getFileID());
        }

        return null;
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getSystemUndelete();
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
