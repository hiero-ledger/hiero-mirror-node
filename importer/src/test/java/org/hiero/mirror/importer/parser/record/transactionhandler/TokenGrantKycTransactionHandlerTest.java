// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenGrantKycTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenGrantKycTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
                        .setAccount(defaultEntityId.toAccountID())
                        .setToken(
                                domainBuilder.entityNum(DEFAULT_ENTITY_NUM + 1).toTokenID())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.tokenGrantKyc().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var body = recordItem.getTransactionBody().getTokenGrantKyc();
        var accountId = EntityId.of(body.getAccount());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(accountId))
                .get();
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var tokenId = EntityId.of(body.getToken());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onTokenAccount(tokenAccount.capture());

        assertThat(tokenAccount.getValue())
                .returns(transaction.getEntityId().getId(), TokenAccount::getAccountId)
                .returns(true, TokenAccount::getAssociated)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)
                .returns(Range.atLeast(timestamp), TokenAccount::getTimestampRange)
                .returns(tokenId.getId(), TokenAccount::getTokenId);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, tokenId));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenGrantKyc().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
