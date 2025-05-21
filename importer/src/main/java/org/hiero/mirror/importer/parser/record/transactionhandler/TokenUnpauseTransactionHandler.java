// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class TokenUnpauseTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUnpause().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENUNPAUSE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        Token token = new Token();
        token.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);
        token.setTimestampLower(recordItem.getConsensusTimestamp());
        token.setTokenId(transaction.getEntityId().getId());
        entityListener.onToken(token);
    }
}
