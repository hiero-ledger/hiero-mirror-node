// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;
import static org.hiero.mirror.common.domain.token.NftTransfer.WILDCARD_SERIAL_NUMBER;
import static org.hiero.mirror.importer.TestUtils.toEntityTransactions;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import com.google.common.primitives.Longs;
import com.google.protobuf.BytesValue;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenReference;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.AbstractNft.Id;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.FallbackFee;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.NftRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenAirdropRepository;
import org.hiero.mirror.importer.repository.TokenAllowanceRepository;
import org.hiero.mirror.importer.repository.TokenHistoryRepository;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.hiero.mirror.importer.repository.TokenTransferRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
class EntityRecordItemListenerTokenTest extends AbstractEntityRecordItemListenerTest {

    private static final long ASSOCIATE_TIMESTAMP = 5L;
    private static final long AUTO_RENEW_PERIOD = 30L;
    private static final long CREATE_TIMESTAMP = 1L;
    private static final long ALLOWANCE_TIMESTAMP = CREATE_TIMESTAMP + 1L;
    private static final Timestamp EXPIRY_TIMESTAMP =
            Timestamp.newBuilder().setSeconds(360L).build();
    private static final long EXPIRY_NS = EXPIRY_TIMESTAMP.getSeconds() * 1_000_000_000 + EXPIRY_TIMESTAMP.getNanos();
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_1 = DOMAIN_BUILDER.entityNum(1199);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_2 = DOMAIN_BUILDER.entityNum(1200);
    private static final EntityId FEE_COLLECTOR_ACCOUNT_ID_3 = DOMAIN_BUILDER.entityNum(1201);
    private static final EntityId FEE_DOMAIN_TOKEN_ID = DOMAIN_BUILDER.entityNum(9800);
    private static final EntityId FEE_PAYER_1 = DOMAIN_BUILDER.entityNum(1500);
    private static final EntityId FEE_PAYER_2 = DOMAIN_BUILDER.entityNum(1501);
    private static final long INITIAL_SUPPLY = 1_000_000L;
    private static final byte[] METADATA = "METADATA".getBytes();
    private static final long SERIAL_NUMBER_1 = 1L;
    private static final long SERIAL_NUMBER_2 = 2L;
    private static final List<Long> SERIAL_NUMBER_LIST = Arrays.asList(SERIAL_NUMBER_1, SERIAL_NUMBER_2);
    private static final String SYMBOL = "FOOCOIN";
    private static final String TOKEN_CREATE_MEMO = "TokenCreate memo";
    private static final EntityId DOMAIN_TOKEN_ID = DOMAIN_BUILDER.entityNum(3001);
    private static final TokenID TOKEN_ID = DOMAIN_TOKEN_ID.toTokenID();
    private static final Key TOKEN_REF_KEY = keyFromString(KEY);
    private static final long TOKEN_UPDATE_AUTO_RENEW_PERIOD = 12L;
    private static final Key TOKEN_UPDATE_REF_KEY = keyFromString(KEY2);
    private static final String TOKEN_UPDATE_MEMO = "TokenUpdate memo";
    private static final long TRANSFER_TIMESTAMP = 15L;
    private static final EntityId PAYER_ACCOUNT_ID = EntityId.of(PAYER);
    private static final byte[] TRANSFER_SIGNATURE = Bytes.fromHexString(
                    "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            .toArray();

    private final ContractLogRepository contractLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAirdropRepository tokenAirdropRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenRepository tokenRepository;
    private final TokenHistoryRepository tokenHistoryRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TransactionRepository transactionRepository;

    private static CustomFee emptyCustomFees(long consensusTimestamp, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setEntityId(tokenId.getId());
        customFee.setTimestampRange(Range.atLeast(consensusTimestamp));
        return customFee;
    }

    private static CustomFee nonEmptyCustomFee(long consensusTimestamp, EntityId tokenId, TokenType tokenType) {
        var customFee = new CustomFee();
        customFee.setTimestampRange(Range.atLeast(consensusTimestamp));
        customFee.setEntityId(tokenId.getId());
        EntityId treasury = PAYER_ACCOUNT_ID;

        var fixedFee1 = new org.hiero.mirror.common.domain.token.FixedFee();
        fixedFee1.setAllCollectorsAreExempt(false);
        fixedFee1.setAmount(11L);
        fixedFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1);
        customFee.addFixedFee(fixedFee1);

        var fixedFee2 = new org.hiero.mirror.common.domain.token.FixedFee();
        fixedFee2.setAllCollectorsAreExempt(false);
        fixedFee2.setAmount(12L);
        fixedFee2.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee2.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
        customFee.addFixedFee(fixedFee2);

        var fixedFee3 = new org.hiero.mirror.common.domain.token.FixedFee();
        fixedFee3.setAllCollectorsAreExempt(true);
        fixedFee3.setAmount(13L);
        fixedFee3.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2);
        fixedFee3.setDenominatingTokenId(tokenId);
        customFee.addFixedFee(fixedFee3);

        if (tokenType == FUNGIBLE_COMMON) {
            // fractional fees only apply for fungible tokens
            var fractionalFee1 = new org.hiero.mirror.common.domain.token.FractionalFee();
            fractionalFee1.setAllCollectorsAreExempt(false);
            fractionalFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_3);
            fractionalFee1.setDenominator(31L);
            fractionalFee1.setMinimumAmount(1L);
            fractionalFee1.setMaximumAmount(100L);
            fractionalFee1.setNetOfTransfers(true);
            fractionalFee1.setNumerator(14L);
            customFee.addFractionalFee(fractionalFee1);

