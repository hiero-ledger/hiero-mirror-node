// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.hapi.fees.usage.crypto;

import static com.hedera.services.hapi.fees.usage.crypto.CryptoContextUtils.*;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.*;
import java.util.*;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoApproveAllowanceMetaTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final EntityId proxy = domainBuilder.entityId();
    private final AccountID proxyAccountId = proxy.toAccountID();
    private final EntityId tokenId = domainBuilder.entityId();

    private final CryptoAllowance cryptoAllowances = CryptoAllowance.newBuilder()
            .setSpender(proxyAccountId)
            .setAmount(10L)
            .build();
    private final TokenAllowance tokenAllowances = TokenAllowance.newBuilder()
            .setSpender(proxyAccountId)
            .setAmount(10L)
            .setTokenId(tokenId.toTokenID())
            .build();
    private final NftAllowance nftAllowances = NftAllowance.newBuilder()
            .setSpender(proxyAccountId)
            .setTokenId(tokenId.toTokenID())
            .addAllSerialNumbers(List.of(1L, 2L, 3L))
            .build();
    private Map<EntityNum, Long> cryptoAllowancesMap = new HashMap<>();
    private Map<AllowanceId, Long> tokenAllowancesMap = new HashMap<>();
    private Set<AllowanceId> nftAllowancesMap = new HashSet<>();

    @BeforeEach
    void setUp() {
        cryptoAllowancesMap = convertToCryptoMap(List.of(cryptoAllowances));
        tokenAllowancesMap = convertToTokenMap(List.of(tokenAllowances));
        nftAllowancesMap = convertToNftMap(List.of(nftAllowances));
    }

    @Test
    void allGettersAndToStringWork() {
        final var expected = String.format(
                "CryptoApproveAllowanceMeta{cryptoAllowances={EntityNum{value=%1$s}=10},"
                        + " tokenAllowances={AllowanceId{tokenId=%2$s, spenderId=%1$s}=10},"
                        + " nftAllowances=[AllowanceId{tokenId=%2$s, spenderId=%1$s}],"
                        + " effectiveNow=1234567, msgBytesUsed=112}",
                proxy, tokenId);
        final var now = 1_234_567;
        final var subject = CryptoApproveAllowanceMeta.newBuilder()
                .msgBytesUsed(112)
                .cryptoAllowances(cryptoAllowancesMap)
                .tokenAllowances(tokenAllowancesMap)
                .nftAllowances(nftAllowancesMap)
                .effectiveNow(now)
                .build();

        assertEquals(now, subject.getEffectiveNow());
        assertEquals(112, subject.getMsgBytesUsed());
        assertEquals(expected, subject.toString());
    }

    @Test
    void calculatesBaseSizeAsExpected() {
        final var op = CryptoApproveAllowanceTransactionBody.newBuilder()
                .addAllCryptoAllowances(List.of(cryptoAllowances))
                .addAllTokenAllowances(List.of(tokenAllowances))
                .addAllNftAllowances(List.of(nftAllowances))
                .build();
        final var canonicalTxn =
                TransactionBody.newBuilder().setCryptoApproveAllowance(op).build();

        final var subject = new CryptoApproveAllowanceMeta(
                op, canonicalTxn.getTransactionID().getTransactionValidStart().getSeconds());

        final var expectedMsgBytes = (op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE)
                + (op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE)
                + (op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE)
                + countSerials(op.getNftAllowancesList()) * LONG_SIZE;

        assertEquals(expectedMsgBytes, subject.getMsgBytesUsed());

        final var expectedCryptoMap = new HashMap<>();
        final var expectedTokenMap = new HashMap<>();
        final var expectedNfts = new HashSet<>();

        expectedCryptoMap.put(EntityNum.fromAccountId(proxyAccountId), 10L);
        expectedTokenMap.put(new AllowanceId(tokenId.toTokenID(), proxyAccountId), 10L);
        expectedNfts.add(new AllowanceId(tokenId.toTokenID(), proxyAccountId));
        assertEquals(expectedCryptoMap, subject.getCryptoAllowances());
        assertEquals(expectedTokenMap, subject.getTokenAllowances());
        assertEquals(expectedNfts, subject.getNftAllowances());
    }

    @Test
    void hashCodeAndEqualsWork() {
        final var now = 1_234_567;
        final var subject1 = CryptoApproveAllowanceMeta.newBuilder()
                .msgBytesUsed(112)
                .cryptoAllowances(cryptoAllowancesMap)
                .tokenAllowances(tokenAllowancesMap)
                .nftAllowances(nftAllowancesMap)
                .effectiveNow(now)
                .build();

        final var subject2 = CryptoApproveAllowanceMeta.newBuilder()
                .msgBytesUsed(112)
                .cryptoAllowances(cryptoAllowancesMap)
                .tokenAllowances(tokenAllowancesMap)
                .nftAllowances(nftAllowancesMap)
                .effectiveNow(now)
                .build();

        assertEquals(subject1, subject2);
        assertEquals(subject1.hashCode(), subject2.hashCode());
    }
}
