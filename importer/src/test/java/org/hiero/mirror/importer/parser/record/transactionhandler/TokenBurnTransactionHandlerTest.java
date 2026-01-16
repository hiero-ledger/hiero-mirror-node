// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenBurnTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenBurnTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenBurn(TokenBurnTransactionBody.newBuilder()
                        .setToken(defaultEntityId.toTokenID())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.tokenBurn().build();
        var transaction = domainBuilder.transaction().get();
        var token = ArgumentCaptor.forClass(Token.class);
        var nft = ArgumentCaptor.forClass(Nft.class);
        var transactionBody = recordItem.getTransactionBody().getTokenBurn();
        var receipt = recordItem.getTransactionRecord().getReceipt();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onNft(nft.capture());

        assertThat(token.getValue())
                .returns(transaction.getEntityId().getId(), t -> t.getTokenId())
                .returns(receipt.getNewTotalSupply(), Token::getTotalSupply);

        assertThat(nft.getValue())
                .returns(true, Nft::getDeleted)
                .returns(transactionBody.getSerialNumbers(0), AbstractNft::getSerialNumber)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), Nft::getTimestampRange)
                .returns(transaction.getEntityId().getId(), AbstractNft::getTokenId);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenBurn().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
