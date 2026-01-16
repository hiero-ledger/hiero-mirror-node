// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TokenWipeTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenWipeTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setAccount(
                                domainBuilder.entityNum(DEFAULT_ENTITY_NUM + 1).toAccountID())
                        .setToken(defaultEntityId.toTokenID())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransactionFungible() {
        var accountId = recordItemBuilder.accountId();
        var resolved = Optional.of(EntityId.of(accountId));
        testUpdateTransactionFungible(accountId, resolved);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionFungibleWithAliasAccount(boolean isResolvable) {
        var accountId = recordItemBuilder.accountId();
        var aliasAccountId =
                AccountID.newBuilder().setAlias(recordItemBuilder.bytes(32)).build();
        var resolved = isResolvable ? Optional.of(EntityId.of(accountId)) : Optional.<EntityId>empty();
        testUpdateTransactionFungible(aliasAccountId, resolved);
    }

    @Test
    void updateTransactionNonFungible() {
        // Given
        var recordItem = recordItemBuilder.tokenWipe(NON_FUNGIBLE_UNIQUE).build();
        var body = recordItem.getTransactionBody().getTokenWipe();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(body.getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var nft = ArgumentCaptor.forClass(Nft.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var protoAccountId = body.getAccount();
        var accountId = EntityId.of(protoAccountId);
        when(entityIdService.lookup(protoAccountId)).thenReturn(Optional.of(accountId));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityIdService).lookup(protoAccountId);
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onNft(nft.capture());

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);

        assertThat(nft.getValue())
                .returns(true, Nft::getDeleted)
                .returns(body.getSerialNumbers(0), Nft::getSerialNumber)
                .returns(Range.atLeast(timestamp), Nft::getTimestampRange)
                .returns(tokenId.getId(), Nft::getTokenId);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, accountId));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenWipe().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityIdService);
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    void testUpdateTransactionFungible(AccountID accountId, Optional<EntityId> resolved) {
        // Given
        var recordItem = recordItemBuilder
                .tokenWipe(FUNGIBLE_COMMON)
                .transactionBody(b -> b.setAccount(accountId))
                .build();
        var body = recordItem.getTransactionBody().getTokenWipe();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(body.getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var token = ArgumentCaptor.forClass(Token.class);
        when(entityIdService.lookup(accountId)).thenReturn(resolved);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityIdService).lookup(accountId);
        verify(entityListener).onToken(token.capture());
        verifyNoMoreInteractions(entityListener);

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);
        var expected = resolved.isPresent()
                ? getExpectedEntityTransactions(recordItem, transaction, resolved.get())
                : getExpectedEntityTransactions(recordItem, transaction);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