            var fractionalFee2 = new org.hiero.mirror.common.domain.token.FractionalFee();
            fractionalFee2.setAllCollectorsAreExempt(true);
            fractionalFee2.setCollectorAccountId(treasury);
            fractionalFee2.setDenominator(32L);
            fractionalFee2.setMinimumAmount(10L);
            fractionalFee2.setMaximumAmount(110L);
            fractionalFee2.setNetOfTransfers(false);
            fractionalFee2.setNumerator(15L);
            customFee.addFractionalFee(fractionalFee2);
        } else {
            // royalty fees only apply for non-fungible tokens
            var royaltyFee1 = new org.hiero.mirror.common.domain.token.RoyaltyFee();
            royaltyFee1.setAllCollectorsAreExempt(false);
            royaltyFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_3);
            royaltyFee1.setDenominator(31L);
            royaltyFee1.setNumerator(14L);
            customFee.addRoyaltyFee(royaltyFee1);

            // with fallback fee
            var royaltyFee2 = new org.hiero.mirror.common.domain.token.RoyaltyFee();
            royaltyFee2.setAllCollectorsAreExempt(true);
            royaltyFee2.setCollectorAccountId(treasury);
            royaltyFee2.setDenominator(32L);
            royaltyFee2.setNumerator(15L);

            var fallBackFee = new FallbackFee();
            fallBackFee.setAmount(103L);
            fallBackFee.setDenominatingTokenId(FEE_DOMAIN_TOKEN_ID);
            royaltyFee2.setFallbackFee(fallBackFee);
            customFee.addRoyaltyFee(royaltyFee2);
        }

        return customFee;
    }

    private static Stream<Arguments> provideTokenCreateFtArguments() {
        return provideTokenCreateArguments(FUNGIBLE_COMMON);
    }

    private static Stream<Arguments> provideTokenCreateNftArguments() {
        return provideTokenCreateArguments(NON_FUNGIBLE_UNIQUE);
    }

    private static Stream<Arguments> provideTokenCreateArguments(TokenType tokenType) {
        var nonEmptyCustomFee = nonEmptyCustomFee(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID, tokenType);
        EntityId treasury = PAYER_ACCOUNT_ID;
        // fractional fees only apply for FT, thus FEE_COLLECTOR_ACCOUNT_ID_3 (collector of a fractional fee for FT, and
        // a royalty fee in case of NFT) will be auto enabled only for FT custom fees
        List<EntityId> autoEnabledAccounts = tokenType == FUNGIBLE_COMMON
                ? List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2, FEE_COLLECTOR_ACCOUNT_ID_3)
                : List.of(treasury, FEE_COLLECTOR_ACCOUNT_ID_2);

        List<Long> autoEnabledAccountBalances =
                tokenType == FUNGIBLE_COMMON ? List.of(1000000L, 0L, 0L) : List.of(0L, 0L, 0L);
        return Stream.of(
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(List.of(treasury))
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(emptyCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID)))
                        .customFeesDescription("empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(nonEmptyCustomFee))
                        .customFeesDescription("non-empty custom fees")
                        .expectedFreezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .freezeKey(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(nonEmptyCustomFee))
                        .customFeesDescription("non-empty custom fees")
                        .expectedFreezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .freezeDefault(true)
                        .freezeKey(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(nonEmptyCustomFee))
                        .customFeesDescription("non-empty custom fees")
                        .expectedKycstatus(TokenKycStatusEnum.REVOKED)
                        .kycKey(true)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(nonEmptyCustomFee))
                        .customFeesDescription("non-empty custom fees")
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments(),
                TokenCreateArguments.builder()
                        .autoEnabledAccounts(autoEnabledAccounts)
                        .balances(autoEnabledAccountBalances)
                        .createdTimestamp(CREATE_TIMESTAMP)
                        .customFees(List.of(nonEmptyCustomFee))
                        .customFeesDescription("non-empty custom fees")
                        .expectedPauseStatus(TokenPauseStatusEnum.UNPAUSED)
                        .pauseKey(true)
                        .tokenId(DOMAIN_TOKEN_ID)
                        .build()
                        .toArguments());
    }

    private static Stream<Arguments> provideTokenUpdateArguments() {
        return Stream.of(
                Arguments.of(
                        "new auto renew account", PAYER2, EntityId.of(PAYER2).getId()),
                Arguments.of(
                        "clear auto renew account",
                        AccountID.newBuilder().setAccountNum(0).build(),
                        0L),
                Arguments.of("keep auto renew account", null, PAYER_ACCOUNT_ID.getId()));
    }

    private static Stream<Arguments> provideAssessedCustomFees() {
        // without effective payer account ids, this is prior to services 0.17.1
        // paid in HBAR
        AssessedCustomFee assessedCustomFee1 = new AssessedCustomFee();
        assessedCustomFee1.setAmount(12505L);
        assessedCustomFee1.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1.getId());
        assessedCustomFee1.setConsensusTimestamp(TRANSFER_TIMESTAMP);
        assessedCustomFee1.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee1.setPayerAccountId(PAYER_ACCOUNT_ID);

        // paid in FEE_DOMAIN_TOKEN_ID
        AssessedCustomFee assessedCustomFee2 = new AssessedCustomFee();
        assessedCustomFee2.setAmount(8750L);
        assessedCustomFee2.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2.getId());
        assessedCustomFee2.setConsensusTimestamp(TRANSFER_TIMESTAMP);
        assessedCustomFee2.setEffectivePayerAccountIds(Collections.emptyList());
        assessedCustomFee2.setPayerAccountId(PAYER_ACCOUNT_ID);
        assessedCustomFee2.setTokenId(FEE_DOMAIN_TOKEN_ID);
        List<AssessedCustomFee> assessedCustomFees = List.of(assessedCustomFee1, assessedCustomFee2);

        // build the corresponding protobuf assessed custom fee list
        var protoAssessedCustomFee1 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .setAmount(12505L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_1))
                .build();
        var protoAssessedCustomFee2 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .setAmount(8750L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_2))
                .setTokenId(convertTokenId(FEE_DOMAIN_TOKEN_ID))
                .build();
        var protoAssessedCustomFees = List.of(protoAssessedCustomFee1, protoAssessedCustomFee2);

        // with effective payer account ids
        // paid in HBAR, one effective payer
        AssessedCustomFee assessedCustomFee3 = new AssessedCustomFee();
        assessedCustomFee3.setAmount(12300L);
        assessedCustomFee3.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_1.getId());
        assessedCustomFee3.setConsensusTimestamp(TRANSFER_TIMESTAMP);
        assessedCustomFee3.setEffectivePayerAccountIds(List.of(FEE_PAYER_1.getId()));
        assessedCustomFee3.setPayerAccountId(PAYER_ACCOUNT_ID);

        // paid in FEE_DOMAIN_TOKEN_ID, two effective payers
        AssessedCustomFee assessedCustomFee4 = new AssessedCustomFee();
        assessedCustomFee4.setAmount(8790L);
        assessedCustomFee4.setCollectorAccountId(FEE_COLLECTOR_ACCOUNT_ID_2.getId());
        assessedCustomFee4.setConsensusTimestamp(TRANSFER_TIMESTAMP);
        assessedCustomFee4.setEffectivePayerAccountIds(List.of(FEE_PAYER_1.getId(), FEE_PAYER_2.getId()));
        assessedCustomFee4.setPayerAccountId(PAYER_ACCOUNT_ID);
        assessedCustomFee4.setTokenId(FEE_DOMAIN_TOKEN_ID);
        List<AssessedCustomFee> assessedCustomFeesWithPayers = List.of(assessedCustomFee3, assessedCustomFee4);

        // build the corresponding protobuf assessed custom fee list, with effective payer account ids
        var protoAssessedCustomFee3 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .addAllEffectivePayerAccountId(List.of(convertAccountId(FEE_PAYER_1)))
                .setAmount(12300L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_1))
                .build();
        var protoAssessedCustomFee4 = com.hederahashgraph.api.proto.java.AssessedCustomFee.newBuilder()
                .addAllEffectivePayerAccountId(List.of(convertAccountId(FEE_PAYER_1), convertAccountId(FEE_PAYER_2)))
                .setAmount(8790L)
                .setFeeCollectorAccountId(convertAccountId(FEE_COLLECTOR_ACCOUNT_ID_2))
                .setTokenId(convertTokenId(FEE_DOMAIN_TOKEN_ID))
                .build();
        var protoAssessedCustomFeesWithPayers = List.of(protoAssessedCustomFee3, protoAssessedCustomFee4);

        return Stream.of(
                Arguments.of("no assessed custom fees", Collections.emptyList(), Collections.emptyList()),
                Arguments.of(
                        "has assessed custom fees without effective payer account ids",
                        assessedCustomFees,
                        protoAssessedCustomFees),
                Arguments.of(
                        "has assessed custom fees with effective payer account ids",
                        assessedCustomFeesWithPayers,
                        protoAssessedCustomFeesWithPayers));
    }

    private static AccountID convertAccountId(EntityId accountId) {
        return AccountID.newBuilder()
                .setShardNum(accountId.getShard())
                .setRealmNum(accountId.getRealm())
                .setAccountNum(accountId.getNum())
                .build();
    }

    private static TokenID convertTokenId(EntityId tokenId) {
        return TokenID.newBuilder()
                .setShardNum(tokenId.getShard())
                .setRealmNum(tokenId.getRealm())
                .setTokenNum(tokenId.getNum())
                .build();
    }

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEntityTransactions(true);
        entityProperties.getPersist().setSyntheticContractResults(true);
        entityProperties.getPersist().setTokens(true);
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setEntityTransactions(false);
        entityProperties.getPersist().setSyntheticContractResults(false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithAutoTokenAssociations(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus,
            TokenPauseStatusEnum expectedPauseStatus,
            List<TokenAccount> expectedTokenAccounts) {
        List<EntityId> autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(t -> EntityId.of(t.getAccountId()))
                .toList();
        tokenCreate(
                customFees,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                expectedFreezeStatus,
                expectedKycStatus,
                expectedPauseStatus,
                expectedTokenAccounts,
                autoAssociatedAccounts);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateFtArguments")
    void tokenCreateWithoutAutoTokenAssociations(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus,
            TokenPauseStatusEnum expectedPauseStatus,
            List<TokenAccount> expectedTokenAccounts) {
        tokenCreate(
                customFees,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                expectedFreezeStatus,
                expectedKycStatus,
                expectedPauseStatus,
                expectedTokenAccounts,
                Collections.emptyList());
    }

    @Test
    void tokenCreateWithoutPersistence() {
        entityProperties.getPersist().setTokens(false);

        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, false, false);

        assertThat(tokenRepository.findAll()).isEmpty();
        assertThat(tokenTransferRepository.count()).isZero();
        assertCustomFeesInDb(Collections.emptyList(), Collections.emptyList());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenCreateNftArguments")
    void tokenCreateWithNfts(
            String name,
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus,
            TokenPauseStatusEnum expectedPauseStatus,
            List<TokenAccount> expectedTokenAccounts) {
        // given
        var expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN,
                TOKEN_REF_KEY,
                PAYER_ACCOUNT_ID.getId(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
        var autoAssociatedAccounts = expectedTokenAccounts.stream()
                .map(t -> EntityId.of(t.getAccountId()))
                .toList();

        // when
        createTokenEntity(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                customFees,
                autoAssociatedAccounts);

        // then
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        // verify token
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(expectedFreezeStatus, Token::getFreezeStatus)
                .returns(expectedKycStatus, Token::getKycStatus)
                .returns(expectedPauseStatus, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(0L, Token::getTotalSupply);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        assertCustomFeesInDb(customFees, Collections.emptyList());
        assertThat(tokenTransferRepository.count()).isZero();
    }

    @Test
    void tokenAssociate() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                null,
                ASSOCIATE_TIMESTAMP);
    }

    @Test
    void tokenAssociatePrecompile() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, true, true);

        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction, builder -> {
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                null,
                ASSOCIATE_TIMESTAMP);

        assertContractResult(ASSOCIATE_TIMESTAMP, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenAssociateWithMissingToken() {
        var associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        // verify token account was created
        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                null,
                ASSOCIATE_TIMESTAMP);
    }

    @Test
    void tokenDissociate() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 10L;
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction);

        EntityId tokenId = EntityId.of(TOKEN_ID);
        EntityId accountId = EntityId.of(PAYER2);

        var expected = TokenAccount.builder()
                .accountId(accountId.getId())
                .associated(false)
                .automaticAssociation(false)
                .balanceTimestamp(ASSOCIATE_TIMESTAMP)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .tokenId(tokenId.getId())
                .build();

        assertThat(tokenAccountRepository.findById(expected.getId())).hasValue(expected);
    }

    @Test
    void tokenDissociatePrecompile() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        Transaction dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = 10L;
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(dissociateTimeStamp, dissociateTransaction, builder -> {
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                false,
                null,
                null,
                dissociateTimeStamp);

        assertContractResult(dissociateTimeStamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenDissociateDeletedFungibleToken() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long amount = 10;
        long tokenTransferTimestamp = 10L;
        var transfers = tokenTransferList(TOKEN_ID, accountAmount(PAYER, -amount), accountAmount(PAYER2, amount));
        insertAndParseTransaction(
                tokenTransferTimestamp,
                tokenTransferTransaction(),
                builder -> builder.addTokenTransferLists(transfers));

        long tokenDeleteTimestamp = tokenTransferTimestamp + 5L;
        var deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenDeleteTimestamp + 5L;
        var dissociateTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -amount));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfer));

        // then
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY - amount, Token::getTotalSupply);
        var tokenTransferId = new TokenTransfer.Id(dissociateTimeStamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER2));
        var expectedDissociateTransfer = domainBuilder
                .tokenTransfer()
                .customize(t ->
                        t.amount(-amount).id(tokenTransferId).isApproval(false).payerAccountId(PAYER_ACCOUNT_ID))
                .get();
        assertThat(tokenTransferRepository.findById(tokenTransferId)).hasValue(expectedDissociateTransfer);

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(EntityId.of(PAYER2).getId())
                .associated(false)
                .automaticAssociation(false)
                .balanceTimestamp(dissociateTimeStamp)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .balance(0)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
        // No history row should be created for deleted token dissociate.
        assertThat(tokenHistoryRepository.count()).isZero();
    }

    @Test
    void tokenDissociateDeletedNonFungibleToken() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        // mint
        long mintTimestamp = 10L;
        var mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        var mintTransaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);
        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        // transfer
        long transferTimestamp = mintTimestamp + 5L;
        var nftTransfer = nftTransfer(TOKEN_ID, PAYER2, PAYER, SERIAL_NUMBER_LIST);
        insertAndParseTransaction(
                transferTimestamp, tokenTransferTransaction(), builder -> builder.addTokenTransferLists(nftTransfer));

        // then
        var historyRange = Range.closedOpen(mintTimestamp, transferTimestamp);
        var nftHistory = new ArrayList<Nft>();
        nftHistory.add(nft1.toBuilder().timestampRange(historyRange).build());
        nftHistory.add(nft2.toBuilder().timestampRange(historyRange).build());
        // Current rows
        Stream.of(nft1, nft2).forEach(n -> {
            n.setAccountId(EntityId.of(PAYER2));
            n.setTimestampLower(transferTimestamp);
        });
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        // delete
        long tokenDeleteTimestamp = transferTimestamp + 5L;
        var deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        insertAndParseTransaction(tokenDeleteTimestamp, deleteTransaction);

        // when
        // dissociate
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenDeleteTimestamp + 5L;
        var dissociateTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -2));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfer));

        // then
        historyRange = Range.closedOpen(transferTimestamp, dissociateTimeStamp);
        nftHistory.add(nft1.toBuilder().timestampRange(historyRange).build());
        nftHistory.add(nft2.toBuilder().timestampRange(historyRange).build());
        Stream.of(nft1, nft2).forEach(n -> {
            n.setAccountId(null);
            n.setDeleted(true);
            n.setTimestampLower(dissociateTimeStamp);
        });
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(0L, Token::getTotalSupply);
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(dissociateTimeStamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER2, -2, TOKEN_ID));
        assertThat(tokenTransferRepository.findAll()).isEmpty();

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(EntityId.of(PAYER2).getId())
                .associated(false)
                .automaticAssociation(false)
                .balance(0)
                .balanceTimestamp(dissociateTimeStamp)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenDissociateExpiredFungibleToken(boolean isTreasuryTransferFirst) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long amount = 10;
        long tokenTransferTimestamp = 10L;
        var transfers = tokenTransferList(TOKEN_ID, accountAmount(PAYER, -amount), accountAmount(PAYER2, amount));
        insertAndParseTransaction(
                tokenTransferTimestamp,
                tokenTransferTransaction(),
                builder -> builder.addTokenTransferLists(transfers));

        // at some time the token has expired, later, when
        var dissociateTransaction = tokenDissociate(List.of(TOKEN_ID), PAYER2);
        long dissociateTimeStamp = tokenTransferTimestamp + 10L;
        var dissociateTransfers = isTreasuryTransferFirst
                ? tokenTransferList(TOKEN_ID, accountAmount(PAYER, amount), accountAmount(PAYER2, -amount))
                : tokenTransferList(TOKEN_ID, accountAmount(PAYER2, -amount), accountAmount(PAYER, amount));
        insertAndParseTransaction(
                dissociateTimeStamp,
                dissociateTransaction,
                builder -> builder.addTokenTransferLists(dissociateTransfers));

        // then
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply);
        var expectedDissociateTransfers = List.of(
                domainBuilder
                        .tokenTransfer()
                        .customize(t -> t.amount(-amount)
                                .id(new TokenTransfer.Id(
                                        dissociateTimeStamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER2)))
                                .isApproval(false)
                                .payerAccountId(PAYER_ACCOUNT_ID))
                        .get(),
                domainBuilder
                        .tokenTransfer()
                        .customize(t -> t.amount(amount)
                                .id(new TokenTransfer.Id(dissociateTimeStamp, EntityId.of(TOKEN_ID), PAYER_ACCOUNT_ID))
                                .isApproval(false)
                                .payerAccountId(PAYER_ACCOUNT_ID))
                        .get());
        assertThat(tokenTransferRepository.findByConsensusTimestamp(dissociateTimeStamp))
                .containsExactlyInAnyOrderElementsOf(expectedDissociateTransfers);

        var expectedTokenAccount = TokenAccount.builder()
                .accountId(EntityId.of(PAYER2).getId())
                .associated(false)
                .automaticAssociation(false)
                .balanceTimestamp(dissociateTimeStamp)
                .createdTimestamp(ASSOCIATE_TIMESTAMP)
                .timestampRange(Range.atLeast(dissociateTimeStamp))
                .balance(0)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId()))
                .get()
                .isEqualTo(expectedTokenAccount);
    }

    @Test
    void tokenDelete() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        // delete token
        Transaction deleteTransaction = tokenDeleteTransaction(TOKEN_ID);
        long deleteTimeStamp = 10L;
        insertAndParseTransaction(deleteTimeStamp, deleteTransaction);

        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN,
                TOKEN_REF_KEY,
                PAYER_ACCOUNT_ID.getId(),
                AUTO_RENEW_PERIOD,
                true,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                CREATE_TIMESTAMP,
                deleteTimeStamp);
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenFeeScheduleUpdate(TokenType tokenType) {
        // given
        // create the token entity with empty custom fees
        createTokenEntity(TOKEN_ID, tokenType, SYMBOL, CREATE_TIMESTAMP, false, false, false);
        // update fee schedule
        long updateTimestamp = CREATE_TIMESTAMP + 10L;
        var expectedEntity = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN,
                TOKEN_REF_KEY,
                PAYER_ACCOUNT_ID.getId(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);
        var initialCustomFee = emptyCustomFees(CREATE_TIMESTAMP, DOMAIN_TOKEN_ID);
        var secondCustomFee = nonEmptyCustomFee(updateTimestamp, DOMAIN_TOKEN_ID, tokenType);

        long expectedSupply = tokenType == FUNGIBLE_COMMON ? INITIAL_SUPPLY : 0;

        // when
        updateTokenFeeSchedule(TOKEN_ID, updateTimestamp, List.of(secondCustomFee));

        // then
        assertEntity(expectedEntity);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(expectedSupply, Token::getTotalSupply);
        initialCustomFee.setTimestampRange(Range.closedOpen(CREATE_TIMESTAMP, updateTimestamp));
        assertCustomFeesInDb(List.of(secondCustomFee), List.of(initialCustomFee));

        // when update with empty custom fees
        updateTimestamp += 10L;
        updateTokenFeeSchedule(TOKEN_ID, updateTimestamp, Collections.emptyList());

        // then
        assertEntity(expectedEntity);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(expectedSupply, Token::getTotalSupply);

        secondCustomFee.setTimestampUpper(updateTimestamp);
        var lastCustomFee = emptyCustomFees(updateTimestamp, DOMAIN_TOKEN_ID);
        assertCustomFeesInDb(List.of(lastCustomFee), List.of(initialCustomFee, secondCustomFee));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTokenUpdateArguments")
    void tokenUpdate(String name, AccountID autoRenewAccountId, Long expectedAutoRenewAccountId) {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID, newSymbol, TOKEN_UPDATE_MEMO, TOKEN_UPDATE_REF_KEY, autoRenewAccountId, PAYER2);
        long updateTimeStamp = 10L;
        insertAndParseTransaction(updateTimeStamp, transaction);

        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN,
                TOKEN_UPDATE_REF_KEY,
                expectedAutoRenewAccountId,
                TOKEN_UPDATE_AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_UPDATE_MEMO,
                CREATE_TIMESTAMP,
                updateTimeStamp);
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        var keyBytes = TOKEN_UPDATE_REF_KEY.toByteArray();
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(keyBytes, Token::getFeeScheduleKey)
                .returns(keyBytes, Token::getFreezeKey)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(keyBytes, Token::getKycKey)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(keyBytes, Token::getSupplyKey)
                .returns(newSymbol, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply)
                .returns(keyBytes, Token::getWipeKey);
        // History row should not be created
        assertThat(tokenHistoryRepository.count()).isEqualTo(1L);
    }

    @Test
    void tokenUpdateWithMissingToken() {
        String newSymbol = "NEWSYMBOL";
        Transaction transaction = tokenUpdateTransaction(
                TOKEN_ID,
                newSymbol,
                TOKEN_UPDATE_MEMO,
                keyFromString(KEY2),
                AccountID.newBuilder().setAccountNum(2002).build(),
                PAYER2);
        insertAndParseTransaction(10L, transaction);

        // verify token was not created when missing
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void tokenPause() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.UNPAUSED,
                INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        long pauseTimeStamp = 15L;
        insertAndParseTransaction(pauseTimeStamp, transaction);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.PAUSED, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply);
    }

    @Test
    void tokenUnpause() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                true,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.UNPAUSED,
                INITIAL_SUPPLY);

        Transaction transaction = tokenPauseTransaction(TOKEN_ID, true);
        insertAndParseTransaction(15L, transaction);

        transaction = tokenPauseTransaction(TOKEN_ID, false);
        long unpauseTimeStamp = 20L;
        insertAndParseTransaction(unpauseTimeStamp, transaction);

        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.UNPAUSED, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftUpdateTreasury(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        var metadata = recordItemBuilder.bytes(12);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();
        var updateRecordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).setTreasury(PAYER2))
                .record(r -> r.clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER2)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(WILDCARD_SERIAL_NUMBER))))
                .build();
        var recordItems = List.of(mintRecordItem, updateRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        long mintTimestamp = mintRecordItem.getConsensusTimestamp();
        long updateTimestamp = updateRecordItem.getConsensusTimestamp();
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                updateTimestamp, domainNftTransfer(PAYER2, PAYER, WILDCARD_SERIAL_NUMBER, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);

        var expectedNft = Nft.builder()
                .accountId(EntityId.of(PAYER2))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var expectedNftHistory = expectedNft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(expectedNft);
        assertThat(findHistory(Nft.class)).containsExactly(expectedNftHistory);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            true, true
                            true, false
                            false, true
                            false, false
                            """)
    void nftUpdateTreasuryWithNftStateChange(boolean explicitAssociation, boolean singleRecordFile) {
        // given
        var account = domainBuilder.entityId();
        var oldTreasury = domainBuilder.entityId();
        var newTreasury = domainBuilder.entityId();
        var tokenId = domainBuilder.entityId();
        var protoAccount = account.toAccountID();
        var protoOldTreasury = oldTreasury.toAccountID();
        var protoNewTreasury = newTreasury.toAccountID();
        var protoTokenId = tokenId.toTokenID();

        var recordItems = new ArrayList<RecordItem>();
        var tokenCreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.clearCustomFees()
                        .clearFreezeKey()
                        .clearKycKey()
                        .setInitialSupply(0L)
                        .setTokenType(NON_FUNGIBLE_UNIQUE)
                        .setTreasury(protoOldTreasury))
                .receipt(r -> r.setTokenID(protoTokenId))
                .record(r -> r.clearAutomaticTokenAssociations()
                        .addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setTokenId(protoTokenId)
                                .setAccountId(protoOldTreasury)))
                .build();
        recordItems.add(tokenCreateRecordItem);

        // when
        // mint
        var mintSerials = List.of(1L, 2L, 3L);
        var metadata = recordItemBuilder.bytes(16);
        var mintTransfers = mintSerials.stream()
                .map(serial -> NftTransfer.newBuilder()
                        .setSerialNumber(serial)
                        .setReceiverAccountID(protoOldTreasury)
                        .build())
                .toList();
        var nftMintTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addAllNftTransfers(mintTransfers)
                .build();
        var nftMintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(
                        b -> b.setToken(protoTokenId).clearMetadata().addAllMetadata(Collections.nCopies(3, metadata)))
                .record(r -> r.clearTokenTransferLists().addTokenTransferLists(nftMintTransferList))
                .receipt(r -> r.clearSerialNumbers().addAllSerialNumbers(mintSerials))
                .build();
        recordItems.add(nftMintRecordItem);

        // transfer serial number 2 to account
        var nftTransfer = NftTransfer.newBuilder()
                .setSerialNumber(2L)
                .setReceiverAccountID(protoAccount)
                .setSenderAccountID(protoOldTreasury)
                .build();
        var nftTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addNftTransfers(nftTransfer)
                .build();
        var nftTransferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .record(r -> r.addTokenTransferLists(nftTransferList))
                .build();
        recordItems.add(nftTransferRecordItem);

        long newTreasuryAssociationTimestamp = 0;
        if (explicitAssociation) {
            var recordItem = recordItemBuilder
                    .tokenAssociate()
                    .transactionBody(
                            b -> b.setAccount(protoNewTreasury).clearTokens().addTokens(protoTokenId))
                    .build();
            newTreasuryAssociationTimestamp = recordItem.getConsensusTimestamp();
            recordItems.add(recordItem);
        }

        // token update which changes treasury
        var nftTreasuryUpdate = NftTransfer.newBuilder()
                .setSerialNumber(WILDCARD_SERIAL_NUMBER)
                .setReceiverAccountID(protoNewTreasury)
                .setSenderAccountID(protoOldTreasury)
                .build();
        var nftTreasuryUpdateTransferList = TokenTransferList.newBuilder()
                .setToken(protoTokenId)
                .addNftTransfers(nftTreasuryUpdate)
                .build();
        var nftUpdateRecordItem = recordItemBuilder
                .tokenUpdate()
                .transactionBody(b -> b.setToken(protoTokenId).setTreasury(protoNewTreasury))
                .record(r -> {
                    r.addTokenTransferLists(nftTreasuryUpdateTransferList);
                    if (!explicitAssociation) {
                        r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(protoNewTreasury)
                                .setTokenId(protoTokenId));
                    }
                })
                .build();
        if (!explicitAssociation) {
            newTreasuryAssociationTimestamp = nftUpdateRecordItem.getConsensusTimestamp();
        }
        recordItems.add(nftUpdateRecordItem);

        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        var metadataBytes = DomainUtils.toBytes(metadata);
        long mintTimestamp = nftMintRecordItem.getConsensusTimestamp();
        long transferTimestamp = nftTransferRecordItem.getConsensusTimestamp();
        long updateTimestamp = nftUpdateRecordItem.getConsensusTimestamp();
        var nft1 = Nft.builder()
                .accountId(newTreasury)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(1)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft2 = Nft.builder()
                .accountId(account)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(2)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft3 = Nft.builder()
                .accountId(newTreasury)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(metadataBytes)
                .serialNumber(3)
                .timestampRange(Range.atLeast(updateTimestamp))
                .tokenId(tokenId.getId())
                .build();
        var nft1History = nft1.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        var nft2History = nft2.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, transferTimestamp))
                .build();
        var nft3History = nft3.toBuilder()
                .accountId(oldTreasury)
                .timestampRange(Range.closedOpen(mintTimestamp, updateTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2, nft3);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1History, nft2History, nft3History);

        assertNftTransferInRepository(
                nftMintRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 1, protoTokenId),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 2, protoTokenId),
                domainNftTransfer(protoOldTreasury, DEFAULT_ACCOUNT_ID, 3, protoTokenId));
        assertNftTransferInRepository(
                nftTransferRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoAccount, protoOldTreasury, 2, protoTokenId));
        assertNftTransferInRepository(
                nftUpdateRecordItem.getConsensusTimestamp(),
                domainNftTransfer(protoNewTreasury, protoOldTreasury, WILDCARD_SERIAL_NUMBER, protoTokenId));

        var tokenAccountOldTreasury = TokenAccount.builder()
                .accountId(oldTreasury.getId())
                .associated(true)
                .automaticAssociation(false)
                .balance(0L)
                .balanceTimestamp(updateTimestamp)
                .createdTimestamp(tokenCreateRecordItem.getConsensusTimestamp())
                .timestampRange(Range.atLeast(tokenCreateRecordItem.getConsensusTimestamp()))
                .tokenId(tokenId.getId())
                .build();
        var tokenAccountNewTreasury = TokenAccount.builder()
                .accountId(newTreasury.getId())
                .associated(true)
                .automaticAssociation(!explicitAssociation)
                .balance(2L)
                .balanceTimestamp(updateTimestamp)
                .createdTimestamp(newTreasuryAssociationTimestamp)
                .timestampRange(Range.atLeast(newTreasuryAssociationTimestamp))
                .tokenId(tokenId.getId())
                .build();
        assertThat(tokenAccountRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("freezeStatus", "kycStatus")
                .containsExactlyInAnyOrder(tokenAccountOldTreasury, tokenAccountNewTreasury);
    }

    @Test
    void tokenAccountFreeze() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                true,
                false,
                false,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        Transaction transaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 15L;
        insertAndParseTransaction(freezeTimeStamp, transaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.FROZEN,
                null,
                freezeTimeStamp);
    }

    @Test
    void tokenAccountUnfreeze() {
        // create token with freeze default
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, true, false, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        Transaction freezeTransaction = tokenFreezeTransaction(TOKEN_ID, true);
        long freezeTimeStamp = 10L;
        insertAndParseTransaction(freezeTimeStamp, freezeTransaction);

        // unfreeze
        Transaction unfreezeTransaction = tokenFreezeTransaction(TOKEN_ID, false);
        long unfreezeTimeStamp = 444;
        insertAndParseTransaction(unfreezeTimeStamp, unfreezeTransaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                TokenFreezeStatusEnum.UNFROZEN,
                null,
                unfreezeTimeStamp);
    }

    @Test
    void tokenAccountGrantKyc() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                true,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.REVOKED,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        Transaction transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, transaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                TokenKycStatusEnum.GRANTED,
                grantTimeStamp);
    }

    @Test
    void tokenAccountGrantKycWithMissingTokenAccount() {
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true, false);

        var transaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, transaction);

        // verify minimal token account with kyc granted
        assertTokenAccountInRepository(
                TOKEN_ID, PAYER2, null, 0, null, null, true, null, TokenKycStatusEnum.GRANTED, grantTimeStamp);
    }

    @Test
    void tokenAccountRevokeKyc() {
        // create token with kyc revoked
        createTokenEntity(TOKEN_ID, FUNGIBLE_COMMON, SYMBOL, CREATE_TIMESTAMP, false, true, false);

        // associate account
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        insertAndParseTransaction(ASSOCIATE_TIMESTAMP, associateTransaction);

        Transaction grantTransaction = tokenKycTransaction(TOKEN_ID, true);
        long grantTimeStamp = 10L;
        insertAndParseTransaction(grantTimeStamp, grantTransaction);

        // revoke
        Transaction revokeTransaction = tokenKycTransaction(TOKEN_ID, false);
        long revokeTimestamp = 333;
        insertAndParseTransaction(revokeTimestamp, revokeTransaction);

        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                0,
                ASSOCIATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                TokenKycStatusEnum.REVOKED,
                revokeTimestamp);
    }

    @Test
    void tokenBurn() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long amount = -1000;
        long burnTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, false, amount, null);

        // when
        insertAndParseTransaction(burnTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // then
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);

        // History row should not be created
        assertThat(tokenHistoryRepository.count()).isZero();
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, burnTimestamp, amount);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY - amount, Token::getTotalSupply);

        assertThat(contractLogRepository.findById(new ContractLog.Id(burnTimestamp, 0)))
                .get()
                .returns(burnTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(toTrimmedBytes(EntityId.of(PAYER2)), from(ContractLog::getTopic1))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(-amount), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(burnTimestamp))
                .hasSize(1)
                .first()
                .returns(burnTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 12L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .spender(EntityId.of(SPENDER).getId())
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).hasValue(expectedNft1);

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L);
            builder.addTokenTransferLists(burnTransfer);
        });

        expectedNft1.setAccountId(null);
        expectedNft1.setDeleted(true);
        expectedNft1.setTimestampLower(burnTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        // Verify
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                burnTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER, SERIAL_NUMBER_1, TOKEN_ID));

        assertThat(tokenTransferRepository.findAll()).isEmpty();
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);

        assertThat(contractLogRepository.findById(new ContractLog.Id(burnTimestamp, 0)))
                .get()
                .returns(burnTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(toTrimmedBytes(PAYER_ACCOUNT_ID), from(ContractLog::getTopic1))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(SERIAL_NUMBER_1), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(burnTimestamp))
                .hasSize(1)
                .first()
                .returns(burnTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNftMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0L);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer);
        });

        long burnTimestamp = 15L;
        TokenTransferList burnTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER, List.of(SERIAL_NUMBER_1));
        Transaction burnTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(burnTimestamp, burnTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1);
            builder.addTokenTransferLists(burnTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                burnTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER, SERIAL_NUMBER_1, TOKEN_ID));

        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);
        var nft1 = Nft.builder()
                .deleted(true)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(burnTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void tokenMint() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, true, amount, null);
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY + amount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, mintTimestamp, amount);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY + amount, Token::getTotalSupply);
        // History row should not be created
        assertThat(tokenHistoryRepository.count()).isZero();

        assertThat(contractLogRepository.findById(new ContractLog.Id(mintTimestamp, 0)))
                .get()
                .returns(mintTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic1))
                .returns(toTrimmedBytes(EntityId.of(PAYER2)), from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(amount), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(mintTimestamp))
                .hasSize(1)
                .first()
                .returns(mintTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnFtsPrecompile() {
        tokenSupplyFtsPrecompile(false);
    }

    @Test
    void tokenMintFtsPrecompile() {
        tokenSupplyFtsPrecompile(true);
    }

    private void tokenSupplyFtsPrecompile(boolean isMint) {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long amount = 1000;
        long mintTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER, amount));
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, FUNGIBLE_COMMON, isMint, amount, null);
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY + amount);
            builder.addTokenTransferLists(tokenTransfer);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // Verify
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, mintTimestamp, amount);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY + amount, Token::getTotalSupply);

        assertContractResult(mintTimestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNfts() {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(2L, Token::getTotalSupply);

        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        assertThat(contractLogRepository.findById(new ContractLog.Id(mintTimestamp, 0)))
                .get()
                .returns(mintTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic1))
                .returns(toTrimmedBytes(PAYER_ACCOUNT_ID), from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(SERIAL_NUMBER_1), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(mintTimestamp))
                .hasSize(1)
                .first()
                .returns(mintTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenBurnNftsPrecompile() {
        // given
        long mintTimestamp = 10L;
        tokenSupplyMintNftsPrecompile(mintTimestamp);

        long burnTimestamp = 15L;
        var transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, false, 0, SERIAL_NUMBER_LIST);
        var contractFunctionResultAtomic = new AtomicReference<ContractFunctionResult>();

        // when
        insertAndParseTransaction(burnTimestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // then
        var nft1 = Nft.builder()
                .createdTimestamp(mintTimestamp)
                .deleted(true)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(burnTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        var nftHistory = Stream.of(nft1, nft2)
                .map(Nft::toBuilder)
                .map(b -> b.accountId(PAYER_ACCOUNT_ID)
                        .deleted(false)
                        .timestampRange(Range.closedOpen(mintTimestamp, burnTimestamp))
                        .build())
                .toList();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(nftHistory);

        assertContractResult(burnTimestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNftsPrecompile() {
        tokenSupplyMintNftsPrecompile(10L);
    }

    private void tokenSupplyMintNftsPrecompile(long timestamp) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        // when
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        insertAndParseTransaction(timestamp, transaction, builder -> {
            builder.getReceiptBuilder()
                    .setNewTotalSupply(SERIAL_NUMBER_LIST.size())
                    .addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
            buildContractFunctionResult(builder.getContractCallResultBuilder());
            contractFunctionResultAtomic.set(builder.getContractCallResult());
        });

        // then
        assertNftTransferInRepository(
                timestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(2L, Token::getTotalSupply);

        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(timestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(timestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();

        assertContractResult(timestamp, contractFunctionResultAtomic.get());
    }

    @Test
    void tokenMintNftsMissingToken() {
        // given
        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction transaction = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 2, SERIAL_NUMBER_LIST);

        // when
        insertAndParseTransaction(mintTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertThat(tokenRepository.findAll()).isEmpty();
        var nft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithoutAutoTokenAssociations(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, false, false);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferPrecompile(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, false, true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideAssessedCustomFees")
    void tokenTransferWithAutoTokenAssociations(
            String name,
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees) {
        tokenTransfer(assessedCustomFees, protoAssessedCustomFees, true, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintAllowanceTransfer(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        // mint
        long mintTimestamp = CREATE_TIMESTAMP + 20;
        var metadata = recordItemBuilder.bytes(16);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();

        // approve allowance
        var approveAllowanceTimestamp = mintTimestamp + 20;
        var approveAllowanceRecordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> b.clear()
                        .addNftAllowances(NftAllowance.newBuilder()
                                .addSerialNumbers(1)
                                .setSpender(PAYER2)
                                .setTokenId(TOKEN_ID)))
                .transactionBodyWrapper(w -> w.setTransactionID(Utility.getTransactionId(PAYER)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(approveAllowanceTimestamp)))
                .build();

        // transfer using allowance, PAYER2 transfers nft serial 1 from PAYER to RECEIVER
        var transferTimestamp = approveAllowanceTimestamp + 20;
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setIsApproval(true)
                                        .setReceiverAccountID(RECEIVER)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(1))))
                .transactionBodyWrapper(w -> w.setTransactionID(Utility.getTransactionId(PAYER2)))
                .record(r -> r.clearTokenTransferLists()
                        .setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(RECEIVER)
                                        .setSenderAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();
        var recordItems = List.of(mintRecordItem, approveAllowanceRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID));
        assertNftTransferInRepository(transferTimestamp, domainNftTransfer(RECEIVER, PAYER, 1, TOKEN_ID, true));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);

        var nft = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        // with spender
        var nftHistory1 = nft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .spender(EntityId.of(PAYER2).getId())
                .timestampRange(Range.closedOpen(approveAllowanceTimestamp, transferTimestamp))
                .build();
        // when mint
        var nftHistory2 = nft.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp, approveAllowanceTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(nft);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nftHistory1, nftHistory2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintTransferDuplicateTransfersInRecord(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        long mintTimestamp = 20;
        var metadata1 = recordItemBuilder.bytes(16);
        var metadata2 = recordItemBuilder.bytes(16);
        var mintNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER)
                        .setSerialNumber(2)
                        .build());
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(
                        b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata1).addMetadata(metadata2))
                .receipt(r -> r.clearSerialNumbers()
                        .addSerialNumbers(1)
                        .addSerialNumbers(2)
                        .setNewTotalSupply(2))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .clearTokenTransferLists()
                        .addTokenTransferLists(
                                TokenTransferList.newBuilder()
                                        .setToken(TOKEN_ID)
                                        .addAllNftTransfers(mintNftTransfers)
                                        .addAllNftTransfers(mintNftTransfers) // duplicate
                                ))
                .build();

        long transferTimestamp = mintTimestamp + 10;
        var transferNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(2)
                        .build());
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(
                                TokenTransferList.newBuilder()
                                        .setToken(TOKEN_ID)
                                        .addAllNftTransfers(transferNftTransfers)
                                        .addAllNftTransfers(transferNftTransfers) // duplicate
                                ))
                .build();

        var recordItems = List.of(mintRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(
                mintTimestamp,
                // with duplicates
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 2, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, 2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                // with duplicates
                domainNftTransfer(PAYER2, PAYER, 1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 1, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 2, TOKEN_ID),
                domainNftTransfer(PAYER2, PAYER, 2, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(2L, Token::getTotalSupply);

        var nft1 = Nft.builder()
                .accountId(EntityId.of(PAYER2))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata1))
                .serialNumber(1)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .timestampRange(Range.atLeast(transferTimestamp))
                .build();
        var nft2 = nft1.toBuilder()
                .metadata(DomainUtils.toBytes(metadata2))
                .serialNumber(2)
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);

        List.of(nft1, nft2).forEach(n -> {
            n.setAccountId(PAYER_ACCOUNT_ID);
            n.setTimestampRange(Range.closedOpen(mintTimestamp, transferTimestamp));
        });
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1, nft2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nftMintTransferMultipleParties(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        long mintTimestamp = 20;
        var metadata = recordItemBuilder.bytes(16);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .clearTokenTransferLists()
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();

        long transferTimestamp = mintTimestamp + 10;
        // same NFT transferred twice, PAYER -> PAYER2 -> RECEIVER
        var transferNftTransfers = List.of(
                NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER2)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(1)
                        .build(),
                NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER2)
                        .setSerialNumber(1)
                        .build());
        var transferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.clear()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addAllNftTransfers(transferNftTransfers)))
                .build();

        var recordItems = List.of(mintRecordItem, transferRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertNftTransferInRepository(
                mintTimestamp, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(PAYER2, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER2, SERIAL_NUMBER_1, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);

        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(DomainUtils.toBytes(metadata))
                .serialNumber(1)
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .timestampRange(Range.atLeast(transferTimestamp))
                .build();
        assertThat(nftRepository.findAll()).containsExactly(nft1);

        nft1.setAccountId(PAYER_ACCOUNT_ID);
        nft1.setTimestampRange(Range.closedOpen(mintTimestamp, transferTimestamp));
        assertThat(findHistory(Nft.class)).containsExactly(nft1);
    }

    @Test
    void nftTransfer() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);
        long mintTimestamp1 = 20L;
        var mintTransfer1 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_1));
        var mintTransaction1 = tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_1));

        insertAndParseTransaction(mintTimestamp1, mintTransaction1, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addSerialNumbers(SERIAL_NUMBER_1);
            builder.addTokenTransferLists(mintTransfer1);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 25L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp1)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .spender(EntityId.of(SPENDER).getId())
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).hasValue(expectedNft1);

        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction2 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        // Verify
        insertAndParseTransaction(mintTimestamp2, mintTransaction2, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer2);
        });

        // token transfer
        var expectedEntityTransactions =
                Streams.stream(entityTransactionRepository.findAll()).collect(Collectors.toList());
        var transaction = tokenTransferTransaction();
        var transferList = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2)
                        .build())
                .build();
        long transferTimestamp = 40L;
        var recordItem = insertAndParseTransaction(
                transferTimestamp, transaction, builder -> builder.addTokenTransferLists(transferList));

        // then
        expectedNft1.setAccountId(EntityId.of(RECEIVER));
        expectedNft1.setTimestampLower(transferTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp2)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        assertNftTransferInRepository(
                mintTimestamp1, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                mintTimestamp2, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);
        assertThat(contractLogRepository.findById(new ContractLog.Id(transferTimestamp, 0)))
                .get()
                .returns(transferTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(toTrimmedBytes(PAYER_ACCOUNT_ID), from(ContractLog::getTopic1))
                .returns(toTrimmedBytes(EntityId.of(RECEIVER)), from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(SERIAL_NUMBER_1), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(transferTimestamp))
                .hasSize(1)
                .first()
                .returns(transferTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));

        var entityIds = Lists.newArrayList(
                EntityId.of(recordItem.getTransactionBody().getNodeAccountID()),
                recordItem.getPayerAccountId(),
                EntityId.of(PAYER),
                EntityId.of(RECEIVER),
                EntityId.of(TOKEN_ID));
        expectedEntityTransactions.addAll(toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values());
        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                            true, true
                            true, false
                            false, true
                            false, false
                            """)
    void nftTransfersHaveCorrectIsApprovalValue(boolean isApproval1, boolean isApproval2) {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        // mint transfer / transaction 1
        long mintTimestamp1 = 20L;
        TokenTransferList mintTransfer1 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_1));
        Transaction mintTransaction1 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_1));

        insertAndParseTransaction(mintTimestamp1, mintTransaction1, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L).addSerialNumbers(SERIAL_NUMBER_1);
            builder.addTokenTransferLists(mintTransfer1);
        });

        // mint transfer / transaction 2
        long mintTimestamp2 = 30L;
        TokenTransferList mintTransfer2 = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, List.of(SERIAL_NUMBER_2));
        Transaction mintTransaction2 =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, List.of(SERIAL_NUMBER_2));

        insertAndParseTransaction(mintTimestamp2, mintTransaction2, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addSerialNumbers(SERIAL_NUMBER_2);
            builder.addTokenTransferLists(mintTransfer2);
        });

        // token transfer
        Transaction transaction = buildTransaction(builder -> {
            // NFT transfer list 1
            TokenTransferList transferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_1)
                            .setIsApproval(isApproval1)
                            .build())
                    .build();

            // NFT transfer list 2
            TokenTransferList transferList2 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_2)
                            .setIsApproval(isApproval2)
                            .build())
                    .build();

            builder.getCryptoTransferBuilder().addTokenTransfers(transferList1).addTokenTransfers(transferList2);
        });

        long transferTimestamp = 40L;
        insertAndParseTransaction(transferTimestamp, transaction, builder -> {
            // NFT transfer list 1
            TokenTransferList transferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_1)
                            .build())
                    .build();

            // NFT transfer list 2
            TokenTransferList transferList2 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addNftTransfers(NftTransfer.newBuilder()
                            .setReceiverAccountID(RECEIVER)
                            .setSenderAccountID(PAYER)
                            .setSerialNumber(SERIAL_NUMBER_2)
                            .build())
                    .build();

            builder.addAllTokenTransferLists(List.of(transferList1, transferList2));
        });

        assertNftTransferInRepository(
                mintTimestamp1, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID));
        assertNftTransferInRepository(
                mintTimestamp2, domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID, isApproval1),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID, isApproval2));

        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .createdTimestamp(mintTimestamp1)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder()
                .createdTimestamp(mintTimestamp2)
                .serialNumber(SERIAL_NUMBER_2)
                .build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);

        var nft1History = nft1.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp1, transferTimestamp))
                .build();
        var nft2History = nft2.toBuilder()
                .accountId(PAYER_ACCOUNT_ID)
                .timestampRange(Range.closedOpen(mintTimestamp2, transferTimestamp))
                .build();
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrder(nft1History, nft2History);
    }

    @Test
    void nftTransferMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        // token transfer
        var transaction = tokenTransferTransaction();
        var transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .build();
        var transferList2 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_2)
                        .build())
                .build();

        long transferTimestamp = 25L;
        insertAndParseTransaction(
                transferTimestamp,
                transaction,
                builder -> builder.addAllTokenTransferLists(List.of(transferList1, transferList2)));

        assertNftTransferInRepository(
                transferTimestamp,
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(RECEIVER, PAYER, SERIAL_NUMBER_2, TOKEN_ID));
        var nft1 = Nft.builder()
                .accountId(EntityId.of(RECEIVER))
                .deleted(false)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(transferTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        var nft2 = nft1.toBuilder().serialNumber(SERIAL_NUMBER_2).build();
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void tokenTransfersMustHaveCorrectIsApprovalValue() {
        entityProperties.getPersist().setTrackAllowance(true);
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        // approve allowance for fungible token
        var allowanceAmount = INITIAL_SUPPLY / 4L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addTokenAllowances(TokenAllowance.newBuilder()
                        .setTokenId(TOKEN_ID)
                        .setOwner(PAYER2)
                        .setSpender(PAYER)
                        .setAmount(allowanceAmount)));
        insertAndParseTransaction(ALLOWANCE_TIMESTAMP, cryptoApproveAllowanceTransaction);

        TokenID tokenId2 = TokenID.newBuilder().setTokenNum(7).build();
        createTokenEntity(tokenId2, FUNGIBLE_COMMON, "MIRROR", 10L, false, false, false);

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();
        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-1000)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER2)
                        .setAmount(-100)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(1000)
                        .build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenId2)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(333)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(-333)
                        .build())
                .build();
        List<TokenTransferList> transferLists = List.of(transferList1, transferList2);

        // when
        Transaction transaction = buildTransaction(builder -> {
            TokenTransferList bodyTransferList1 = TokenTransferList.newBuilder()
                    .setToken(TOKEN_ID)
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER)
                            .setAmount(-600)
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER2)
                            .setAmount(-100) // Decrement from allowance amount
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(PAYER2)
                            .setAmount(-900) // Decrement from allowance amount
                            .setIsApproval(true)
                            .build())
                    .addTransfers(AccountAmount.newBuilder()
                            .setAccountID(accountId)
                            .setAmount(-333)
                            .build())
                    .build();
            builder.getCryptoTransferBuilder().addTokenTransfers(bodyTransferList1);
        });
        insertAndParseTransaction(
                TRANSFER_TIMESTAMP, transaction, builder -> builder.addAllTokenTransferLists(transferLists));

        // then
        assertTokenTransferInRepository(TOKEN_ID, PAYER, TRANSFER_TIMESTAMP, -1000, true);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, TRANSFER_TIMESTAMP, -100, true);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenTransferInRepository(tokenId2, PAYER, TRANSFER_TIMESTAMP, 333);
        assertTokenTransferInRepository(tokenId2, accountId, TRANSFER_TIMESTAMP, -333);
        assertTokenAllowanceInRepository(
                TOKEN_ID, PAYER2, PAYER, allowanceAmount, allowanceAmount - 1000L, ALLOWANCE_TIMESTAMP);
    }

    @Test
    void tokenTransferWithApprovalViaContract() {
        entityProperties.getPersist().setTrackAllowance(true);
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        // approve allowance for fungible token
        var allowanceAmount = INITIAL_SUPPLY / 4L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addTokenAllowances(TokenAllowance.newBuilder()
                        .setTokenId(TOKEN_ID)
                        .setOwner(PAYER2)
                        .setSpender(PAYER)
                        .setAmount(allowanceAmount)));
        insertAndParseTransaction(ALLOWANCE_TIMESTAMP, cryptoApproveAllowanceTransaction);

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();

        // when
        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(1000)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER2)
                        .setAmount(-1000)
                        .setIsApproval(true)
                        .build())
                .build();

        Transaction transaction =
                buildTransaction(builder -> builder.getCryptoTransferBuilder().addTokenTransfers(transferList1));

        List<TokenTransferList> transferLists = List.of(transferList1);
        insertAndParseTransaction(TRANSFER_TIMESTAMP, transaction, builder -> {
            builder.addAllTokenTransferLists(transferLists);
            buildContractFunctionResult(builder.getContractCallResultBuilder().setSenderId(PAYER));
        });

        // then
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, TRANSFER_TIMESTAMP, -1000, true);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenAllowanceInRepository(
                TOKEN_ID, PAYER2, PAYER, allowanceAmount, allowanceAmount - 1000L, ALLOWANCE_TIMESTAMP);
    }

    @Test
    void tokenWipe() {
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);

        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        TokenTransferList tokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, transferAmount));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount, Collections.emptyList());
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY - wipeAmount);
            builder.addTokenTransferLists(tokenTransfer);
        });

        // Verify
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY - wipeAmount, Token::getTotalSupply);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, wipeTimestamp, transferAmount);
        // History row should not be created
        assertThat(tokenHistoryRepository.count()).isZero();

        assertThat(contractLogRepository.findById(new ContractLog.Id(wipeTimestamp, 0)))
                .get()
                .returns(wipeTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(toTrimmedBytes(EntityId.of(PAYER2)), from(ContractLog::getTopic1))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(-transferAmount), from(ContractLog::getData));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(wipeTimestamp))
                .hasSize(1)
                .first()
                .returns(wipeTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenWipeNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        long mintTimestamp = 10L;
        TokenTransferList mintTransfer = nftTransfer(TOKEN_ID, PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_LIST);
        Transaction mintTransaction =
                tokenSupplyTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, true, 0, SERIAL_NUMBER_LIST);

        insertAndParseTransaction(mintTimestamp, mintTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(2L).addAllSerialNumbers(SERIAL_NUMBER_LIST);
            builder.addTokenTransferLists(mintTransfer);
        });

        // approve allowance for nft 1
        long approveAllowanceTimestamp = 12L;
        var cryptoApproveAllowanceTransaction = buildTransaction(b -> b.getCryptoApproveAllowanceBuilder()
                .addNftAllowances(NftAllowance.newBuilder()
                        .setOwner(PAYER)
                        .setTokenId(TOKEN_ID)
                        .addSerialNumbers(SERIAL_NUMBER_1)
                        .setSpender(SPENDER)));

        insertAndParseTransaction(approveAllowanceTimestamp, cryptoApproveAllowanceTransaction);

        var expectedNft1 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_1)
                .spender(EntityId.of(SPENDER).getId())
                .timestampRange(Range.atLeast(approveAllowanceTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findById(expectedNft1.getId())).hasValue(expectedNft1);

        long wipeTimestamp = 15L;
        TokenTransferList wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, PAYER2, List.of(SERIAL_NUMBER_1));
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(1L);
            builder.addTokenTransferLists(wipeTransfer);
        });
        expectedNft1.setAccountId(null);
        expectedNft1.setDeleted(true);
        expectedNft1.setTimestampLower(wipeTimestamp);
        expectedNft1.setSpender(null);
        var expectedNft2 = Nft.builder()
                .accountId(PAYER_ACCOUNT_ID)
                .createdTimestamp(mintTimestamp)
                .deleted(false)
                .metadata(METADATA)
                .serialNumber(SERIAL_NUMBER_2)
                .timestampRange(Range.atLeast(mintTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();

        // Verify
        assertNftTransferInRepository(
                mintTimestamp,
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_1, TOKEN_ID),
                domainNftTransfer(PAYER, DEFAULT_ACCOUNT_ID, SERIAL_NUMBER_2, TOKEN_ID));
        assertNftTransferInRepository(
                wipeTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, PAYER2, SERIAL_NUMBER_1, TOKEN_ID));

        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2);

        assertThat(contractLogRepository.findById(new ContractLog.Id(wipeTimestamp, 0)))
                .get()
                .returns(wipeTimestamp, from(ContractLog::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractLog::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getContractId))
                .returns(EntityId.of(TOKEN_ID), from(ContractLog::getRootContractId))
                .returns(TRANSFER_SIGNATURE, from(ContractLog::getTopic0))
                .returns(toTrimmedBytes(EntityId.of(PAYER2)), from(ContractLog::getTopic1))
                .returns(ArrayUtils.EMPTY_BYTE_ARRAY, from(ContractLog::getTopic2))
                .returns(toTrimmedBytes(SERIAL_NUMBER_1), from(ContractLog::getTopic3));

        assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(wipeTimestamp))
                .hasSize(1)
                .first()
                .returns(wipeTimestamp, from(ContractResult::getConsensusTimestamp))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getPayerAccountId))
                .returns(EntityId.of(TOKEN_ID).getId(), from(ContractResult::getContractId))
                .returns(PAYER_ACCOUNT_ID, from(ContractResult::getSenderId))
                .returns(Bytes.fromHexString("a9059cbb").toArray(), from(ContractResult::getFunctionParameters));
    }

    @Test
    void tokenWipeWithMissingToken() {
        Transaction transaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, 100L, null);
        insertAndParseTransaction(10L, transaction);

        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void tokenWipeNftMissingNft() {
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        long wipeTimestamp = 15L;
        var wipeTransfer = nftTransfer(TOKEN_ID, DEFAULT_ACCOUNT_ID, RECEIVER, List.of(SERIAL_NUMBER_1));
        var transaction = tokenWipeTransaction(TOKEN_ID, NON_FUNGIBLE_UNIQUE, 0, List.of(SERIAL_NUMBER_1));
        insertAndParseTransaction(wipeTimestamp, transaction, builder -> builder.addTokenTransferLists(wipeTransfer));

        // Verify
        assertNftTransferInRepository(
                wipeTimestamp, domainNftTransfer(DEFAULT_ACCOUNT_ID, RECEIVER, SERIAL_NUMBER_1, TOKEN_ID));
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(0L, Token::getTotalSupply);

        var expected = Nft.builder()
                .deleted(true)
                .serialNumber(SERIAL_NUMBER_1)
                .timestampRange(Range.atLeast(wipeTimestamp))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        assertThat(nftRepository.findAll()).containsExactly(expected);
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tokenCreateNftCollectionAssociateAllowanceUpdateMetadata(boolean singleRecordFile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                NON_FUNGIBLE_UNIQUE,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                0);

        // mint
        long mintTimestamp = CREATE_TIMESTAMP + 20L;
        var metadata = recordItemBuilder.bytes(16);
        var mintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.clear().setToken(TOKEN_ID).addMetadata(metadata))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(1).setNewTotalSupply(1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(mintTimestamp))
                        .addTokenTransferLists(TokenTransferList.newBuilder()
                                .setToken(TOKEN_ID)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setReceiverAccountID(PAYER)
                                        .setSerialNumber(1))))
                .build();

        // approve allowance
        var approveAllowanceTimestamp = mintTimestamp + 20L;
        var approveAllowanceRecordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> b.clear()
                        .addNftAllowances(NftAllowance.newBuilder()
                                .addSerialNumbers(1)
                                .setSpender(PAYER2)
                                .setDelegatingSpender(PAYER3)
                                .setTokenId(TOKEN_ID)))
                .transactionBodyWrapper(w -> w.setTransactionID(Utility.getTransactionId(PAYER)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(approveAllowanceTimestamp)))
                .build();

        var updateNftMetadataTimestamp = approveAllowanceTimestamp + 20L;
        var newMetadata = BytesValue.of(recordItemBuilder.bytes(16));
        var updateNftMetadataRecordItem = recordItemBuilder
                .tokenUpdateNfts()
                .transactionBody(b -> b.clearSerialNumbers()
                        .setToken(TOKEN_ID)
                        .addSerialNumbers(1L)
                        .setMetadata(newMetadata))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateNftMetadataTimestamp)))
                .build();

        var recordItems = List.of(mintRecordItem, approveAllowanceRecordItem, updateNftMetadataRecordItem);

        // when
        if (singleRecordFile) {
            parseRecordItemsAndCommit(recordItems);
        } else {
            recordItems.forEach(this::parseRecordItemAndCommit);
        }

        // then
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(1L, Token::getTotalSupply);

        assertThat(nftRepository.findById(new AbstractNft.Id(1L, DOMAIN_TOKEN_ID.getId())))
                .get()
                .returns(mintTimestamp, Nft::getCreatedTimestamp)
                .returns(DomainUtils.toBytes(newMetadata.getValue()), Nft::getMetadata)
                .returns(EntityId.of(PAYER3).getId(), Nft::getDelegatingSpender)
                .returns(EntityId.of(PAYER2).getId(), Nft::getSpender);

        var nftHistory = findHistory(Nft.class);
        assertThat(nftHistory)
                .hasSize(2)
                .satisfiesExactly(
                        // First history row written when allowance created, pre-allowance spender columns are null.
                        n -> assertThat(n)
                                .returns(null, Nft::getDelegatingSpender)
                                .returns(null, Nft::getSpender),
                        // Second history row written when NFT metadata was updated. Allowance set spender columns must
                        // be indicated.
                        n -> assertThat(n)
                                .returns(EntityId.of(PAYER3).getId(), Nft::getDelegatingSpender)
                                .returns(EntityId.of(PAYER2).getId(), Nft::getSpender));
    }

    @Test
    void tokenCreateAndAssociateAndWipeInSameRecordFile() {
        long transferAmount = -1000L;
        long wipeAmount = 100L;
        long wipeTimestamp = 10L;
        long newTotalSupply = INITIAL_SUPPLY - wipeAmount;

        // create token with a transfer
        Transaction createTransaction = tokenCreateTransaction(FUNGIBLE_COMMON, false, false, false, SYMBOL);
        TokenTransferList createTokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, INITIAL_SUPPLY));
        RecordItem createTokenRecordItem = getRecordItem(CREATE_TIMESTAMP, createTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(INITIAL_SUPPLY).setTokenID(TOKEN_ID);
            builder.addTokenTransferLists(createTokenTransfer);
        });

        // associate with token
        Transaction associateTransaction = tokenAssociate(List.of(TOKEN_ID), PAYER2);
        RecordItem associateRecordItem = getRecordItem(ASSOCIATE_TIMESTAMP, associateTransaction);

        // wipe amount from token with a transfer
        TokenTransferList wipeTokenTransfer = tokenTransferList(TOKEN_ID, accountAmount(PAYER2, transferAmount));
        Transaction wipeTransaction = tokenWipeTransaction(TOKEN_ID, FUNGIBLE_COMMON, wipeAmount, null);
        RecordItem wipeRecordItem = getRecordItem(wipeTimestamp, wipeTransaction, builder -> {
            builder.getReceiptBuilder().setNewTotalSupply(newTotalSupply);
            builder.addTokenTransferLists(wipeTokenTransfer);
        });

        // process all record items in a single file
        parseRecordItemsAndCommit(List.of(createTokenRecordItem, associateRecordItem, wipeRecordItem));

        // Verify token, tokenAccount and tokenTransfer
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, Token::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, Token::getKycStatus)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(newTotalSupply, Token::getTotalSupply);
        assertTokenAccountInRepository(
                TOKEN_ID,
                PAYER2,
                false,
                999000L,
                wipeTimestamp,
                ASSOCIATE_TIMESTAMP,
                true,
                null,
                null,
                ASSOCIATE_TIMESTAMP);
        assertThat(tokenTransferRepository.count()).isEqualTo(2L);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertTokenTransferInRepository(TOKEN_ID, PAYER2, wipeTimestamp, transferAmount);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = TokenAirdropStateEnum.class,
            names = {"CANCELLED", "CLAIMED"})
    void tokenAirdrop(TokenAirdropStateEnum airdropType) {
        // given
        long transferAmount = 100;
        long pendingAmount = 1000;
        long createTimestamp = 10L;
        var nftTokenId = TokenID.newBuilder().setTokenNum(1234L).build();

        var tokenCreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.setInitialSupply(INITIAL_SUPPLY)
                        .setTokenType(FUNGIBLE_COMMON)
                        .setTreasury(PAYER))
                .receipt(r -> r.setTokenID(TOKEN_ID))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(PAYER)
                                .setTokenId(TOKEN_ID))
                        .setConsensusTimestamp(TestUtils.toTimestamp(createTimestamp)))
                .build();
        parseRecordItemAndCommit(tokenCreateRecordItem);

        var tokenMintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.setToken(nftTokenId).addMetadata(DomainUtils.fromBytes(METADATA)))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(SERIAL_NUMBER_1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(createTimestamp + 1)))
                .build();
        parseRecordItemAndCommit(tokenMintRecordItem);

        // when
        long airdropTimestamp = 20L;

        // Airdrops that where directly transferred and not added to pending airdrops.
        var fungibleAirdrop = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-transferAmount)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER3)
                        .setAmount(transferAmount)
                        .build())
                .build();
        var nftAirdrop = TokenTransferList.newBuilder()
                .setToken(nftTokenId)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER3)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .build();
        var pendingFungibleAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(RECEIVER)
                        .setSenderId(PAYER)
                        .setFungibleTokenType(TOKEN_ID))
                .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                        .setAmount(pendingAmount)
                        .build());
        var protoNftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(SERIAL_NUMBER_1);
        var pendingNftAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(RECEIVER)
                        .setSenderId(PAYER)
                        .setNonFungibleToken(protoNftId));
        var tokenAirdrop = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(airdropTimestamp))
                        .clearNewPendingAirdrops()
                        .clearTokenTransferLists()
                        .addNewPendingAirdrops(pendingFungibleAirdrop)
                        .addNewPendingAirdrops(pendingNftAirdrop)
                        .addTokenTransferLists(fungibleAirdrop)
                        .addTokenTransferLists(nftAirdrop))
                .build();
        parseRecordItemAndCommit(tokenAirdrop);

        // then
        var expectedTransferFromPayer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-transferAmount)
                        .id(new TokenTransfer.Id(airdropTimestamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER))))
                .get();
        var expectedTransferToReceiver = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(transferAmount)
                        .id(new TokenTransfer.Id(airdropTimestamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER3))))
                .get();
        var expectedNftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.serialNumber(SERIAL_NUMBER_1)
                        .receiverAccountId(EntityId.of(PAYER3))
                        .senderAccountId(EntityId.of(PAYER))
                        .tokenId(EntityId.of(nftTokenId))
                        .isApproval(false))
                .get();
        var expectedPendingFungible = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(pendingAmount)
                        .receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .state(TokenAirdropStateEnum.PENDING)
                        .timestampRange(Range.atLeast(airdropTimestamp))
                        .tokenId(DOMAIN_TOKEN_ID.getId()))
                .get();
        var expectedPendingNft = domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .serialNumber(SERIAL_NUMBER_1)
                        .state(TokenAirdropStateEnum.PENDING)
                        .timestampRange(Range.atLeast(airdropTimestamp))
                        .tokenId(EntityId.of(nftTokenId).getId()))
                .get();

        assertThat(tokenTransferRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("isApproval", "payerAccountId")
                .containsExactlyInAnyOrderElementsOf(List.of(expectedTransferFromPayer, expectedTransferToReceiver));
        assertNftTransferInRepository(airdropTimestamp, expectedNftTransfer);
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));
        assertThat(findHistory(TokenAirdrop.class)).isEmpty();
        assertThat(tokenAccountRepository.count()).isEqualTo(2);

        // when
        long updateTimestamp = 30L;
        var pendingFungibleAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setFungibleTokenType(TOKEN_ID)
                .build();
        var pendingNftAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setNonFungibleToken(protoNftId)
                .build();

        var expectedState = TokenAirdropStateEnum.CANCELLED;
        RecordItemBuilder.Builder<?> updateAirdrop = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops()
                        .addPendingAirdrops(pendingFungibleAirdropId)
                        .addPendingAirdrops(pendingNftAirdropId))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        if (airdropType == TokenAirdropStateEnum.CLAIMED) {
            expectedState = TokenAirdropStateEnum.CLAIMED;
            updateAirdrop = recordItemBuilder
                    .tokenClaimAirdrop()
                    .transactionBody(b -> b.clearPendingAirdrops()
                            .addPendingAirdrops(pendingFungibleAirdropId)
                            .addPendingAirdrops(pendingNftAirdropId))
                    .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        }
        parseRecordItemAndCommit(updateAirdrop.build());

        // then
        expectedPendingFungible.setTimestampRange(Range.closedOpen(airdropTimestamp, updateTimestamp));
        expectedPendingNft.setTimestampRange(Range.closedOpen(airdropTimestamp, updateTimestamp));
        assertThat(findHistory(TokenAirdrop.class))
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));

        expectedPendingFungible.setState(expectedState);
        expectedPendingFungible.setTimestampRange(Range.atLeast(updateTimestamp));
        expectedPendingNft.setState(expectedState);
        expectedPendingNft.setTimestampRange(Range.atLeast(updateTimestamp));
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));

        if (airdropType == TokenAirdropStateEnum.CLAIMED) {
            var tokenAccountFungible = tokenAccount(TOKEN_ID, RECEIVER, updateTimestamp);
            var tokenAccountNonFungible = tokenAccount(protoNftId.getTokenID(), RECEIVER, updateTimestamp);
            assertThat(tokenAccountRepository.findAll())
                    .hasSize(4)
                    .contains(tokenAccountFungible, tokenAccountNonFungible);
        } else {
            assertThat(tokenAccountRepository.count()).isEqualTo(2);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = TokenAirdropStateEnum.class,
            names = {"CANCELLED", "CLAIMED"})
    void tokenAirdropUpdateState(TokenAirdropStateEnum airdropType) {
        // given
        long pendingAmount = 1000;
        long createTimestamp = 10L;
        var nftTokenId = TokenID.newBuilder().setTokenNum(1234L).build();

        var tokenCreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.setInitialSupply(INITIAL_SUPPLY)
                        .setTokenType(FUNGIBLE_COMMON)
                        .setTreasury(PAYER))
                .receipt(r -> r.setTokenID(TOKEN_ID))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(PAYER)
                                .setTokenId(TOKEN_ID))
                        .setConsensusTimestamp(TestUtils.toTimestamp(createTimestamp)))
                .build();
        parseRecordItemAndCommit(tokenCreateRecordItem);
        var tokenAccounts = tokenAccountRepository.findAll();

        var tokenMintRecordItem = recordItemBuilder
                .tokenMint()
                .transactionBody(b -> b.setToken(nftTokenId).addMetadata(DomainUtils.fromBytes(METADATA)))
                .receipt(r -> r.clearSerialNumbers().addSerialNumbers(SERIAL_NUMBER_1))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(createTimestamp + 1)))
                .build();
        parseRecordItemAndCommit(tokenMintRecordItem);

        // when
        long airdropTimestamp = 20L;
        var pendingFungibleAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(RECEIVER)
                        .setSenderId(PAYER)
                        .setFungibleTokenType(TOKEN_ID))
                .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                        .setAmount(pendingAmount)
                        .build());
        var protoNftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(SERIAL_NUMBER_1);
        var pendingNftAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(RECEIVER)
                        .setSenderId(PAYER)
                        .setNonFungibleToken(protoNftId));
        var tokenAirdrop = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(airdropTimestamp))
                        .clearNewPendingAirdrops()
                        .addNewPendingAirdrops(pendingFungibleAirdrop)
                        .addNewPendingAirdrops(pendingNftAirdrop))
                .build();

        // then
        long updateTimestamp = 30L;
        var expectedPendingFungible = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                .customize(t -> t.amount(pendingAmount)
                        .receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .timestampRange(Range.atLeast(airdropTimestamp))
                        .tokenId(DOMAIN_TOKEN_ID.getId()))
                .get();
        var expectedPendingNft = domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .serialNumber(SERIAL_NUMBER_1)
                        .timestampRange(Range.atLeast(airdropTimestamp))
                        .tokenId(EntityId.of(nftTokenId).getId()))
                .get();

        var pendingFungibleAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setFungibleTokenType(TOKEN_ID)
                .build();
        var pendingNftAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setNonFungibleToken(protoNftId)
                .build();

        var expectedState = TokenAirdropStateEnum.CANCELLED;
        RecordItemBuilder.Builder<?> updateAirdrop = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops()
                        .addPendingAirdrops(pendingFungibleAirdropId)
                        .addPendingAirdrops(pendingNftAirdropId))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        if (airdropType == TokenAirdropStateEnum.CLAIMED) {
            expectedState = TokenAirdropStateEnum.CLAIMED;
            updateAirdrop = recordItemBuilder
                    .tokenClaimAirdrop()
                    .transactionBody(b -> b.clearPendingAirdrops()
                            .addPendingAirdrops(pendingFungibleAirdropId)
                            .addPendingAirdrops(pendingNftAirdropId))
                    .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        }

        // when
        parseRecordItemsAndCommit(List.of(tokenAirdrop, updateAirdrop.build()));

        // then
        expectedPendingFungible.setTimestampRange(Range.closedOpen(airdropTimestamp, updateTimestamp));
        expectedPendingNft.setTimestampRange(Range.closedOpen(airdropTimestamp, updateTimestamp));
        assertThat(findHistory(TokenAirdrop.class))
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));

        expectedPendingFungible.setState(expectedState);
        expectedPendingFungible.setTimestampRange(Range.atLeast(updateTimestamp));
        expectedPendingNft.setState(expectedState);
        expectedPendingNft.setTimestampRange(Range.atLeast(updateTimestamp));
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));

        if (airdropType == TokenAirdropStateEnum.CLAIMED) {
            var tokenAccountFungible = tokenAccount(TOKEN_ID, RECEIVER, updateTimestamp);
            var tokenAccountNonFungible = tokenAccount(protoNftId.getTokenID(), RECEIVER, updateTimestamp);
            assertThat(tokenAccountRepository.findAll())
                    .hasSize(4)
                    .containsAnyElementsOf(tokenAccounts)
                    .contains(tokenAccountFungible, tokenAccountNonFungible);
        } else {
            assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAccounts);
        }
    }

    private TokenAccount tokenAccount(TokenID tokenId, AccountID accountId, long consensusTimestamp) {
        var tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(EntityId.of(accountId).getId());
        tokenAccount.setAssociated(true);
        tokenAccount.setAutomaticAssociation(false);
        tokenAccount.setBalance(0L);
        tokenAccount.setBalanceTimestamp(consensusTimestamp);
        tokenAccount.setClaim(true);
        tokenAccount.setCreatedTimestamp(consensusTimestamp);
        tokenAccount.setTimestampLower(consensusTimestamp);
        tokenAccount.setTokenId(EntityId.of(tokenId).getId());
        return tokenAccount;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = TokenAirdropStateEnum.class,
            names = {"CANCELLED", "CLAIMED"})
    void tokenAirdropPartialData(TokenAirdropStateEnum airdropType) {
        // given
        // when a claim or cancel occurs but there is no prior pending airdrop
        long updateTimestamp = 30L;
        var pendingFungibleAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setFungibleTokenType(TOKEN_ID)
                .build();
        var nftTokenId = domainBuilder.entityNum(1234L).toTokenID();
        var protoNftId = NftID.newBuilder().setTokenID(nftTokenId).setSerialNumber(SERIAL_NUMBER_1);
        var pendingNftAirdropId = PendingAirdropId.newBuilder()
                .setReceiverId(RECEIVER)
                .setSenderId(PAYER)
                .setNonFungibleToken(protoNftId)
                .build();

        var expectedState = TokenAirdropStateEnum.CANCELLED;
        RecordItemBuilder.Builder<?> updateAirdrop;
        if (airdropType == TokenAirdropStateEnum.CANCELLED) {
            updateAirdrop = recordItemBuilder
                    .tokenCancelAirdrop()
                    .transactionBody(b -> b.clearPendingAirdrops()
                            .addPendingAirdrops(pendingFungibleAirdropId)
                            .addPendingAirdrops(pendingNftAirdropId))
                    .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        } else {
            expectedState = TokenAirdropStateEnum.CLAIMED;
            updateAirdrop = recordItemBuilder
                    .tokenClaimAirdrop()
                    .transactionBody(b -> b.clearPendingAirdrops()
                            .addPendingAirdrops(pendingFungibleAirdropId)
                            .addPendingAirdrops(pendingNftAirdropId))
                    .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(updateTimestamp)));
        }
        parseRecordItemAndCommit(updateAirdrop.build());

        // then
        var expectedPendingFungible = domainBuilder
                .tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON)
                // Amount will be null when there is no pending airdrop
                .customize(t -> t.amount(null)
                        .receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .timestampRange(Range.atLeast(updateTimestamp))
                        .tokenId(DOMAIN_TOKEN_ID.getId()))
                .get();
        var expectedPendingNft = domainBuilder
                .tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .customize(t -> t.receiverAccountId(EntityId.of(RECEIVER).getId())
                        .senderAccountId(PAYER_ACCOUNT_ID.getId())
                        .serialNumber(SERIAL_NUMBER_1)
                        .timestampRange(Range.atLeast(updateTimestamp))
                        .tokenId(EntityId.of(nftTokenId).getId()))
                .get();
        expectedPendingFungible.setState(expectedState);
        expectedPendingNft.setState(expectedState);
        assertThat(tokenAirdropRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(List.of(expectedPendingFungible, expectedPendingNft));
        assertThat(findHistory(TokenAirdrop.class)).isEmpty();

        if (airdropType == TokenAirdropStateEnum.CLAIMED) {
            assertThat(tokenAccountRepository.count()).isEqualTo(2);
        } else {
            assertThat(tokenAccountRepository.count()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenRejectFungible(boolean hasOwner) {
        long amount = 1000;
        long transferTimestamp = 10L;
        var tokenTransfer = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-amount)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(RECEIVER)
                        .setAmount(amount)
                        .build())
                .build();

        var tokenCreateRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.setInitialSupply(INITIAL_SUPPLY)
                        .setTokenType(FUNGIBLE_COMMON)
                        .setTreasury(PAYER))
                .receipt(r -> r.setTokenID(TOKEN_ID))
                .record(r -> r.addAutomaticTokenAssociations(TokenAssociation.newBuilder()
                                .setAccountId(PAYER)
                                .setTokenId(TOKEN_ID))
                        .addTokenTransferLists(tokenTransfer)
                        .setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp)))
                .build();

        parseRecordItemAndCommit(tokenCreateRecordItem);

        // when
        long rejectTimestamp = 20L;
        var tokenRejectTransfer = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(amount)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(RECEIVER)
                        .setAmount(-amount)
                        .build())
                .build();

        var tokenReject = recordItemBuilder
                .tokenReject()
                .transactionBody(b -> {
                    if (hasOwner) {
                        b.setOwner(RECEIVER);
                    } else {
                        b.clearOwner();
                    }
                    b.addRejections(TokenReference.newBuilder()
                            .setFungibleToken(TOKEN_ID)
                            .build());
                })
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(rejectTimestamp))
                        .clearTokenTransferLists()
                        .addTokenTransferLists(tokenRejectTransfer))
                .build();

        parseRecordItemAndCommit(tokenReject);

        // then
        var expectedTransferFromPayer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-amount)
                        .id(new TokenTransfer.Id(transferTimestamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER))))
                .get();
        var expectedTransferToReceiver = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(amount)
                        .id(new TokenTransfer.Id(transferTimestamp, EntityId.of(TOKEN_ID), EntityId.of(RECEIVER))))
                .get();
        var expectedRejectToTreasury = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(amount)
                        .id(new TokenTransfer.Id(rejectTimestamp, EntityId.of(TOKEN_ID), EntityId.of(PAYER))))
                .get();
        var expectedRejectFromReceiver = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(-amount)
                        .id(new TokenTransfer.Id(rejectTimestamp, EntityId.of(TOKEN_ID), EntityId.of(RECEIVER))))
                .get();

        assertThat(tokenTransferRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("isApproval", "payerAccountId")
                .containsExactlyInAnyOrderElementsOf(List.of(
                        expectedTransferFromPayer,
                        expectedTransferToReceiver,
                        expectedRejectToTreasury,
                        expectedRejectFromReceiver));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenRejectNft(boolean hasOwner) {
        long transferTimestamp = 10L;
        var nftTransfer = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(RECEIVER)
                        .setSenderAccountID(PAYER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .build();

        var cryptoTransferRecordItem = recordItemBuilder
                .cryptoTransfer()
                .transactionBody(b -> b.addTokenTransfers(nftTransfer))
                .record(r -> r.addTokenTransferLists(nftTransfer)
                        .setConsensusTimestamp(TestUtils.toTimestamp(transferTimestamp)))
                .build();

        parseRecordItemAndCommit(cryptoTransferRecordItem);

        var nftId = new Id(SERIAL_NUMBER_1, DOMAIN_TOKEN_ID.getId());
        assertThat(nftRepository.findById(nftId))
                .get()
                .returns(EntityId.of(RECEIVER), Nft::getAccountId)
                .returns(transferTimestamp, Nft::getTimestampLower);
        var expectedNftTransfer = domainBuilder
                .nftTransfer()
                .customize(t -> t.receiverAccountId(EntityId.of(RECEIVER))
                        .senderAccountId(PAYER_ACCOUNT_ID)
                        .serialNumber(SERIAL_NUMBER_1)
                        .tokenId(DOMAIN_TOKEN_ID))
                .get();
        assertNftTransferInRepository(transferTimestamp, expectedNftTransfer);

        // when
        long rejectTimestamp = 20L;
        var tokenRejectTransfer = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(PAYER)
                        .setSenderAccountID(RECEIVER)
                        .setSerialNumber(SERIAL_NUMBER_1)
                        .build())
                .build();

        var protoNftId = NftID.newBuilder()
                .setTokenID(TOKEN_ID)
                .setSerialNumber(SERIAL_NUMBER_1)
                .build();
        var tokenReject = recordItemBuilder
                .tokenReject()
                .transactionBody(b -> {
                    if (hasOwner) {
                        b.setOwner(RECEIVER);
                    } else {
                        b.clearOwner();
                    }
                    b.addRejections(
                            TokenReference.newBuilder().setNft(protoNftId).build());
                })
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(rejectTimestamp))
                        .clearTokenTransferLists()
                        .addTokenTransferLists(tokenRejectTransfer))
                .build();

        parseRecordItemAndCommit(tokenReject);

        // then
        assertThat(nftRepository.findById(nftId))
                .get()
                .returns(PAYER_ACCOUNT_ID, Nft::getAccountId)
                .returns(rejectTimestamp, Nft::getTimestampLower);
        expectedNftTransfer.setReceiverAccountId(PAYER_ACCOUNT_ID);
        expectedNftTransfer.setSenderAccountId(EntityId.of(RECEIVER));
        assertNftTransferInRepository(rejectTimestamp, expectedNftTransfer);
    }

    void tokenCreate(
            List<CustomFee> customFees,
            boolean freezeDefault,
            boolean freezeKey,
            boolean kycKey,
            boolean pauseKey,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus,
            TokenPauseStatusEnum expectedPauseStatus,
            List<TokenAccount> expectedTokenAccounts,
            List<EntityId> autoAssociatedAccounts) {
        // given
        Entity expected = createEntity(
                DOMAIN_TOKEN_ID,
                TOKEN,
                TOKEN_REF_KEY,
                PAYER_ACCOUNT_ID.getId(),
                AUTO_RENEW_PERIOD,
                false,
                EXPIRY_NS,
                TOKEN_CREATE_MEMO,
                CREATE_TIMESTAMP,
                CREATE_TIMESTAMP);

        // when
        createTokenEntity(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                freezeDefault,
                freezeKey,
                kycKey,
                pauseKey,
                customFees,
                autoAssociatedAccounts);

        // then
        assertEquals(1L, entityRepository.count());
        assertEntity(expected);

        // verify token
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(expectedFreezeStatus, Token::getFreezeStatus)
                .returns(expectedKycStatus, Token::getKycStatus)
                .returns(expectedPauseStatus, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(INITIAL_SUPPLY, Token::getTotalSupply);
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedTokenAccounts);
        assertTokenTransferInRepository(TOKEN_ID, PAYER, CREATE_TIMESTAMP, INITIAL_SUPPLY);
        assertCustomFeesInDb(customFees, Collections.emptyList());
        assertThat(tokenTransferRepository.count()).isEqualTo(1L);
    }

    void tokenTransfer(
            List<AssessedCustomFee> assessedCustomFees,
            List<com.hederahashgraph.api.proto.java.AssessedCustomFee> protoAssessedCustomFees,
            boolean hasAutoTokenAssociations,
            boolean isPrecompile) {
        // given
        createAndAssociateToken(
                TOKEN_ID,
                FUNGIBLE_COMMON,
                SYMBOL,
                CREATE_TIMESTAMP,
                ASSOCIATE_TIMESTAMP,
                PAYER2,
                false,
                false,
                false,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                TokenPauseStatusEnum.NOT_APPLICABLE,
                INITIAL_SUPPLY);
        TokenID tokenId2 = TokenID.newBuilder().setTokenNum(7).build();
        String symbol2 = "MIRROR";
        createTokenEntity(tokenId2, FUNGIBLE_COMMON, symbol2, 10L, false, false, false);

        var existingEntityTransactions = Streams.stream(entityTransactionRepository.findAll())
                .collect(Collectors.toMap(EntityTransaction::getId, Function.identity()));

        AccountID accountId = AccountID.newBuilder().setAccountNum(1).build();

        // token transfer
        Transaction transaction = tokenTransferTransaction();

        TokenTransferList transferList1 = TokenTransferList.newBuilder()
                .setToken(TOKEN_ID)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(-1000)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(1000)
                        .build())
                .build();
        TokenTransferList transferList2 = TokenTransferList.newBuilder()
                .setToken(tokenId2)
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(PAYER)
                        .setAmount(333)
                        .build())
                .addTransfers(AccountAmount.newBuilder()
                        .setAccountID(accountId)
                        .setAmount(-333)
                        .build())
                .build();
        List<TokenTransferList> transferLists = List.of(transferList1, transferList2);

        // token treasury associations <TOKEN_ID, PAYER> and <tokenId2, PAYER> are created in the token create
        // transaction and they are not auto associations; the two token transfers' <token, recipient> pairs are
        // <TOKEN_ID, accountId> and <tokenId2, PAYER>, since <tokenId2, PAYER> already exists, only
        // <TOKEN_ID accountId> will be auto associated
        var autoTokenAssociation = TokenAssociation.newBuilder()
                .setAccountId(accountId)
                .setTokenId(TOKEN_ID)
                .build();

        var autoTokenAccount = TokenAccount.builder()
                .accountId(EntityId.of(accountId).getId())
                .associated(true)
                .automaticAssociation(true)
                .balance(1000L)
                .balanceTimestamp(TRANSFER_TIMESTAMP)
                .createdTimestamp(TRANSFER_TIMESTAMP)
                .timestampRange(Range.atLeast(TRANSFER_TIMESTAMP))
                .tokenId(DOMAIN_TOKEN_ID.getId())
                .build();
        List<TokenAccount> expectedAutoAssociatedTokenAccounts =
                hasAutoTokenAssociations ? List.of(autoTokenAccount) : Collections.emptyList();

        // when
        AtomicReference<ContractFunctionResult> contractFunctionResultAtomic = new AtomicReference<>();
        var recordItem = insertAndParseTransaction(TRANSFER_TIMESTAMP, transaction, builder -> {
            builder.addAllTokenTransferLists(transferLists).addAllAssessedCustomFees(protoAssessedCustomFees);
            if (hasAutoTokenAssociations) {
                builder.addAutomaticTokenAssociations(autoTokenAssociation);
            }
            if (isPrecompile) {
                buildContractFunctionResult(builder.getContractCallResultBuilder());
                contractFunctionResultAtomic.set(builder.getContractCallResult());
            }
        });

        // then
        assertTokenTransferInRepository(TOKEN_ID, PAYER, TRANSFER_TIMESTAMP, -1000);
        assertTokenTransferInRepository(TOKEN_ID, accountId, TRANSFER_TIMESTAMP, 1000);
        assertTokenTransferInRepository(tokenId2, PAYER, TRANSFER_TIMESTAMP, 333);
        assertTokenTransferInRepository(tokenId2, accountId, TRANSFER_TIMESTAMP, -333);
        assertAssessedCustomFeesInDb(assessedCustomFees);
        assertThat(tokenAccountRepository.findAll())
                .filteredOn(TokenAccount::getAutomaticAssociation)
                .containsExactlyInAnyOrderElementsOf(expectedAutoAssociatedTokenAccounts);

        if (isPrecompile) {
            assertContractResult(TRANSFER_TIMESTAMP, contractFunctionResultAtomic.get());
        }

        var entityIds = Lists.newArrayList(
                recordItem.getPayerAccountId(),
                EntityId.of(recordItem.getTransactionBody().getNodeAccountID()),
                EntityId.of(accountId),
                EntityId.of(PAYER),
                EntityId.of(TOKEN_ID),
                EntityId.of(tokenId2));
        assessedCustomFees.forEach(assessedCustomFee -> {
            entityIds.add(EntityId.of(assessedCustomFee.getCollectorAccountId()));
            entityIds.add(assessedCustomFee.getTokenId());
            assessedCustomFee.getEffectivePayerAccountIds().forEach(id -> entityIds.add(EntityId.of(id)));
        });
        if (isPrecompile) {
            entityIds.add(EntityId.of(CONTRACT_ID));
            entityIds.add(EntityId.of(CREATED_CONTRACT_ID));
        }
        var expectedEntityTransactions = new HashMap<>(existingEntityTransactions);
        toEntityTransactions(
                        recordItem, entityIds, entityProperties.getPersist().getEntityTransactionExclusion())
                .values()
                .forEach(e -> expectedEntityTransactions.put(e.getId(), e));

        assertThat(entityTransactionRepository.findAll())
                .containsExactlyInAnyOrderElementsOf(expectedEntityTransactions.values());
    }

    private RecordItem getRecordItem(long consensusTimestamp, Transaction transaction) {
        return getRecordItem(consensusTimestamp, transaction, builder -> {});
    }

    private RecordItem getRecordItem(
            long consensusTimestamp, Transaction transaction, Consumer<TransactionRecord.Builder> customBuilder) {
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                builder -> {
                    builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));
                    customBuilder.accept(builder);
                },
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        return RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
    }

    private RecordItem insertAndParseTransaction(long consensusTimestamp, Transaction transaction) {
        return insertAndParseTransaction(consensusTimestamp, transaction, builder -> {});
    }

    private RecordItem insertAndParseTransaction(
            long consensusTimestamp, Transaction transaction, Consumer<TransactionRecord.Builder> customBuilder) {
        var recordItem = getRecordItem(consensusTimestamp, transaction, customBuilder);
        parseRecordItemAndCommit(recordItem);
        assertTransactionInRepository(ResponseCodeEnum.SUCCESS, consensusTimestamp, null);
        return recordItem;
    }

    private org.hiero.mirror.common.domain.token.NftTransfer domainNftTransfer(
            AccountID receiver, AccountID sender, long serialNumber, TokenID tokenId) {
        return domainNftTransfer(receiver, sender, serialNumber, tokenId, false);
    }

    private org.hiero.mirror.common.domain.token.NftTransfer domainNftTransfer(
            AccountID receiver, AccountID sender, long serialNumber, TokenID tokenId, boolean isApproval) {
        var receiverEntityId = receiver.equals(DEFAULT_ACCOUNT_ID) ? null : EntityId.of(receiver);
        var senderEntityId = sender.equals(DEFAULT_ACCOUNT_ID) ? null : EntityId.of(sender);

        return org.hiero.mirror.common.domain.token.NftTransfer.builder()
                .isApproval(isApproval)
                .receiverAccountId(receiverEntityId)
                .senderAccountId(senderEntityId)
                .serialNumber(serialNumber)
                .tokenId(EntityId.of(tokenId))
                .build();
    }

    private Transaction tokenCreateTransaction(
            TokenType tokenType,
            boolean freezeDefault,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            String symbol,
            List<CustomFee> customFees) {
        return buildTransaction(builder -> {
            builder.getTokenCreationBuilder()
                    .setAdminKey(TOKEN_REF_KEY)
                    .setAutoRenewAccount(PAYER)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                    .setDecimals(1000)
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFreezeDefault(freezeDefault)
                    .setMemo(TOKEN_CREATE_MEMO)
                    .setName(symbol + "_token_name")
                    .setSupplyKey(TOKEN_REF_KEY)
                    .setSupplyType(TokenSupplyType.INFINITE)
                    .setSymbol(symbol)
                    .setTokenType(tokenType)
                    .setTreasury(PAYER)
                    .setWipeKey(TOKEN_REF_KEY)
                    .addAllCustomFees(convertCustomFees(customFees));

            if (tokenType == FUNGIBLE_COMMON) {
                builder.getTokenCreationBuilder().setInitialSupply(INITIAL_SUPPLY);
            }

            if (setFreezeKey) {
                builder.getTokenCreationBuilder().setFreezeKey(TOKEN_REF_KEY);
            }

            if (setKycKey) {
                builder.getTokenCreationBuilder().setKycKey(TOKEN_REF_KEY);
            }

            if (setPauseKey) {
                builder.getTokenCreationBuilder().setPauseKey(TOKEN_REF_KEY);
            }
        });
    }

    private Transaction tokenCreateTransaction(
            TokenType tokenType, boolean setFreezeKey, boolean setKycKey, boolean setPauseKey, String symbol) {
        return tokenCreateTransaction(
                tokenType, false, setFreezeKey, setKycKey, setPauseKey, symbol, Collections.emptyList());
    }

    private Transaction tokenUpdateTransaction(
            TokenID tokenId, String symbol, String memo, Key newKey, AccountID autoRenewAccount, AccountID treasury) {
        return buildTransaction(builder -> {
            builder.getTokenUpdateBuilder()
                    .setAdminKey(newKey)
                    .setAutoRenewPeriod(Duration.newBuilder().setSeconds(TOKEN_UPDATE_AUTO_RENEW_PERIOD))
                    .setExpiry(EXPIRY_TIMESTAMP)
                    .setFeeScheduleKey(newKey)
                    .setFreezeKey(newKey)
                    .setKycKey(newKey)
                    .setMemo(StringValue.of(memo))
                    .setName(symbol + "_update_name")
                    .setPauseKey(newKey)
                    .setSupplyKey(newKey)
                    .setSymbol(symbol)
                    .setToken(tokenId)
                    .setTreasury(treasury)
                    .setWipeKey(newKey);
            if (autoRenewAccount != null) {
                builder.getTokenUpdateBuilder().setAutoRenewAccount(autoRenewAccount);
            }
        });
    }

    private Transaction tokenAssociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder ->
                builder.getTokenAssociateBuilder().setAccount(accountID).addAllTokens(tokenIDs));
    }

    private Transaction tokenDissociate(List<TokenID> tokenIDs, AccountID accountID) {
        return buildTransaction(builder ->
                builder.getTokenDissociateBuilder().setAccount(accountID).addAllTokens(tokenIDs));
    }

    private Transaction tokenDeleteTransaction(TokenID tokenID) {
        return buildTransaction(builder -> builder.getTokenDeletionBuilder().setToken(tokenID));
    }

    private Transaction tokenFreezeTransaction(TokenID tokenID, boolean freeze) {
        Transaction transaction = null;
        if (freeze) {
            transaction = buildTransaction(
                    builder -> builder.getTokenFreezeBuilder().setToken(tokenID).setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder ->
                    builder.getTokenUnfreezeBuilder().setToken(tokenID).setAccount(PAYER2));
        }

        return transaction;
    }

    private Transaction tokenPauseTransaction(TokenID tokenID, boolean pause) {
        Transaction transaction;
        if (pause) {
            transaction =
                    buildTransaction(builder -> builder.getTokenPauseBuilder().setToken(tokenID));
        } else {
            transaction =
                    buildTransaction(builder -> builder.getTokenUnpauseBuilder().setToken(tokenID));
        }

        return transaction;
    }

    private Transaction tokenKycTransaction(TokenID tokenId, boolean kyc) {
        Transaction transaction;
        if (kyc) {
            transaction = buildTransaction(builder ->
                    builder.getTokenGrantKycBuilder().setToken(tokenId).setAccount(PAYER2));
        } else {
            transaction = buildTransaction(builder ->
                    builder.getTokenRevokeKycBuilder().setToken(tokenId).setAccount(PAYER2));
        }

        return transaction;
    }

    private Transaction tokenSupplyTransaction(
            TokenID tokenId, TokenType tokenType, boolean mint, long amount, List<Long> serialNumbers) {
        Transaction transaction;
        if (mint) {
            transaction = buildTransaction(builder -> {
                builder.getTokenMintBuilder().setToken(tokenId);

                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenMintBuilder().setAmount(amount);
                } else {
                    builder.getTokenMintBuilder()
                            .addAllMetadata(Collections.nCopies(serialNumbers.size(), DomainUtils.fromBytes(METADATA)));
                }
            });
        } else {
            transaction = buildTransaction(builder -> {
                builder.getTokenBurnBuilder().setToken(tokenId);
                if (tokenType == FUNGIBLE_COMMON) {
                    builder.getTokenBurnBuilder().setAmount(amount);
                } else {
                    builder.getTokenBurnBuilder().addAllSerialNumbers(serialNumbers);
                }
            });
        }

        return transaction;
    }

    private Transaction tokenWipeTransaction(
            TokenID tokenID, TokenType tokenType, long amount, List<Long> serialNumbers) {
        return buildTransaction(builder -> {
            builder.getTokenWipeBuilder().setToken(tokenID).setAccount(PAYER).build();
            if (tokenType == FUNGIBLE_COMMON) {
                builder.getTokenWipeBuilder().setAmount(amount);
            } else {
                builder.getTokenWipeBuilder().addAllSerialNumbers(serialNumbers);
            }
        });
    }

    private Transaction tokenTransferTransaction() {
        return buildTransaction(TransactionBody.Builder::getCryptoTransferBuilder);
    }

    private void assertNftTransferInRepository(
            long consensusTimestamp, org.hiero.mirror.common.domain.token.NftTransfer... nftTransfers) {
        assertThat(transactionRepository.findById(consensusTimestamp))
                .get()
                .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getNftTransfer)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(nftTransfers));
    }

    private void assertTokenAccountInRepository(
            TokenID tokenID,
            AccountID accountId,
            Boolean automaticAssociation,
            long balance,
            Long balanceTimestamp,
            Long createdTimestamp,
            boolean associated,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            long timestampLowerBound) {
        var expected = TokenAccount.builder()
                .accountId(EntityId.of(accountId).getId())
                .associated(associated)
                .automaticAssociation(automaticAssociation)
                .balance(balance)
                .balanceTimestamp(balanceTimestamp)
                .createdTimestamp(createdTimestamp)
                .freezeStatus(freezeStatus)
                .kycStatus(kycStatus)
                .timestampRange(Range.atLeast(timestampLowerBound))
                .tokenId(EntityId.of(tokenID).getId())
                .build();

        assertThat(tokenAccountRepository.findById(expected.getId())).hasValue(expected);
    }

    private void assertTokenTransferInRepository(
            TokenID tokenID, AccountID accountID, long consensusTimestamp, long amount) {
        assertTokenTransferInRepository(tokenID, accountID, consensusTimestamp, amount, false);
    }

    private void assertTokenTransferInRepository(
            TokenID tokenID, AccountID accountID, long consensusTimestamp, long amount, boolean isApproval) {
        var expected = domainBuilder
                .tokenTransfer()
                .customize(t -> t.amount(amount)
                        .id(new TokenTransfer.Id(consensusTimestamp, EntityId.of(tokenID), EntityId.of(accountID)))
                        .isApproval(isApproval)
                        .payerAccountId(PAYER_ACCOUNT_ID))
                .get();
        assertThat(tokenTransferRepository.findById(expected.getId())).get().isEqualTo(expected);
    }

    private void assertCustomFeesInDb(List<CustomFee> expected, List<CustomFee> expectedHistory) {
        assertThat(customFeeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(findHistory(CustomFee.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private void assertAssessedCustomFeesInDb(List<AssessedCustomFee> expected) {
        var actual = jdbcTemplate.query("select * from assessed_custom_fee", rowMapper(AssessedCustomFee.class));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertContractResult(long timestamp, ContractFunctionResult contractFunctionResult) {
        ObjectAssert<ContractResult> contractResult = assertThat(contractResultRepository.findAll())
                .filteredOn(c -> c.getConsensusTimestamp().equals(timestamp))
                .hasSize(1)
                .first()
                .returns(EntityId.of(CONTRACT_ID).getId(), ContractResult::getContractId);

        contractResult
                .returns(contractFunctionResult.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(contractFunctionResult.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(timestamp, ContractResult::getConsensusTimestamp)
                .returns(contractFunctionResult.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(contractFunctionResult.getGasUsed(), ContractResult::getGasUsed);
    }

    private void assertTokenAllowanceInRepository(
            TokenID tokenId,
            AccountID owner,
            AccountID spender,
            long amountGranted,
            long amount,
            long allowanceTimestamp) {
        var expected = domainBuilder
                .tokenAllowance()
                .customize(a -> a.amountGranted(amountGranted)
                        .amount(amount)
                        .tokenId(EntityId.of(tokenId).getId())
                        .owner(EntityId.of(owner).getId())
                        .spender(EntityId.of(spender).getId())
                        .payerAccountId(EntityId.of(spender))
                        .timestampRange(Range.atLeast(allowanceTimestamp)))
                .get();
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(expected);
    }

    private void createTokenEntity(
            TokenID tokenId,
            TokenType tokenType,
            String symbol,
            long consensusTimestamp,
            boolean freezeDefault,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            List<CustomFee> customFees,
            List<EntityId> autoAssociatedAccounts) {
        var transaction = tokenCreateTransaction(
                tokenType, freezeDefault, setFreezeKey, setKycKey, setPauseKey, symbol, customFees);
        insertAndParseTransaction(consensusTimestamp, transaction, builder -> {
            builder.getReceiptBuilder().setTokenID(tokenId).setNewTotalSupply(INITIAL_SUPPLY);
            builder.addAllAutomaticTokenAssociations(autoAssociatedAccounts.stream()
                    .map(account -> TokenAssociation.newBuilder()
                            .setTokenId(tokenId)
                            .setAccountId(convertAccountId(account))
                            .build())
                    .toList());
            if (tokenType == FUNGIBLE_COMMON) {
                builder.addTokenTransferLists(tokenTransferList(tokenId, accountAmount(PAYER, INITIAL_SUPPLY)));
            }
        });
    }

    private void createTokenEntity(
            TokenID tokenID,
            TokenType tokenType,
            String symbol,
            long consensusTimestamp,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey) {
        createTokenEntity(
                tokenID,
                tokenType,
                symbol,
                consensusTimestamp,
                false,
                setFreezeKey,
                setKycKey,
                setPauseKey,
                Collections.emptyList(),
                Collections.emptyList());
    }

    private void createAndAssociateToken(
            TokenID tokenId,
            TokenType tokenType,
            String symbol,
            long createTimestamp,
            long associateTimestamp,
            AccountID accountId,
            boolean setFreezeKey,
            boolean setKycKey,
            boolean setPauseKey,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus,
            TokenPauseStatusEnum expectedPauseStatus,
            long initialSupply) {
        createTokenEntity(tokenId, tokenType, symbol, createTimestamp, setFreezeKey, setKycKey, setPauseKey);
        assertThat(tokenRepository.findById(DOMAIN_TOKEN_ID.getId()))
                .get()
                .returns(CREATE_TIMESTAMP, Token::getCreatedTimestamp)
                .returns(expectedFreezeStatus, Token::getFreezeStatus)
                .returns(expectedKycStatus, Token::getKycStatus)
                .returns(expectedPauseStatus, Token::getPauseStatus)
                .returns(SYMBOL, Token::getSymbol)
                .returns(initialSupply, Token::getTotalSupply);

        Transaction associateTransaction = tokenAssociate(List.of(tokenId), accountId);
        insertAndParseTransaction(associateTimestamp, associateTransaction);

        assertTokenAccountInRepository(
                tokenId,
                accountId,
                false,
                0,
                associateTimestamp,
                associateTimestamp,
                true,
                null,
                null,
                associateTimestamp);
    }

    private void updateTokenFeeSchedule(TokenID tokenId, long consensusTimestamp, List<CustomFee> customFees) {
        Transaction transaction = buildTransaction(builder -> builder.getTokenFeeScheduleUpdateBuilder()
                .setTokenId(tokenId)
                .addAllCustomFees(convertCustomFees(customFees)));
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord transactionRecord = buildTransactionRecord(
                builder -> builder.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)),
                transactionBody,
                ResponseCodeEnum.SUCCESS.getNumber());

        parseRecordItemAndCommit(RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build());
    }

    private TokenTransferList tokenTransferList(TokenID tokenId, AccountAmount.Builder... accountAmounts) {
        var builder = TokenTransferList.newBuilder().setToken(tokenId);
        for (var aa : accountAmounts) {
            builder.addTransfers(aa);
        }
        return builder.build();
    }

    private TokenTransferList nftTransfer(
            TokenID tokenId, AccountID receiverAccountId, AccountID senderAccountId, List<Long> serialNumbers) {
        TokenTransferList.Builder builder = TokenTransferList.newBuilder();
        builder.setToken(tokenId);
        for (Long serialNumber : serialNumbers) {
            NftTransfer.Builder nftTransferBuilder = NftTransfer.newBuilder().setSerialNumber(serialNumber);
            if (receiverAccountId != null) {
                nftTransferBuilder.setReceiverAccountID(receiverAccountId);
            }
            if (senderAccountId != null) {
                nftTransferBuilder.setSenderAccountID(senderAccountId);
            }
            nftTransferBuilder.setIsApproval(false);
            builder.addNftTransfers(nftTransferBuilder);
        }
        return builder.build();
    }

    private List<com.hederahashgraph.api.proto.java.CustomFee> convertCustomFee(CustomFee customFee) {
        List<com.hederahashgraph.api.proto.java.CustomFee> protoCustomFees = new ArrayList<>();
        if (customFee.getFractionalFees() != null) {
            for (var fractionalFee : customFee.getFractionalFees()) {
                var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder();
                long maximumAmount = fractionalFee.getMaximumAmount();
                protoCustomFee.setFractionalFee(FractionalFee.newBuilder()
                        .setFractionalAmount(Fraction.newBuilder()
                                .setNumerator(fractionalFee.getNumerator())
                                .setDenominator(fractionalFee.getDenominator()))
                        .setMaximumAmount(maximumAmount)
                        .setMinimumAmount(fractionalFee.getMinimumAmount())
                        .setNetOfTransfers(fractionalFee.isNetOfTransfers()));
                protoCustomFee.setAllCollectorsAreExempt(fractionalFee.isAllCollectorsAreExempt());
                protoCustomFee.setFeeCollectorAccountId(convertAccountId(fractionalFee.getCollectorAccountId()));
                protoCustomFees.add(protoCustomFee.build());
            }
        }
        if (customFee.getRoyaltyFees() != null) {
            for (var royaltyFee : customFee.getRoyaltyFees()) {
                var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder();
                RoyaltyFee.Builder protoRoyaltyFee = RoyaltyFee.newBuilder()
                        .setExchangeValueFraction(Fraction.newBuilder()
                                .setNumerator(royaltyFee.getNumerator())
                                .setDenominator(royaltyFee.getDenominator()));
                if (royaltyFee.getFallbackFee() != null) {
                    var amount = royaltyFee.getFallbackFee().getAmount();
                    var denominatingTokenId = royaltyFee.getFallbackFee().getDenominatingTokenId();
                    protoRoyaltyFee.setFallbackFee(
                            convertFixedFee(amount, denominatingTokenId, customFee.getEntityId()));
                }

                protoCustomFee.setRoyaltyFee(protoRoyaltyFee);
                protoCustomFee.setAllCollectorsAreExempt(royaltyFee.isAllCollectorsAreExempt());
                protoCustomFee.setFeeCollectorAccountId(convertAccountId(royaltyFee.getCollectorAccountId()));
                protoCustomFees.add(protoCustomFee.build());
            }
        }
        if (customFee.getFixedFees() != null) {
            for (var fixedFee : customFee.getFixedFees()) {
                if (fixedFee.getCollectorAccountId() != null) {
                    var protoCustomFee = com.hederahashgraph.api.proto.java.CustomFee.newBuilder();
                    protoCustomFee.setAllCollectorsAreExempt(fixedFee.isAllCollectorsAreExempt());
                    protoCustomFee.setFeeCollectorAccountId(convertAccountId(fixedFee.getCollectorAccountId()));
                    protoCustomFee.setFixedFee(convertFixedFee(
                            fixedFee.getAmount(), fixedFee.getDenominatingTokenId(), customFee.getEntityId()));
                    protoCustomFees.add(protoCustomFee.build());
                }
            }
        }

        return protoCustomFees;
    }

    private FixedFee.Builder convertFixedFee(Long amount, EntityId denominatingTokenId, long tokenId) {
        FixedFee.Builder protoFixedFee = FixedFee.newBuilder().setAmount(amount);
        if (denominatingTokenId != null) {
            if (denominatingTokenId.equals(tokenId)) {
                protoFixedFee.setDenominatingTokenId(TokenID.getDefaultInstance());
            } else {
                protoFixedFee.setDenominatingTokenId(convertTokenId(denominatingTokenId));
            }
        }

        return protoFixedFee;
    }

    private List<com.hederahashgraph.api.proto.java.CustomFee> convertCustomFees(List<CustomFee> customFees) {
        List<com.hederahashgraph.api.proto.java.CustomFee> protoCustomFees = new ArrayList<>();
        for (CustomFee customFee : customFees) {
            protoCustomFees.addAll(convertCustomFee(customFee));
        }

        return protoCustomFees;
    }

    private byte[] toTrimmedBytes(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return trim(DomainUtils.toEvmAddress(entityId));
    }

    private byte[] toTrimmedBytes(long value) {
        return trim(Longs.toByteArray(value));
    }

    @SneakyThrows
    private byte[] trim(byte[] data) {
        return Hex.decodeHex(Hex.encodeHexString(data).replaceFirst("^(00)+", ""));
    }

    @Builder
    static class TokenCreateArguments {
        List<EntityId> autoEnabledAccounts;
        List<Long> balances;
        long createdTimestamp;
        List<CustomFee> customFees;
        String customFeesDescription;

        @Builder.Default
        TokenFreezeStatusEnum expectedFreezeStatus = TokenFreezeStatusEnum.NOT_APPLICABLE;

        @Builder.Default
        TokenKycStatusEnum expectedKycstatus = TokenKycStatusEnum.NOT_APPLICABLE;

        @Builder.Default
        TokenPauseStatusEnum expectedPauseStatus = TokenPauseStatusEnum.NOT_APPLICABLE;

        boolean freezeDefault;
        boolean freezeKey;

        @Builder.Default
        TokenFreezeStatusEnum freezeStatus = TokenFreezeStatusEnum.NOT_APPLICABLE;

        boolean kycKey;

        @Builder.Default
        TokenKycStatusEnum kycStatus = TokenKycStatusEnum.NOT_APPLICABLE;

        boolean pauseKey;
        EntityId tokenId;

        public Arguments toArguments() {
            String description = StringUtils.joinWith(
                    ", ",
                    customFeesDescription,
                    freezeDefault ? "freezeDefault false" : "freezeDefault true",
                    freezeKey ? "has freezeKey" : "no freezeKey",
                    kycKey ? "has kycKey" : "no kycKey");
            List<TokenAccount> tokenAccounts = autoEnabledAccounts.stream()
                    .map(account -> TokenAccount.builder()
                            .accountId(account.getId())
                            .associated(true)
                            .automaticAssociation(false)
                            .balanceTimestamp(createdTimestamp)
                            .createdTimestamp(createdTimestamp)
                            .freezeStatus(freezeStatus)
                            .kycStatus(kycStatus)
                            .tokenId(tokenId.getId())
                            .timestampRange(Range.atLeast(createdTimestamp))
                            .build())
                    .collect(Collectors.toList());

            for (int i = 0; i < autoEnabledAccounts.size(); i++) {
                tokenAccounts.get(i).setBalance(balances.get(i));
            }

            return Arguments.of(
                    description,
                    customFees,
                    freezeDefault,
                    freezeKey,
                    kycKey,
                    pauseKey,
                    expectedFreezeStatus,
                    expectedKycstatus,
                    expectedPauseStatus,
                    tokenAccounts);
        }
    }
}
