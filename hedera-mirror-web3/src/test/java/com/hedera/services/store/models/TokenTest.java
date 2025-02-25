// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.services.utils.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenTest {
    private final JKey someKey = asFcKeyUnchecked(
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    private final int numAssociations = 2;
    private final int numPositiveBalances = 1;
    private final long initialSupply = 1_000L;
    private final long initialTreasuryBalance = 500L;
    private final long expiry = 1000L;
    private final Id tokenId = new Id(1, 2, 3);
    private final Id treasuryId = new Id(0, 0, 0);
    private final Id nonTreasuryId = new Id(3, 2, 3);
    private final long defaultLongValue = 0;
    private final int defaultIntValue = 0;
    private Account treasuryAccount = new Account(
            ByteString.EMPTY,
            0L,
            treasuryId,
            defaultLongValue,
            () -> defaultLongValue,
            false,
            () -> defaultLongValue,
            defaultLongValue,
            Id.DEFAULT,
            defaultIntValue,
            null,
            null,
            null,
            () -> numAssociations,
            () -> numPositiveBalances,
            defaultIntValue,
            0L,
            false,
            null,
            0L,
            0);
    private Account nonTreasuryAccount = new Account(
            ByteString.EMPTY,
            0L,
            nonTreasuryId,
            defaultLongValue,
            () -> defaultLongValue,
            false,
            () -> defaultLongValue,
            defaultLongValue,
            Id.DEFAULT,
            defaultIntValue,
            null,
            null,
            null,
            () -> numAssociations,
            () -> numPositiveBalances,
            defaultIntValue,
            0L,
            false,
            null,
            0L,
            0);

    private Token subject;
    private TokenRelationship treasuryRel;
    private TokenRelationship nonTreasuryRel;

    @BeforeEach
    void setUp() {
        subject = new Token(
                0L,
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                () -> initialSupply,
                21_000_000,
                null,
                null,
                null,
                null,
                someKey,
                null,
                null,
                false,
                () -> treasuryAccount,
                null,
                false,
                false,
                false,
                expiry,
                0L,
                false,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections::emptyList);

        treasuryRel = new TokenRelationship(
                subject, treasuryAccount, () -> initialTreasuryBalance, false, false, false, false, true, 0);
        nonTreasuryRel =
                new TokenRelationship(subject, nonTreasuryAccount, () -> 0L, false, false, false, false, true, 0);
    }

    @Test
    void deleteAsExpected() {
        assertDoesNotThrow(() -> subject.delete());
    }

    @Test
    void constructsOkToken() {
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final var feeScheduleKey =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var pauseKey =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var op = TransactionBody.newBuilder()
                .setTokenCreation(TokenCreateTransactionBody.newBuilder()
                        .setTokenType(com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON)
                        .setInitialSupply(25)
                        .setMaxSupply(21_000_000)
                        .setSupplyType(com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE)
                        .setDecimals(10)
                        .setFreezeDefault(false)
                        .setMemo("the mother")
                        .setName("bitcoin")
                        .setSymbol("BTC")
                        .setFeeScheduleKey(feeScheduleKey)
                        .setPauseKey(pauseKey)
                        .addAllCustomFees(List.of(CustomFee.newBuilder()
                                .setFixedFee(FixedFee.newBuilder().setAmount(10).build())
                                .setFeeCollectorAccountId(asAccount("1.2.3"))
                                .build()))
                        .setAutoRenewAccount(nonTreasuryAccount.getId().asGrpcAccount())
                        .setExpiry(Timestamp.newBuilder().setSeconds(1000L).build())
                        .build())
                .build();

        subject = Token.fromGrpcOpAndMeta(tokenId, op.getTokenCreation(), treasuryAccount, nonTreasuryAccount, 123);

        assertEquals("bitcoin", subject.getName());
        assertEquals(123L, subject.getExpiry());
        assertEquals(TokenSupplyType.FINITE, subject.getSupplyType());
        assertNotNull(subject.getFeeScheduleKey());
        assertNotNull(subject.getPauseKey());
        assertFalse(subject.isPaused());
    }

    @Test
    void okCreationRelationship() {
        final var frzKey = someKey;
        final var kycKey = someKey;
        subject = subject.setFreezeKey(frzKey);
        subject = subject.setKycKey(kycKey);
        final var rel = subject.newEnabledRelationship(treasuryAccount);
        assertNotNull(rel);
        assertFalse(rel.isFrozen());
        assertTrue(rel.isKycGranted());
    }

    @Test
    void constructsTreasuryRelationShipAsExpected() {
        subject = subject.setKycKey(someKey);
        var newRel = subject.newRelationshipWith(treasuryAccount, true);
        assertEquals(newRel.getAccount(), treasuryRel.getAccount());
    }

    @Test
    void constructsExpectedDefaultRelWithNoKeys() {
        // setup:
        nonTreasuryRel =
                new TokenRelationship(subject, nonTreasuryAccount, () -> 0L, false, true, false, false, false, 0);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    // Divergence from services test, since we have immutable models and should set up them at the beginning
    @Test
    void constructsExpectedDefaultRelWithFreezeKeyAndFrozenByDefault() {
        // setup:
        subject = new Token(
                0L,
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.FINITE,
                () -> initialSupply,
                20000L,
                null,
                someKey,
                someKey,
                null,
                null,
                null,
                null,
                true,
                () -> treasuryAccount,
                null,
                false,
                false,
                false,
                expiry,
                0L,
                true,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections::emptyList);
        nonTreasuryRel =
                new TokenRelationship(subject, nonTreasuryAccount, () -> 0L, true, true, false, false, false, 0);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    // Divergence from services test, since we have immutable models and should set up them at the beginning
    @Test
    void constructsExpectedDefaultRelWithFreezeKeyAndNotFrozenByDefault() {
        // setup:
        subject = subject.setFreezeKey(someKey);
        nonTreasuryRel =
                new TokenRelationship(subject, nonTreasuryAccount, () -> 0L, false, true, false, false, false, 0);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    @Test
    void constructsExpectedDefaultRelWithKycKeyOnly() {
        // given:
        subject = subject.setKycKey(someKey);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel.getAccount(), nonTreasuryRel.getAccount());
    }

    @Test
    void failsInvalidIfLogicImplTriesToChangeNonTreasurySupply() {
        assertFailsWith(() -> subject.burn(nonTreasuryRel, 1L), FAIL_INVALID);
        assertFailsWith(() -> subject.mint(nonTreasuryRel, 1L, false), FAIL_INVALID);
    }

    @Test
    void cantBurnOrMintNegativeAmounts() {
        assertFailsWith(() -> subject.burn(treasuryRel, -1L), INVALID_TOKEN_BURN_AMOUNT);
        assertFailsWith(() -> subject.mint(treasuryRel, -1L, false), INVALID_TOKEN_MINT_AMOUNT);
    }

    @Test
    void burnsUniqueAsExpected() {
        treasuryRel = new TokenRelationship(subject, treasuryAccount, () -> 2L, false, false, false, false, true, 0);
        subject = subject.setSupplyKey(someKey);
        // treasuryRel = treasuryRel.initBalance(2);
        subject.getLoadedUniqueTokens()
                .putAll(Map.of(
                        10L,
                        new UniqueToken(subject.getId(), 10L, null, treasuryId, null, new byte[] {}),
                        11L,
                        new UniqueToken(subject.getId(), 11L, null, treasuryId, null, new byte[] {})));
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final long serialNumber0 = 10L;
        final long serialNumber1 = 11L;

        var tokenModificationResult = subject.burn(treasuryRel, List.of(serialNumber0, serialNumber1));
        var newToken = tokenModificationResult.token();
        treasuryRel = tokenModificationResult.tokenRelationship();
        treasuryAccount = treasuryRel.getAccount();

        assertEquals(initialSupply - 2, newToken.getTotalSupply());
        assertEquals(initialSupply, subject.getTotalSupply());
        assertEquals(-2, treasuryRel.getBalanceChange());
        assertFalse(subject.hasRemovedUniqueTokens());
        assertTrue(newToken.hasRemovedUniqueTokens());
        final var removedUniqueTokens = newToken.removedUniqueTokens();
        assertEquals(2, removedUniqueTokens.size());
        assertEquals(serialNumber0, removedUniqueTokens.get(0).getSerialNumber());
        assertEquals(serialNumber1, removedUniqueTokens.get(1).getSerialNumber());
        assertEquals(numPositiveBalances - 1, treasuryAccount.getNumPositiveBalances());
    }

    @Test
    void mintsUniqueAsExpected() {
        treasuryRel = new TokenRelationship(subject, treasuryAccount, () -> 0L, false, false, false, false, true, 0);
        subject = subject.setSupplyKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        var tokenModificationResult = subject.mint(
                treasuryRel, List.of(ByteString.copyFromUtf8("memo")), RichInstant.fromJava(Instant.now()));
        var newToken = tokenModificationResult.token();
        treasuryRel = tokenModificationResult.tokenRelationship();
        treasuryAccount = treasuryRel.getAccount();

        assertEquals(initialSupply + 1, newToken.getTotalSupply());
        assertEquals(initialSupply, subject.getTotalSupply());
        assertEquals(1, treasuryRel.getBalanceChange());
        assertTrue(newToken.hasMintedUniqueTokens());
        assertFalse(subject.hasMintedUniqueTokens());
        assertEquals(1, newToken.mintedUniqueTokens().get(0).getSerialNumber());
        assertEquals(1, newToken.getLastUsedSerialNumber());
        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, subject.getType());
        assertEquals(numPositiveBalances + 1, treasuryAccount.getNumPositiveBalances());
    }

    @Test
    void mintsAsExpected() {
        final long mintAmount = 100L;
        subject = subject.setSupplyKey(someKey);

        // when:
        var tokenModificationResult = subject.mint(treasuryRel, mintAmount, false);
        subject = tokenModificationResult.token();
        treasuryRel = tokenModificationResult.tokenRelationship();

        // then:
        assertEquals(initialSupply + mintAmount, subject.getTotalSupply());
        assertEquals(+mintAmount, treasuryRel.getBalanceChange());
        assertEquals(initialTreasuryBalance + mintAmount, treasuryRel.getBalance());
    }

    @Test
    void wipesCommonAsExpected() {
        subject = subject.setSupplyKey(someKey);
        subject = subject.setWipeKey(someKey);
        nonTreasuryRel = nonTreasuryRel.setBalance(100);

        var tokenModificationResult = subject.wipe(nonTreasuryRel, 10);
        subject = tokenModificationResult.token();
        nonTreasuryRel = tokenModificationResult.tokenRelationship();
        nonTreasuryAccount = nonTreasuryRel.getAccount();

        assertEquals(initialSupply - 10, subject.getTotalSupply());
        assertEquals(90, nonTreasuryRel.getBalance());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());

        nonTreasuryRel = nonTreasuryRel.setBalance(30);

        tokenModificationResult = subject.wipe(nonTreasuryRel, 30);
        subject = tokenModificationResult.token();
        nonTreasuryRel = tokenModificationResult.tokenRelationship();
        nonTreasuryAccount = nonTreasuryRel.getAccount();

        assertEquals(initialSupply - 40, subject.getTotalSupply());
        assertEquals(0, nonTreasuryRel.getBalance());
        assertEquals(numPositiveBalances - 1, nonTreasuryAccount.getNumPositiveBalances());
    }

    @Test
    void failsWipingCommonAsExpected() {
        // common setup
        subject = subject.setSupplyKey(someKey);
        // no wipe key
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 10));

        // set wipe key
        subject = subject.setWipeKey(someKey);
        // negative amount
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, -10));

        // wipe treasury
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(treasuryRel, 10));

        // negative total supply
        subject = new Token(
                0L,
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                () -> 10L,
                20000L,
                null,
                null,
                someKey,
                someKey,
                null,
                null,
                null,
                false,
                () -> treasuryAccount,
                null,
                false,
                false,
                false,
                expiry,
                0L,
                true,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections::emptyList);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 11));

        // negate account balance
        nonTreasuryRel = nonTreasuryRel.setBalance(0);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 5));

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void wipesUniqueAsExpected() {
        subject = subject.setSupplyKey(someKey);
        subject = subject.setWipeKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final var loadedUniqueTokensMap = new HashMap<Long, UniqueToken>();
        final var uniqueToken = mock(UniqueToken.class);
        final var owner = nonTreasuryAccount.getId();
        given(uniqueToken.getOwner()).willReturn(owner);
        loadedUniqueTokensMap.put(1L, uniqueToken);
        subject.getLoadedUniqueTokens().putAll(loadedUniqueTokensMap);

        nonTreasuryRel = nonTreasuryRel.setBalance(100);

        var tokenModificationResult = subject.wipe(nonTreasuryRel, List.of(1L));
        var newToken = tokenModificationResult.token();
        nonTreasuryRel = tokenModificationResult.tokenRelationship();

        assertEquals(initialSupply - 1, newToken.getTotalSupply());
        assertEquals(initialSupply, subject.getTotalSupply());
        assertEquals(99, nonTreasuryRel.getBalanceChange());
        assertEquals(99, nonTreasuryRel.getBalance());
        assertTrue(newToken.hasRemovedUniqueTokens());
        assertFalse(subject.hasRemovedUniqueTokens());
        assertEquals(1, newToken.removedUniqueTokens().get(0).getSerialNumber());
        assertTrue(newToken.hasChangedSupply());
        assertEquals(21_000_000, newToken.getMaxSupply());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());
    }

    @Test
    void uniqueWipeFailsAsExpected() {
        subject = subject.setSupplyKey(someKey);

        final Map<Long, UniqueToken> loadedUniqueTokensMap = new HashMap<>();
        subject.getLoadedUniqueTokens().putAll(loadedUniqueTokensMap);

        final var singleSerialNumber = List.of(1L);

        /* Invalid to wipe serial numbers for a FUNGIBLE_COMMON token */
        assertFailsWith(() -> subject.wipe(nonTreasuryRel, singleSerialNumber), FAIL_INVALID);

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        /* Must have a wipe key */
        assertFailsWith(() -> subject.wipe(treasuryRel, singleSerialNumber), TOKEN_HAS_NO_WIPE_KEY);

        subject = subject.setWipeKey(someKey);
        /* Not allowed to wipe treasury */
        assertFailsWith(() -> subject.wipe(treasuryRel, singleSerialNumber), CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT);
    }

    @Test
    void uniqueBurnFailsAsExpected() {
        subject = subject.setSupplyKey(someKey);
        final List<Long> emptySerialNumber = List.of();
        final var singleSerialNumber = List.of(1L);

        assertThrows(InvalidTransactionException.class, () -> {
            subject.burn(treasuryRel, singleSerialNumber);
        });

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertFailsWith(() -> subject.burn(treasuryRel, emptySerialNumber), INVALID_TOKEN_BURN_METADATA);
    }

    @Test
    void canOnlyBurnTokensOwnedByTreasury() {
        // setup:
        var oneToBurn = new UniqueToken(subject.getId(), 1L, null, nonTreasuryId, null, new byte[] {});

        // given:
        subject = subject.setSupplyKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.getLoadedUniqueTokens().put(1L, oneToBurn);

        // expect:
        assertFailsWith(() -> subject.burn(treasuryRel, List.of(1L)), TREASURY_MUST_OWN_BURNED_NFT);

        // and when:

        oneToBurn = new UniqueToken(subject.getId(), 1L, null, treasuryId, null, new byte[] {});
        subject.getLoadedUniqueTokens().put(1L, oneToBurn);
        assertDoesNotThrow(() -> subject.burn(treasuryRel, List.of(1L)));
    }

    @Test
    void cannotMintPastSerialNoLimit() {
        // setup:
        subject = subject.setSupplyKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        final var twoMeta = List.of(ByteString.copyFromUtf8("A"), ByteString.copyFromUtf8("Z"));
        subject = subject.setLastUsedSerialNumber(MAX_NUM_ALLOWED - 1);

        assertFailsWith(
                () -> subject.mint(treasuryRel, twoMeta, RichInstant.MISSING_INSTANT), SERIAL_NUMBER_LIMIT_REACHED);
    }

    @Test
    @SuppressWarnings("java:S5778")
    void uniqueMintFailsAsExpected() {
        subject = new Token(
                0L,
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                () -> 100000L,
                20000L,
                null,
                null,
                someKey,
                null,
                null,
                null,
                null,
                false,
                () -> treasuryAccount,
                null,
                false,
                false,
                false,
                expiry,
                0L,
                true,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections::emptyList);
        final var metadata = List.of(ByteString.copyFromUtf8("memo"));
        final List<ByteString> emptyMetadata = List.of();

        assertThrows(InvalidTransactionException.class, () -> {
            subject.mint(treasuryRel, metadata, RichInstant.fromJava(Instant.now()));
        });

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(InvalidTransactionException.class, () -> {
            subject.mint(treasuryRel, emptyMetadata, RichInstant.fromJava(Instant.now()));
        });
    }

    @Test
    void reflectionObjectHelpersWork() {
        final var otherToken = new Token(
                0L,
                new Id(1, 2, 3),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.FINITE,
                () -> 100000L,
                20000L,
                null,
                null,
                someKey,
                null,
                null,
                null,
                null,
                false,
                () -> treasuryAccount,
                null,
                false,
                false,
                false,
                expiry,
                0L,
                true,
                "the mother",
                "bitcoin",
                "BTC",
                10,
                defaultLongValue,
                defaultLongValue,
                Collections::emptyList);

        assertNotEquals(subject, otherToken);
        assertNotEquals(subject.hashCode(), otherToken.hashCode());
    }

    @Test
    void toStringWorks() {
        final var desired =
                "Token{id=1.2.3, type=FUNGIBLE_COMMON, deleted=false, autoRemoved=false, treasury=Account{entityId=0, "
                        + "id=0.0.0, alias=, address=0x0000000000000000000000000000000000000000, expiry=0, balance=0, "
                        + "deleted=false, ownedNfts=0, autoRenewSecs=0, proxy=0.0.0, "
                        + "accountAddress=0x0000000000000000000000000000000000000000, maxAutoAssociations=0, "
                        + "cryptoAllowances={}, fungibleTokenAllowances={}, approveForAllNfts=[], numAssociations=2, "
                        + "numPositiveBalances=1, numTreasuryTitles=0, ethereumNonce=0, isSmartContract=false, "
                        + "key=null, createdTimestamp=0, usedAutoAssociations=0}, "
                        + "autoRenewAccount=Account{entityId=0, id=0.0.0, alias=, "
                        + "address=0x0000000000000000000000000000000000000000, expiry=0, balance=0, deleted=false, "
                        + "ownedNfts=0, autoRenewSecs=0, proxy=null, "
                        + "accountAddress=0x0000000000000000000000000000000000000000, maxAutoAssociations=0, "
                        + "cryptoAllowances={}, fungibleTokenAllowances={}, approveForAllNfts=[], numAssociations=0, "
                        + "numPositiveBalances=0, numTreasuryTitles=0, ethereumNonce=0, isSmartContract=false, "
                        + "key=null, createdTimestamp=0, usedAutoAssociations=0}, kycKey=null, freezeKey=null, "
                        + "frozenByDefault=false, supplyKey=null, currentSerialNumber=0, pauseKey=null, paused=false}";
        assertEquals(desired, subject.toString());
    }

    @Test
    void switchingPauseStatusFailsIfNoPauseKey() {
        assertFalse(subject.hasPauseKey());
        assertFailsWith(() -> subject.changePauseStatus(true), TOKEN_HAS_NO_PAUSE_KEY);
    }

    @Test
    void switchingPauseStatusWorksIfTokenHasPauseKey() {
        Token modifiedToken = subject.setPauseKey(someKey);
        assertTrue(modifiedToken.hasPauseKey());

        modifiedToken = modifiedToken.changePauseStatus(false);
        assertFalse(modifiedToken.isPaused());

        modifiedToken = modifiedToken.changePauseStatus(true);
        assertTrue(modifiedToken.isPaused());
    }
}
