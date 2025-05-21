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
