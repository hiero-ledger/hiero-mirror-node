// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Consumer;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.util.Version;

class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final long DEFAULT_AUTO_RENEW_ACCOUNT_NUM = 2;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(defaultEntityId.toTokenID())
                        .setAdminKey(DEFAULT_KEY)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360))
                        .setKycKey(DEFAULT_KEY)
                        .setFreezeKey(DEFAULT_KEY)
                        .setMetadataKey(DEFAULT_KEY)
                        .setSymbol("SYMBOL")
                        .setTreasury(domainBuilder.entityNum(1).toAccountID())
                        .setAutoRenewAccount(domainBuilder
                                .entityNum(DEFAULT_AUTO_RENEW_ACCOUNT_NUM)
                                .toAccountID())
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(DEFAULT_KEY));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.49.0, true
            0.47.0, false
            """)
    void updateTransactionSuccessful(Version hapiVersion, boolean shouldProcessMetadata) {
        // Given
        var recordItem = recordItemBuilder
                .tokenUpdate()
                .recordItem(r -> r.hapiVersion(hapiVersion))
                .build();
        var body = recordItem.getTransactionBody().getTokenUpdate();
        var tokenId = EntityId.of(body.getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var autoRenewAccountId = EntityId.of(10L);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(autoRenewAccountId));
        var treasuryId = EntityId.of(body.getTreasury());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        var expectedMetadata =
                shouldProcessMetadata ? body.getMetadata().getValue().toByteArray() : null;
        var expectedMetadataKey = shouldProcessMetadata ? body.getMetadataKey().toByteArray() : null;
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(autoRenewAccountId.getId(), id));
        verify(entityListener).onToken(assertArg(t -> assertThat(t)
                .returns(body.getFeeScheduleKey().toByteArray(), Token::getFeeScheduleKey)
                .returns(body.getFreezeKey().toByteArray(), Token::getFreezeKey)
                .returns(body.getKycKey().toByteArray(), Token::getKycKey)
                .returns(Range.atLeast(timestamp), Token::getTimestampRange)
                .returns(body.getName(), Token::getName)
                .returns(expectedMetadata, Token::getMetadata)
                .returns(expectedMetadataKey, Token::getMetadataKey)
                .returns(body.getPauseKey().toByteArray(), Token::getPauseKey)
                .returns(body.getSupplyKey().toByteArray(), Token::getSupplyKey)
                .returns(body.getSymbol(), Token::getSymbol)
                .returns(EntityId.of(body.getTreasury()), Token::getTreasuryAccountId)
                .returns(tokenId.getId(), Token::getTokenId)
                .returns(body.getWipeKey().toByteArray(), Token::getWipeKey)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, autoRenewAccountId, treasuryId));
    }

    @Test
    void updateTransactionMinimal() {
        // Given
        var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clearAutoRenewAccount()
                        .clearAutoRenewPeriod()
                        .clearFeeScheduleKey()
                        .clearFreezeKey()
                        .clearKycKey()
                        .clearMetadata()
                        .clearMetadataKey()
                        .clearName()
                        .clearPauseKey()
                        .clearSupplyKey()
                        .clearSymbol()
                        .clearTreasury()
                        .clearWipeKey())
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(assertArg(t -> assertThat(t)
                .returns(null, Token::getFeeScheduleKey)
                .returns(null, Token::getFreezeKey)
                .returns(recordItem.getConsensusTimestamp(), Token::getTimestampLower)
                .returns(null, Token::getMetadata)
                .returns(null, Token::getMetadataKey)
                .returns(null, Token::getName)
                .returns(null, Token::getPauseKey)
                .returns(null, Token::getSupplyKey)
                .returns(null, Token::getSymbol)
                .returns(null, Token::getTreasuryAccountId)
                .returns(transaction.getEntityId().getId(), Token::getTokenId)
                .returns(null, Token::getWipeKey)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenUpdate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, never()).onToken(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccount = AccountID.newBuilder().setAlias(alias).build();
        var aliasAccountId = EntityId.of(10L);
        var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.setAutoRenewAccount(aliasAccount).clearTreasury())
                .build();
        var tokenId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(aliasAccountId.getId(), id));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, aliasAccountId));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionWithEmptyEntity(EntityId entityId) {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var aliasAccount = AccountID.newBuilder().setAlias(alias).build();
        var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.setAutoRenewAccount(aliasAccount).clearTreasury())
                .build();
        var tokenId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.ofNullable(entityId));
        var expectedId = entityId == null ? null : entityId.getId();

        transactionHandler.updateTransaction(transaction, recordItem);

        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(expectedId, id));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @SuppressWarnings("java:S6103")
    void assertTokenUpdate(long timestamp, EntityId tokenId, Consumer<Long> assertAutoRenewAccountId) {
        verify(entityListener).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(e -> assertAutoRenewAccountId.accept(e.getAutoRenewAccountId()))
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(tokenId.getId(), Entity::getId)
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(null, Entity::getMaxAutomaticTokenAssociations)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .returns(tokenId.getNum(), Entity::getNum)
                .returns(null, Entity::getProxyAccountId)
                .satisfies(e -> assertThat(e.getPublicKey()).isNotEmpty())
                .returns(tokenId.getRealm(), Entity::getRealm)
                .returns(tokenId.getShard(), Entity::getShard)
                .returns(EntityType.TOKEN, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)));
    }
}
