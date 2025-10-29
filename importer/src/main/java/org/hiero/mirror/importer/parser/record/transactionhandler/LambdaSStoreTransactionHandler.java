// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
@CustomLog
final class LambdaSStoreTransactionHandler extends AbstractTransactionHandler {

    private final EVMHookStorageHandler storageHandler;

    @Override
    public TransactionType getType() {
        return TransactionType.LAMBDA_SSTORE;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        if (!recordItem.getTransactionBody().hasLambdaSstore()) {
            return EntityId.EMPTY;
        }
        return EntityId.of(recordItem
                .getTransactionBody()
                .getLambdaSstore()
                .getHookId()
                .getEntityId()
                .getAccountId());
    }

    @Override
    protected void doUpdateTransaction(Transaction txn, RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody();

        if (!transactionBody.hasLambdaSstore()) {
            log.warn("no lambda sstore in transaction body consensus_timestamp={}", recordItem.getConsensusTimestamp());
            return;
        }

        final var sstore = transactionBody.getLambdaSstore();
        final var owner = getEntity(recordItem);
        final var ownerId = owner.getId();
        final var hookId = sstore.getHookId().getHookId();
        final var consensusTimestamp = recordItem.getConsensusTimestamp();

        storageHandler.processStorageUpdates(consensusTimestamp, hookId, ownerId, recordItem.getSidecarRecords());
    }
}
