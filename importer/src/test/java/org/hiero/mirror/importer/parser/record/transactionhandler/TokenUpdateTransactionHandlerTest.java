// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.hiero.mirror.importer.repository.TokenRepository.Treasury;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.util.Version;

class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final long DEFAULT_AUTO_RENEW_ACCOUNT_NUM = 2;

    private final ParserContext parserContext = new ParserContext();

    @Mock
    private TokenRepository tokenRepository;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionHandler(
                entityIdService, entityListener, entityProperties, parserContext, tokenRepository);
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
    @MethodSource("treasuryKeys")
    void updateTreasuryStatuses(String freezeKeyType, String kycKeyType) {
        boolean freezeKey = freezeKeyType.equals("VALID");
        boolean kycKey = kycKeyType.equals("VALID");
        var original = Token.builder()
                .treasuryAccountId(domainBuilder.entityId())
                .freezeKey(keyBytes(freezeKeyType))
                .kycKey(keyBytes(kycKeyType))
                .build();
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clearFreezeKey().clearKycKey())
                .build();
        var body = item.getTransactionBody().getTokenUpdate();
        var tokenId = EntityId.of(body.getToken());
        when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                .thenReturn(
                        new Treasury(original.getTreasuryAccountId(), original.getFreezeKey(), original.getKycKey()));

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        if (!freezeKey && !kycKey) {
            verify(entityListener, never()).onTokenAccount(any());
        } else {
            verify(entityListener)
                    .onTokenAccount(assertArg(t -> assertThat(t)
                            .returns(tokenId.getId(), TokenAccount::getTokenId)
                            .returns(EntityId.of(body.getTreasury()).getId(), TokenAccount::getAccountId)
                            .returns(true, TokenAccount::getAssociated)
                            .returns(item.getConsensusTimestamp(), TokenAccount::getTimestampLower)
                            .returns(freezeKey ? TokenFreezeStatusEnum.UNFROZEN : null, TokenAccount::getFreezeStatus)
                            .returns(kycKey ? TokenKycStatusEnum.GRANTED : null, TokenAccount::getKycStatus)
                            .returns(null, TokenAccount::getCreatedTimestamp)
                            .returns(null, TokenAccount::getAutomaticAssociation)
                            .returns(0L, TokenAccount::getBalance)
                            .returns(null, TokenAccount::getBalanceTimestamp)));
        }
    }

    private static Stream<Arguments> treasuryKeys() {
        return Stream.of("NULL", "EMPTY", "REMOVED", "VALID")
                .flatMap(
                        freeze -> Stream.of("NULL", "EMPTY", "REMOVED", "VALID").map(kyc -> Arguments.of(freeze, kyc)));
    }

    private static byte[] keyBytes(String type) {
        return switch (type) {
            case "NULL" -> null;
            case "EMPTY" -> new byte[0];
            case "REMOVED" ->
                Key.newBuilder()
                        .setKeyList(KeyList.getDefaultInstance())
                        .build()
                        .toByteArray();
            default -> DEFAULT_KEY.toByteArray();
        };
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTreasuryUsesPendingToken(boolean createdInBatch) {
        var item = recordItemBuilder.tokenUpdate().build();
        var body = item.getTransactionBody().getTokenUpdate();
        var tokenId = EntityId.of(body.getToken());
        // A pending removal must override the persisted key, while omitted fields still inherit persisted values.
        var pending = Token.builder()
                .treasuryAccountId(domainBuilder.entityId())
                .createdTimestamp(createdInBatch ? 1L : null)
                .freezeKey(Key.newBuilder()
                        .setKeyList(KeyList.getDefaultInstance())
                        .build()
                        .toByteArray())
                .build();
        parserContext.add(pending, tokenId.getId());
        if (!createdInBatch) {
            when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                    .thenReturn(new Treasury(
                            EntityId.of(body.getTreasury()), DEFAULT_KEY.toByteArray(), DEFAULT_KEY.toByteArray()));
        }

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        if (createdInBatch) {
            verify(entityListener, never()).onTokenAccount(any());
        } else {
            verify(entityListener)
                    .onTokenAccount(assertArg(t -> assertThat(t)
                            .returns(null, TokenAccount::getFreezeStatus)
                            .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)));
        }
        // Current update keys must not be used, and resolving the original must not mutate queued snapshots.
        assertThat(pending.getKycKey()).isNull();
        if (createdInBatch) {
            verifyNoInteractions(tokenRepository);
        }
    }

    @Test
    void updateTreasuryAfterPendingSupplyChange() {
        var item = recordItemBuilder.tokenUpdate().build();
        var tokenId = EntityId.of(item.getTransactionBody().getTokenUpdate().getToken());
        parserContext.add(Token.builder().totalSupply(100L).build(), tokenId.getId());
        when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                .thenReturn(
                        new Treasury(domainBuilder.entityId(), DEFAULT_KEY.toByteArray(), DEFAULT_KEY.toByteArray()));

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        verify(entityListener)
                .onTokenAccount(assertArg(t -> assertThat(t)
                        .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                        .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)));
    }

    @Test
    void updateUnchangedTreasury() {
        var item = recordItemBuilder.tokenUpdate().build();
        var body = item.getTransactionBody().getTokenUpdate();
        var tokenId = EntityId.of(body.getToken());
        when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                .thenReturn(new Treasury(
                        EntityId.of(body.getTreasury()), DEFAULT_KEY.toByteArray(), DEFAULT_KEY.toByteArray()));

        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), item);

        verify(entityListener, never()).onTokenAccount(any());
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

        verifyNoInteractions(tokenRepository);
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
        verifyNoInteractions(tokenRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FAIL_INVALID", "INVALID_SIGNATURE"})
    void failedUpdateAutomaticAssociations(ResponseCodeEnum status) {
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var alias = AccountID.newBuilder()
                .setAlias(DomainUtils.fromBytes(domainBuilder.key()))
                .build();
        var item = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(tokenId.toTokenID()).setTreasury(alias))
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
        verifyNoInteractions(tokenRepository, entityIdService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void automaticTreasuryAssociationWithDifferentProto(boolean aliasTreasury) {
        var tokenId = domainBuilder.entityId();
        var treasury = domainBuilder.entityId();
        var otherAccount = domainBuilder.entityId();
        var otherToken = domainBuilder.entityId();
        var bodyTreasury = aliasTreasury
                ? AccountID.newBuilder()
                        .setAlias(DomainUtils.fromBytes(domainBuilder.key()))
                        .build()
                : treasury.toAccountID();
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
        if (aliasTreasury) {
            when(entityIdService.lookup(bodyTreasury)).thenReturn(Optional.of(treasury));
        }
        when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                .thenReturn(
                        new Treasury(domainBuilder.entityId(), DEFAULT_KEY.toByteArray(), DEFAULT_KEY.toByteArray()));

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

    @Test
    void updateTreasuryAlias() {
        // Given
        final var alias = DomainUtils.fromBytes(domainBuilder.key());
        final var aliasAccount = AccountID.newBuilder().setAlias(alias).build();
        final var treasury = domainBuilder.entityId();
        final var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clearAutoRenewAccount().setTreasury(aliasAccount))
                .build();
        final var tokenId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(treasury));
        when(tokenRepository.findTreasuryByTokenId(tokenId.getId()))
                .thenReturn(
                        new Treasury(domainBuilder.entityId(), DEFAULT_KEY.toByteArray(), DEFAULT_KEY.toByteArray()));

        // When
        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), recordItem);

        // Then
        verify(entityListener).onToken(assertArg(t -> assertThat(t).returns(treasury, Token::getTreasuryAccountId)));
        verify(entityListener)
                .onTokenAccount(assertArg(t -> assertThat(t)
                        .returns(treasury.getId(), TokenAccount::getAccountId)
                        .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                        .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)));
        assertThat(recordItem.getEntityTransactions()).containsKey(treasury.getId());
    }

    @Test
    void updateTreasuryAliasNotFound() {
        // Given
        final var alias = DomainUtils.fromBytes(domainBuilder.key());
        final var aliasAccount = AccountID.newBuilder().setAlias(alias).build();
        final var recordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clearAutoRenewAccount().setTreasury(aliasAccount))
                .build();
        final var tokenId =
                EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.empty());

        // When
        transactionHandler.updateTransaction(
                domainBuilder.transaction().customize(t -> t.entityId(tokenId)).get(), recordItem);

        // Then
        verify(entityListener).onToken(assertArg(t -> assertThat(t).returns(null, Token::getTreasuryAccountId)));
        verify(entityListener, never()).onTokenAccount(any());
        verifyNoInteractions(tokenRepository);
        assertThat(recordItem.getEntityTransactions()).doesNotContainKey(EntityId.EMPTY.getId());
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
