// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.StringValue;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Consumer;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

final class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

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
        verify(entityListener)
                .onToken(assertArg(t -> assertThat(t)
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
        verify(entityListener)
                .onToken(assertArg(t -> assertThat(t)
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

    @Test
    void updateTransactionEmptyTreasury() {
        // Given
        final var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.setTreasury(AccountID.getDefaultInstance()))
                .build();
        final var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(assertArg(t -> assertThat(t).returns(null, Token::getTreasuryAccountId)));
        assertThat(recordItem.getEntityTransactions()).doesNotContainKey(EntityId.EMPTY.getId());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, ,
            false, '', ''
            false, ' ', ' '
            true, ,
            true, '',
            true, ' ',
            """)
    void updateTransactionWithMemo(boolean fromPrecompile, String memo, String expected) {
        // Given
        final var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> {
                    if (memo == null) {
                        b.clearMemo();
                    } else {
                        b.setMemo(StringValue.of(memo));
                    }
                })
                .record(r -> {
                    if (fromPrecompile) {
                        r.setContractCallResult(ContractFunctionResult.newBuilder()
                                .setContractID(systemEntity
                                        .hederaTokenServiceContract()
                                        .toContractID())
                                .build());
                    }
                })
                .build();
        final var tokenEntityId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(tokenEntityId))
                .get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(assertArg(t -> assertThat(t.getTokenId()).isEqualTo(tokenEntityId.getId())));
        verify(entityListener).onEntity(assertArg(e -> assertThat(e.getMemo()).isEqualTo(expected)));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, true
            true, false
            false, true
            false, false
            """)
    void updateTreasuryStatuses(boolean freezeKey, boolean kycKey) {
        // The statuses are set regardless of the token's keys, readers show NOT_APPLICABLE for a token without the key
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> {
                    if (!freezeKey) {
                        b.clearFreezeKey();
                    }
                    if (!kycKey) {
                        b.clearKycKey();
                    }
                })
                .build();
        var body = item.getTransactionBody().getTokenUpdate();
        var tokenId = EntityId.of(body.getToken());

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        verify(entityListener)
                .onTokenAccount(assertArg(t -> assertThat(t)
                        .returns(tokenId.getId(), TokenAccount::getTokenId)
                        .returns(EntityId.of(body.getTreasury()).getId(), TokenAccount::getAccountId)
                        .returns(true, TokenAccount::getAssociated)
                        .returns(item.getConsensusTimestamp(), TokenAccount::getTimestampLower)
                        .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                        .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)
                        .returns(null, TokenAccount::getCreatedTimestamp)
                        .returns(null, TokenAccount::getAutomaticAssociation)
                        .returns(0L, TokenAccount::getBalance)
                        .returns(null, TokenAccount::getBalanceTimestamp)));
    }

    @Test
    void updateTreasuryFailed() {
        var item = recordItemBuilder
                .tokenUpdate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_SIGNATURE))
                .build();
        var tokenId = EntityId.of(item.getTransactionBody().getTokenUpdate().getToken());

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        verify(entityListener, never()).onTokenAccount(any());
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void automaticAssociationsWhenEntityUpdateSkipped(EntityId entityId) {
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(tokenId.toTokenID()).setTreasury(treasury.toAccountID()))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                        .setAccountId(treasury.toAccountID())
                        .setTokenId(tokenId.toTokenID())))
                .build();

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(entityId)).get(), item);

        verify(entityListener)
                .onTokenAccount(assertArg(t -> assertThat(t)
                        .returns(tokenId.getId(), TokenAccount::getTokenId)
                        .returns(treasury.getId(), TokenAccount::getAccountId)
                        .returns(true, TokenAccount::getAutomaticAssociation)
                        .returns(item.getConsensusTimestamp(), TokenAccount::getCreatedTimestamp)));
        verify(entityListener, never()).onToken(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FAIL_INVALID", "INVALID_SIGNATURE"})
    void failedUpdateAutomaticAssociations(ResponseCodeEnum status) {
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(tokenId.toTokenID()).setTreasury(treasury.toAccountID()))
                .receipt(r -> r.setStatus(status))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                        .setAccountId(treasury.toAccountID())
                        .setTokenId(tokenId.toTokenID())))
                .build();

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        if (status == ResponseCodeEnum.FAIL_INVALID) {
            verify(entityListener)
                    .onTokenAccount(assertArg(t -> assertThat(t)
                            .returns(treasury.getId(), TokenAccount::getAccountId)
                            .returns(true, TokenAccount::getAutomaticAssociation)
                            .returns(null, TokenAccount::getFreezeStatus)
                            .returns(null, TokenAccount::getKycStatus)));
        } else {
            verify(entityListener, never()).onTokenAccount(any());
        }
    }

    @Test
    void automaticTreasuryAssociationWithDifferentProto() {
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var otherAccount = domainBuilder.entityId();
        var otherToken = domainBuilder.entityId();
        var bodyTreasury = treasury.toAccountID();
        // Unknown fields change protobuf equality without changing the account identity.
        var recordTreasury = treasury.toAccountID().toBuilder()
                .setUnknownFields(UnknownFieldSet.newBuilder()
                        .addField(
                                100,
                                UnknownFieldSet.Field.newBuilder().addVarint(1).build())
                        .build())
                .build();
        assertThat(bodyTreasury).isNotEqualTo(recordTreasury);
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(tokenId.toTokenID()).setTreasury(bodyTreasury))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(recordTreasury)
                                .setTokenId(tokenId.toTokenID()))
                        .addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(otherAccount.toAccountID())
                                .setTokenId(otherToken.toTokenID())))
                .build();
        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        var captor = ArgumentCaptor.forClass(TokenAccount.class);
        verify(entityListener, times(2)).onTokenAccount(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(t -> t.getAccountId() == treasury.getId())
                .singleElement()
                .returns(tokenId.getId(), TokenAccount::getTokenId)
                .returns(true, TokenAccount::getAutomaticAssociation)
                .returns(item.getConsensusTimestamp(), TokenAccount::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus);
        assertThat(captor.getAllValues())
                .filteredOn(t -> t.getAccountId() == otherAccount.getId())
                .singleElement()
                .returns(otherToken.getId(), TokenAccount::getTokenId)
                .returns(true, TokenAccount::getAutomaticAssociation)
                .returns(null, TokenAccount::getFreezeStatus)
                .returns(null, TokenAccount::getKycStatus);
        verify(entityListener).onToken(assertArg(t -> assertThat(t).returns(treasury, Token::getTreasuryAccountId)));
    }

    @SuppressWarnings("java:S6103")
    void assertTokenUpdate(long timestamp, EntityId tokenId, Consumer<Long> assertAutoRenewAccountId) {
        verify(entityListener)
                .onEntity(assertArg(t -> assertThat(t)
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
