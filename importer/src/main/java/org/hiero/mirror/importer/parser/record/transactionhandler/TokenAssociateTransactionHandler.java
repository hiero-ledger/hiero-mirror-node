// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class TokenAssociateTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenAssociate().getAccount());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENASSOCIATE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenAssociate();
        long consensusTimestamp = transaction.getConsensusTimestamp();

        transactionBody.getTokensList().forEach(token -> {
            var tokenId = EntityId.of(token);
            var tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(transaction.getEntityId().getId());
            tokenAccount.setAssociated(true);
            tokenAccount.setAutomaticAssociation(false);
            tokenAccount.setBalance(0L);
            tokenAccount.setBalanceTimestamp(consensusTimestamp);
            tokenAccount.setCreatedTimestamp(consensusTimestamp);
            tokenAccount.setTimestampLower(consensusTimestamp);
            tokenAccount.setTokenId(tokenId.getId());
            entityListener.onTokenAccount(tokenAccount);

            recordItem.addEntityId(tokenId);
        });
    }
}
