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
class TokenDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    TokenDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.TOKENDELETION);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenDeletion().getToken());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        entity.setType(EntityType.TOKEN);
        entityListener.onEntity(entity);
    }
}
