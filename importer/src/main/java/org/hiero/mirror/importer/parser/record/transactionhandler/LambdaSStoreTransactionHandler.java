// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
@CustomLog
final class LambdaSStoreTransactionHandler extends AbstractTransactionHandler {

    private final EvmHookStorageHandler hookHandler;
    private final EntityIdService entityIdService;

    @Override
    public TransactionType getType() {
        return TransactionType.LAMBDA_SSTORE;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        final var hookEntityId =
                recordItem.getTransactionBody().getLambdaSstore().getHookId().getEntityId();

        if (hookEntityId.hasAccountId()) {
            return entityIdService.lookup(hookEntityId.getAccountId()).orElse(EntityId.EMPTY);
        }

        return entityIdService.lookup(hookEntityId.getContractId()).orElse(EntityId.EMPTY);
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var transactionBody = recordItem.getTransactionBody().getLambdaSstore();
        final var ownerEntityId = transaction.getEntityId();
        final var hookId = transactionBody.getHookId().getHookId();
        final var consensusTimestamp = recordItem.getConsensusTimestamp();

        hookHandler.processStorageUpdates(
                consensusTimestamp, hookId, ownerEntityId, transactionBody.getStorageUpdatesList());
    }
}
