// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.AccountID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityOperation;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@RequiredArgsConstructor
abstract class AbstractEntityCrudTransactionHandler extends AbstractTransactionHandler {

    protected final EntityIdService entityIdService;

    protected final EntityListener entityListener;

    @Getter
    private final TransactionType type;

    @Override
    protected final void updateEntity(Transaction transaction, RecordItem recordItem) {
        var entityId = transaction.getEntityId();
        var entityOperation = type.getEntityOperation();

        if (entityOperation == EntityOperation.NONE || EntityId.isEmpty(entityId) || !recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var entity = entityId.toEntity();

        if (entityOperation == EntityOperation.CREATE) {
            entity.setCreatedTimestamp(consensusTimestamp);
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.UPDATE) {
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.DELETE) {
            entity.setDeleted(true);
        }

        entity.setTimestampLower(consensusTimestamp);
        doUpdateEntity(entity, recordItem);
    }

    protected void updateProxyAccountId(
            final Entity entity, final RecordItem recordItem, final AccountID proxyAccountId) {
        final var parsed = EntityId.tryOf(proxyAccountId);
        if (EntityId.isEmpty(parsed)) {
            return;
        }

        entity.setProxyAccountId(parsed);
        recordItem.addEntityId(parsed);
    }

    protected abstract void doUpdateEntity(Entity entity, RecordItem recordItem);
}
