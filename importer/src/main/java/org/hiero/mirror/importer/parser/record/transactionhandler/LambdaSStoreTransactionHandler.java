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

    private final EvmHookStorageHandler hookHandler;

    @Override
    public TransactionType getType() {
        return TransactionType.LAMBDA_SSTORE;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        final var body = recordItem.getTransactionBody().getLambdaSstore();
        final var hookId = body.getHookId().getEntityId();

        if (hookId.hasAccountId()) {
            return EntityId.of(hookId.getAccountId());
        }

        return EntityId.of(hookId.getContractId());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getLambdaSstore();
        final var ownerId = transaction.getEntityId().getId();
        final var hookId = transactionBody.getHookId().getHookId();
        final var consensusTimestamp = recordItem.getConsensusTimestamp();

        hookHandler.processStorageUpdates(consensusTimestamp, hookId, ownerId, transactionBody.getStorageUpdatesList());
    }
}
