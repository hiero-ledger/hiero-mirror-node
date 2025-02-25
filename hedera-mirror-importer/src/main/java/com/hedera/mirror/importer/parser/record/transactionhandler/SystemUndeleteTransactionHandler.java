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
