// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.pbj.runtime.OneOf;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.RoyaltyFee;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.CustomFeeRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.Utils;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenReadableKVStateTest {

    private static final TokenID TOKEN_ID =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1252L).build();
    private static final TokenID TOKEN_ID_FOR_NEGATIVE_TEST =
            TokenID.newBuilder().shardNum(0L).realmNum(0L).tokenNum(1253L).build();
    private static final Long TOKEN_ENCODED_ID = EntityId.of(
                    TOKEN_ID.shardNum(), TOKEN_ID.realmNum(), TOKEN_ID.tokenNum())
            .getId();
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final TokenID DENOMINATING_TOKEN_ID =
            TokenID.newBuilder().shardNum(11L).realmNum(12L).tokenNum(13L).build();

    @AutoClose
    private static final MockedStatic<ContractCallContext> contextMockedStatic = mockStatic(ContractCallContext.class);

    private final EntityId collectorId = EntityId.of(1L, 2L, 3L);
    private final AccountID collectorAccountId = new AccountID(
            collectorId.getShard(),
            collectorId.getRealm(),
            new OneOf<>(AccountOneOfType.ACCOUNT_NUM, collectorId.getNum()));
    private final EntityId denominatingTokenId = EntityId.of(11L, 12L, 13L);

    private org.hiero.mirror.common.domain.token.Token databaseToken;
    private CustomFee customFee;

    @InjectMocks
    private TokenReadableKVState tokenReadableKVState;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock(strictness = Strictness.LENIENT)
    private SystemEntity systemEntity;

    private DomainBuilder domainBuilder;
    private Entity entity;
    private Entity account;
    private Entity collector;
    private EntityId treasuryAccountId;

    @Spy
    private ContractCallContext contractCallContext;

    @BeforeEach
    void setup() {
        var systemEntity = new SystemEntity(CommonProperties.getInstance());
        treasuryAccountId = systemEntity.treasuryAccount();
        domainBuilder = new DomainBuilder();
        entity = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(TOKEN_ID.tokenNum());
                    e.num(TOKEN_ID.tokenNum());
                    e.type(EntityType.TOKEN);
                })
                .get();
        account = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(TOKEN_ID_FOR_NEGATIVE_TEST.tokenNum());
                    e.num(TOKEN_ID_FOR_NEGATIVE_TEST.tokenNum());
                    e.type(EntityType.ACCOUNT);
                })
                .get();
        collector = domainBuilder
                .entity()
                .customize(e -> {
                    e.id(collectorId.getId());
                    e.shard(collectorId.getShard());
                    e.realm(collectorId.getRealm());
                    e.num(collectorId.getNum());
                    e.type(EntityType.ACCOUNT);
                    e.evmAddress(null);
                    e.alias(null);
                })
                .get();
        customFee = new CustomFee();

        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
        when(this.systemEntity.treasuryAccount()).thenReturn(treasuryAccountId);
    }

    @Test
    void getTokenMappedValues() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token)
                .returns(TOKEN_ID, Token::tokenId)
                .returns(
                        com.hedera.hapi.node.base.TokenType.valueOf(
                                databaseToken.getType().name()),
                        Token::tokenType)
                .returns(
                        com.hedera.hapi.node.base.TokenSupplyType.valueOf(
                                databaseToken.getSupplyType().name()),
                        Token::supplyType)
                .returns(databaseToken.getMaxSupply(), Token::maxSupply)
                .returns(databaseToken.getFreezeDefault(), Token::accountsFrozenByDefault)
                .returns(false, Token::deleted)
                .returns(false, Token::paused)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Token::expirationSecond)
                .returns(entity.getMemo(), Token::memo)
                .returns(databaseToken.getName(), Token::name)
                .returns(databaseToken.getSymbol(), Token::symbol)
                .returns(databaseToken.getDecimals(), Token::decimals)
                .returns(entity.getAutoRenewPeriod(), Token::autoRenewSeconds)
                .returns(
                        databaseToken.getKycStatus() == TokenKycStatusEnum.GRANTED, Token::accountsKycGrantedByDefault);

        assertThat(token.totalSupplySupplier().get()).isEqualTo(databaseToken.getTotalSupply());
    }

    @Test
    void getTokenReturnsNullWhenIdIsAnAccount() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID_FOR_NEGATIVE_TEST, Optional.empty()))
                .thenReturn(Optional.ofNullable(account));
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID_FOR_NEGATIVE_TEST))
                .isNull();
    }

    @Test
    void getTokenMappedValuesHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token)
                .returns(TOKEN_ID, Token::tokenId)
                .returns(TokenType.valueOf(databaseToken.getType().name()), Token::tokenType)
                .returns(TokenSupplyType.valueOf(databaseToken.getSupplyType().name()), Token::supplyType)
                .returns(databaseToken.getMaxSupply(), Token::maxSupply)
                .returns(databaseToken.getFreezeDefault(), Token::accountsFrozenByDefault)
                .returns(false, Token::deleted)
                .returns(false, Token::paused)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Token::expirationSecond)
                .returns(entity.getMemo(), Token::memo)
                .returns(databaseToken.getName(), Token::name)
                .returns(databaseToken.getSymbol(), Token::symbol)
                .returns(databaseToken.getDecimals(), Token::decimals)
                .returns(entity.getAutoRenewPeriod(), Token::autoRenewSeconds);

        assertThat(token.totalSupplySupplier().get()).isZero();
    }

    @Test
    void getCustomFees() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.customFeesSupplier().get()).isEqualTo(Collections.emptyList()));
        verify(customFeeRepository).findById(TOKEN_ID.tokenNum());
    }

    @Test
    void getCustomFeesHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.customFeesSupplier().get()).isEqualTo(Collections.emptyList()));
    }

    @Test
    void getPartialTreasuryAccount() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        final var entityId = EntityId.of(11L, 12L, 13L);
        setupToken(Optional.empty(), entityId);
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        final var expectedTreasuryAccountId = toAccountId(entityId);

        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);

        assertThat(token.treasuryAccountId()).isEqualTo(expectedTreasuryAccountId);
    }

    @Test
    void getPartialTreasuryAccountHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        final var entityId = EntityId.of(11L, 12L, 13L);
        setupToken(timestamp, entityId);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        final var expectedTreasuryAccountId = toAccountId(entityId);

        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token.treasuryAccountId()).isEqualTo(expectedTreasuryAccountId);
    }

    @Test
    void getAutoRenewAccount() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        databaseToken.setTreasuryAccountId(null);
        entity.setAutoRenewAccountId(10L);
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        final var autoRenewAccountId = toAccountId(0L, 0L, 10L);
        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token.autoRenewAccountId()).isEqualTo(autoRenewAccountId);
    }

    @Test
    void getAutoRenewAccountHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        databaseToken.setTreasuryAccountId(null);

        entity.setAutoRenewAccountId(10L);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        final var autoRenewAccountId = toAccountId(0L, 0L, 10L);
        Token token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        assertThat(token.autoRenewAccountId()).isEqualTo(autoRenewAccountId);
    }

    @Test
    void getTotalSupplyHistoricalFungible() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        final var treasuryId = EntityId.of(123L);
        final var totalSupply = 10L;
        final var historicalSupply = 9L;
        databaseToken.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        databaseToken.setTotalSupply(totalSupply);
        databaseToken.setTreasuryAccountId(treasuryId);

        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(
                        databaseToken.getTokenId(), timestamp.get(), treasuryAccountId.getId()))
                .thenReturn(historicalSupply);

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(
                        token -> assertThat(token.totalSupplySupplier().get()).isEqualTo(historicalSupply));

        verify(tokenRepository)
                .findFungibleTotalSupplyByTokenIdAndTimestamp(
                        databaseToken.getTokenId(), timestamp.get(), treasuryAccountId.getId());
    }

    @Test
    void getTotalSupplyHistoricalNonFungible() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        final var treasuryId = EntityId.of(123L);
        final var totalSupply = 10L;
        final var historicalSupply = 9L;
        databaseToken.setType(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        databaseToken.setTotalSupply(totalSupply);
        databaseToken.setTreasuryAccountId(treasuryId);

        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(nftRepository.findNftTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get()))
                .thenReturn(historicalSupply);

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(
                        token -> assertThat(token.totalSupplySupplier().get()).isEqualTo(historicalSupply));

        verify(nftRepository).findNftTotalSupplyByTokenIdAndTimestamp(databaseToken.getTokenId(), timestamp.get());
    }

    @Test
    void getAccountsKycGrantedByDefault() {
        setupToken(Optional.empty());
        databaseToken.setKycStatus(TokenKycStatusEnum.GRANTED);

        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(
                        token -> assertThat(token.accountsKycGrantedByDefault()).isTrue());
    }

    @Test
    void getNullOnMissingToken() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.empty());
        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void getNullOnMissingTokenDHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.empty());
        assertThat(tokenReadableKVState.get(TOKEN_ID)).isNull();
    }

    @Test
    void getTokenKeysValues() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).satisfies(token -> assertThat(token)
                .returns(Utils.parseKey(entity.getKey()), Token::adminKey)
                .returns(Utils.parseKey(databaseToken.getKycKey()), Token::kycKey)
                .returns(Utils.parseKey(databaseToken.getPauseKey()), Token::pauseKey)
                .returns(Utils.parseKey(databaseToken.getFreezeKey()), Token::freezeKey)
                .returns(Utils.parseKey(databaseToken.getWipeKey()), Token::wipeKey)
                .returns(Utils.parseKey(databaseToken.getSupplyKey()), Token::supplyKey)
                .returns(Utils.parseKey(databaseToken.getFeeScheduleKey()), Token::feeScheduleKey));
    }

    @Test
    void getTokenKeysValuesHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).satisfies(token -> assertThat(token)
                .returns(Utils.parseKey(entity.getKey()), com.hedera.hapi.node.state.token.Token::adminKey)
                .returns(Utils.parseKey(databaseToken.getKycKey()), Token::kycKey)
                .returns(Utils.parseKey(databaseToken.getPauseKey()), Token::pauseKey)
                .returns(Utils.parseKey(databaseToken.getFreezeKey()), Token::freezeKey)
                .returns(Utils.parseKey(databaseToken.getWipeKey()), Token::wipeKey)
                .returns(Utils.parseKey(databaseToken.getSupplyKey()), Token::supplyKey)
                .returns(Utils.parseKey(databaseToken.getFeeScheduleKey()), Token::feeScheduleKey));
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFound() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void getTokenEmptyWhenDatabaseTokenNotFoundHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        when(tokenRepository.findByTokenIdAndTimestamp(entity.getId(), timestamp.get()))
                .thenReturn(Optional.empty());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID)).isNull();
    }

    @Test
    void keyIsNullIfNotParsable() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());

        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        databaseToken.setKycKey("wrOng".getBytes());

        assertThat(tokenReadableKVState.readFromDataSource(TOKEN_ID))
                .satisfies(token -> assertThat(token.kycKey()).isNull());
    }

    @Test
    void royaltyFee() {
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.fractionalFee()).isNull();
                var resultRoyaltyFee = fee.royaltyFee();
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().amount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().denominatingTokenId())
                        .isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void royaltyFeeHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fallbackFeeBuilder = FallbackFee.builder().denominatingTokenId(denominatingTokenId);
        var fallbackFee = fallbackFeeBuilder.amount(20L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder().collectorAccountId(collectorId);
        var royaltyFee = royaltyFeeBuilder
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder.numerator(16L).denominator(11L).build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.fractionalFee()).isNull();
                var resultRoyaltyFee = fee.royaltyFee();
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultRoyaltyFee.exchangeValueFraction().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().amount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                Assertions.assertThat(resultRoyaltyFee.fallbackFee().denominatingTokenId())
                        .isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void royaltyFeeNoFallback() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var fee = results.get(0);
        var resultFee = fee.royaltyFee();
        assertEquals(
                royaltyFee.getNumerator(), resultFee.exchangeValueFraction().numerator());
        assertEquals(
                royaltyFee.getDenominator(), resultFee.exchangeValueFraction().denominator());
        assertEquals(
                0L, resultFee.fallbackFee() != null ? resultFee.fallbackFee().amount() : 0L);
        assertEquals(collectorAccountId, fee.feeCollectorAccountId());
        assertEquals(
                TokenID.DEFAULT,
                resultFee.fallbackFee() != null ? resultFee.fallbackFee().denominatingTokenId() : TokenID.DEFAULT);
    }

    @Test
    void royaltyFeeNoFallbackHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var royaltyFee = RoyaltyFee.builder()
                .numerator(15L)
                .denominator(10L)
                .fallbackFee(null)
                .collectorAccountId(collectorId)
                .build();
        var royaltyFees = List.of(royaltyFee);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var fee = results.get(0);
        var resultFee = fee.royaltyFee();
        assertEquals(
                royaltyFee.getNumerator(), resultFee.exchangeValueFraction().numerator());
        assertEquals(
                royaltyFee.getDenominator(), resultFee.exchangeValueFraction().denominator());
        assertEquals(
                0L, resultFee.fallbackFee() != null ? resultFee.fallbackFee().amount() : 0L);
        assertEquals(collectorAccountId, fee.feeCollectorAccountId());
        assertEquals(
                TokenID.DEFAULT,
                resultFee.fallbackFee() != null ? resultFee.fallbackFee().denominatingTokenId() : TokenID.DEFAULT);
    }

    @Test
    void fractionFee() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFractionalFee = fee.fractionalFee();
                Assertions.assertThat(resultFractionalFee.fractionalAmount().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultFractionalFee.fractionalAmount().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultFractionalFee.maximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                Assertions.assertThat(resultFractionalFee.minimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                Assertions.assertThat(resultFractionalFee.netOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fractionFeeHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .numerator(20L)
                .denominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .numerator(21L)
                .denominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fixedFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFractionalFee = fee.fractionalFee();
                Assertions.assertThat(resultFractionalFee.fractionalAmount().numerator())
                        .isEqualTo(domainFee.getNumerator());
                Assertions.assertThat(resultFractionalFee.fractionalAmount().denominator())
                        .isEqualTo(domainFee.getDenominator());
                Assertions.assertThat(resultFractionalFee.maximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                Assertions.assertThat(resultFractionalFee.minimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                Assertions.assertThat(resultFractionalFee.netOfTransfers()).isEqualTo(domainFee.isNetOfTransfers());
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fixedFee() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, Optional.empty())).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fractionalFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFixedFee = fee.fixedFee();
                Assertions.assertThat(resultFixedFee.amount()).isEqualTo(domainFee.getAmount());
                Assertions.assertThat(resultFixedFee.denominatingTokenId()).isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(resultFixedFee.hasDenominatingTokenId()).isTrue();
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void fixedFeeHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(customFee));
        when(commonEntityAccessor.get(collectorId, timestamp)).thenReturn(Optional.of(collector));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        var listAssert = Assertions.assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                Assertions.assertThat(fee.fractionalFee()).isNull();
                Assertions.assertThat(fee.royaltyFee()).isNull();
                var resultFixedFee = fee.fixedFee();
                Assertions.assertThat(resultFixedFee.amount()).isEqualTo(domainFee.getAmount());
                Assertions.assertThat(resultFixedFee.denominatingTokenId()).isEqualTo(DENOMINATING_TOKEN_ID);
                Assertions.assertThat(resultFixedFee.hasDenominatingTokenId()).isTrue();
                Assertions.assertThat(fee.feeCollectorAccountId()).isEqualTo(collectorAccountId);
            });
        }
    }

    @Test
    void mapOnlyFeesWithCollectorAccountId() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        setupToken(Optional.empty());
        when(commonEntityAccessor.get(TOKEN_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));

        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findById(TOKEN_ENCODED_ID)).thenReturn(Optional.of(noCollectorCustomFee));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void mapOnlyFeesWithCollectorAccountIdHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        setupToken(timestamp);
        when(commonEntityAccessor.get(TOKEN_ID, timestamp)).thenReturn(Optional.ofNullable(entity));

        final var noCollectorCustomFee = new CustomFee();
        when(customFeeRepository.findByTokenIdAndTimestamp(TOKEN_ENCODED_ID, timestamp.get()))
                .thenReturn(Optional.of(noCollectorCustomFee));

        final var token = tokenReadableKVState.readFromDataSource(TOKEN_ID);
        var results = token.customFeesSupplier().get();
        Assertions.assertThat(results).isEmpty();
    }

    @Test
    void sizeIsAlwaysEmpty() {
        assertThat(tokenReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(tokenReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    private void setupToken(Optional<Long> timestamp) {
        setupToken(timestamp, mock(EntityId.class));
    }

    private void setupToken(Optional<Long> timestamp, EntityId treasuryAccountId) {
        databaseToken =
                domainBuilder.token().customize(t -> t.tokenId(entity.getId())).get();

        databaseToken.setTreasuryAccountId(treasuryAccountId);

        if (timestamp.isPresent()) {
            when(tokenRepository.findByTokenIdAndTimestamp(entity.getId(), timestamp.get()))
                    .thenReturn(Optional.ofNullable(databaseToken));
        } else {
            when(tokenRepository.findById(any())).thenReturn(Optional.ofNullable(databaseToken));
        }
    }
}
