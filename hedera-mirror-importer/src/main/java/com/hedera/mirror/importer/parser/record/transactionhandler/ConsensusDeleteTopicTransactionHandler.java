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
class ConsensusDeleteTopicTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ConsensusDeleteTopicTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CONSENSUSDELETETOPIC);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(
                recordItem.getTransactionBody().getConsensusDeleteTopic().getTopicID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        entity.setType(EntityType.TOPIC);
        entityListener.onEntity(entity);
    }
}
