// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenSupplyTypeEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.converter.VersionConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.util.Version;

class TokenCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        var feeScheduleHandler = new TokenFeeScheduleUpdateTransactionHandler(entityListener, entityProperties);
        return new TokenCreateTransactionHandler(entityIdService, entityListener, entityProperties, feeScheduleHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var recordItem = recordItemBuilder.tokenCreate().build();
        return TransactionBody.newBuilder()
                .setTokenCreation(recordItem.getTransactionBody().getTokenCreation());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setTokenID(defaultEntityId.toTokenID());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @CsvSource(textBlock = """
                true, true, true, true, true, 0.49.0, true, FROZEN, REVOKED, UNFROZEN, GRANTED
                false, true, false, false, true, 0.50.0, true, UNFROZEN, NOT_APPLICABLE, UNFROZEN, NOT_APPLICABLE
                false, false, false, false, false, 0.49.0, true, NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE
                true, true, true, true, true, 0.47.0, false, FROZEN, REVOKED, UNFROZEN, GRANTED
                """)
    @ParameterizedTest
    void updateTransaction(
            boolean freezeDefault,
            boolean hasFreezeKey,
            boolean hasKycKey,
            boolean hasMetadata,
            boolean hasMetadataKey,
            @ConvertWith(VersionConverter.class) Version hapiVersion,
            boolean shouldProcessMetadata,
            TokenFreezeStatusEnum expectedTokenFreezeStatus,
            TokenKycStatusEnum expectedTokenKycStatus,
            TokenFreezeStatusEnum expectedTokenAccountFreezeStatus,
            TokenKycStatusEnum expectedTokenAccountKycStatus) {
        // Given
        var recordItem = recordItemBuilder
                .tokenCreate()
                .recordItem(r -> r.hapiVersion(hapiVersion))
                .transactionBody(b -> {
                    b.setFreezeDefault(freezeDefault);
                    if (!hasFreezeKey) {
                        b.clearFreezeKey();
                    }
                    if (!hasKycKey) {
                        b.clearKycKey();
                    }
                    if (!hasMetadata) {
                        b.setMetadata(ByteString.EMPTY);
                    }
                    if (!hasMetadataKey) {
                        b.clearMetadataKey();
                    }
                })
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        var customFeeCollector = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        var customFeeTokenId = EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId());
        var autoAssociation = recordItem.getTransactionRecord().getAutomaticTokenAssociations(0);
        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem,
                transaction,
                customFeeCollector,
                customFeeTokenId,
                EntityId.of(autoAssociation.getAccountId()));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        var expectedFreezeKey = hasFreezeKey ? transactionBody.getFreezeKey().toByteArray() : null;
        var expectedKycKey = hasKycKey ? transactionBody.getKycKey().toByteArray() : null;
        byte[] expectedMetadata = null;
        byte[] expectedMetadataKey = null;
        if (shouldProcessMetadata) {
            expectedMetadata = hasMetadata ? transactionBody.getMetadata().toByteArray() : EMPTY_BYTE_ARRAY;
            expectedMetadataKey =
                    hasMetadataKey ? transactionBody.getMetadataKey().toByteArray() : null;
        }
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(customFee.capture());
        verify(entityListener).onTokenAccount(tokenAccount.capture());
        assertThat(token.getValue())
                .returns(timestamp, Token::getCreatedTimestamp)
                .returns(transactionBody.getDecimals(), Token::getDecimals)
                .returns(transactionBody.getFeeScheduleKey().toByteArray(), Token::getFeeScheduleKey)
                .returns(transactionBody.getFreezeDefault(), Token::getFreezeDefault)
                .returns(expectedFreezeKey, Token::getFreezeKey)
                .returns(expectedTokenFreezeStatus, Token::getFreezeStatus)
                .returns(transactionBody.getInitialSupply(), Token::getInitialSupply)
                .returns(expectedKycKey, Token::getKycKey)
                .returns(expectedTokenKycStatus, Token::getKycStatus)
                .returns(transactionBody.getMaxSupply(), Token::getMaxSupply)
                .returns(expectedMetadata, Token::getMetadata)
                .returns(expectedMetadataKey, Token::getMetadataKey)
                .returns(Range.atLeast(timestamp), Token::getTimestampRange)
                .returns(transactionBody.getName(), Token::getName)
                .returns(transactionBody.getPauseKey().toByteArray(), Token::getPauseKey)
                .returns(TokenPauseStatusEnum.UNPAUSED, Token::getPauseStatus)
                .returns(transactionBody.getSupplyKey().toByteArray(), Token::getSupplyKey)
                .returns(TokenSupplyTypeEnum.fromId(transactionBody.getSupplyTypeValue()), Token::getSupplyType)
                .returns(transactionBody.getSymbol(), Token::getSymbol)
                .returns(transactionBody.getInitialSupply(), Token::getTotalSupply)
                .returns(EntityId.of(transactionBody.getTreasury()), Token::getTreasuryAccountId)
                .returns(TokenTypeEnum.fromId(transactionBody.getTokenTypeValue()), Token::getType)
                .returns(transaction.getEntityId().getId(), Token::getTokenId)
                .returns(transactionBody.getWipeKey().toByteArray(), Token::getWipeKey);

        assertThat(customFee.getValue())
                .returns(transaction.getConsensusTimestamp(), CustomFee::getTimestampLower)
                .returns(Range.atLeast(transaction.getConsensusTimestamp()), CustomFee::getTimestampRange)
                .returns(transaction.getEntityId().getId(), CustomFee::getEntityId)
                .returns(null, CustomFee::getFractionalFees)
                .returns(null, CustomFee::getRoyaltyFees);
        var listAssert =
                Assertions.assertThat(customFee.getValue().getFixedFees()).hasSize(1);
        listAssert
                .extracting(FixedFee::getAmount)
                .containsOnly(customFeeProto.getFixedFee().getAmount());
        listAssert
                .extracting(FixedFee::getCollectorAccountId)
                .containsOnly(EntityId.of(customFeeProto.getFeeCollectorAccountId()));
        listAssert
                .extracting(FixedFee::isAllCollectorsAreExempt)
                .containsOnly(customFeeProto.getAllCollectorsAreExempt());
        listAssert
                .extracting(FixedFee::getDenominatingTokenId)
                .containsOnly(EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId()));

        assertThat(tokenAccount.getValue())
                .returns(EntityId.of(autoAssociation.getAccountId()).getId(), TokenAccount::getAccountId)
                .returns(true, TokenAccount::getAssociated)
                .returns(false, TokenAccount::getAutomaticAssociation)
                .returns(timestamp, TokenAccount::getCreatedTimestamp)
                .returns(expectedTokenAccountFreezeStatus, TokenAccount::getFreezeStatus)
                .returns(expectedTokenAccountKycStatus, TokenAccount::getKycStatus)
                .returns(Range.atLeast(timestamp), TokenAccount::getTimestampRange)
                .returns(transaction.getEntityId().getId(), TokenAccount::getTokenId);

        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionMinimal() {
        // Given
        var recordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.clearAutoRenewAccount()
                        .clearAdminKey()
                        .clearAutoRenewPeriod()
                        .clearCustomFees()
                        .clearExpiry()
                        .clearFeeScheduleKey()
                        .clearFreezeKey()
                        .clearKycKey()
                        .clearMemo()
                        .clearMetadata()
                        .clearMetadataKey()
                        .clearPauseKey()
                        .clearSupplyKey()
                        .clearWipeKey())
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var treasuryId =
                EntityId.of(recordItem.getTransactionBody().getTokenCreation().getTreasury());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var expectedEntity = tokenId.toEntity().toBuilder()
                .deleted(false)
                .createdTimestamp(timestamp)
                .memo("")
                .timestampRange(Range.atLeast(timestamp))
                .type(getExpectedEntityIdType())
                .build();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(any());
        verify(entityListener).onTokenAccount(tokenAccount.capture());

        assertThat(token.getValue())
                .returns(null, Token::getFeeScheduleKey)
                .returns(null, Token::getFreezeKey)
                .returns(null, Token::getKycKey)
                .returns(EMPTY_BYTE_ARRAY, Token::getMetadata)
                .returns(null, Token::getMetadataKey)
                .returns(null, Token::getPauseKey)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(null, Token::getSupplyKey)
                .returns(null, Token::getWipeKey);

        assertThat(tokenAccount.getValue())
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, TokenAccount::getKycStatus);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, treasuryId));
    }

    @Test
    void updateTransactionNoAutoAssociations() {
        // Given
        var recordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> {
                    // 0.0.0 as denominating token id means fee is charged in the newly created token
                    var customFee = b.getCustomFees(0).toBuilder();
                    customFee.setFixedFee(
                            customFee.getFixedFeeBuilder().setDenominatingTokenId(TokenID.getDefaultInstance()));
                    b.setCustomFees(0, customFee);
                })
                .record(Builder::clearAutomaticTokenAssociations)
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        var customFeeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        transaction.setType(TransactionType.TOKENCREATION.getProtoId());
        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem, transaction, customFeeCollectorId, EntityId.of(transactionBody.getTreasury()));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(any());
        verify(entityListener).onCustomFee(any());
        verify(entityListener, times(2)).onTokenAccount(tokenAccount.capture());

        assertThat(tokenAccount.getAllValues())
                .asInstanceOf(InstanceOfAssertFactories.list(TokenAccount.class))
                .extracting(TokenAccount::getAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(customFeeProto.getFeeCollectorAccountId()).getId(),
                        EntityId.of(transactionBody.getTreasury()).getId());

        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenCreate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, never()).onToken(any());
        verify(entityListener, never()).onTokenAccount(any());
        verify(entityListener, never()).onCustomFee(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
