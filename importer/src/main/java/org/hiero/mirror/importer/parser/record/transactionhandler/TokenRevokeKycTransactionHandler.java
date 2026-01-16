// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
class TokenRevokeKycTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenRevokeKyc().getAccount());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENREVOKEKYC;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var tokenRevokeKycTransactionBody = recordItem.getTransactionBody().getTokenRevokeKyc();
        var tokenId = EntityId.of(tokenRevokeKycTransactionBody.getToken());

        var tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(transaction.getEntityId().getId());
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        tokenAccount.setTimestampLower(recordItem.getConsensusTimestamp());
        tokenAccount.setTokenId(tokenId.getId());
        entityListener.onTokenAccount(tokenAccount);
        recordItem.addEntityId(tokenId);
    }
}
