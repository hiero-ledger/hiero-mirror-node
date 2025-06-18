// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.ledger;

import static com.hedera.services.ledger.BalanceChange.NO_TOKEN_FOR_HBAR_ADJUST;
import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.services.utils.IdUtils.asAccountWithAlias;
import static com.hedera.services.utils.IdUtils.nftXfer;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.junit.jupiter.api.Test;

class BalanceChangeTest {
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final Id t = EntityIdUtils.idFromEntityId(domainBuilder.entityId());
    private final long delta = -1_234L;
    private final long serialNo = 1234L;
    private final AccountID a = domainBuilder.entityId().toAccountID();
    private final AccountID b = domainBuilder.entityId().toAccountID();
    private final AccountID payer = domainBuilder.entityId().toAccountID();

    @Test
    void objectContractSanityChecks() {
        // given:
        final var hbarChange = IdUtils.hbarChange(a, delta);
        final var tokenChange = IdUtils.tokenChange(t, a, delta);
        final var nftChange = changingNftOwnership(t, t.asGrpcToken(), nftXfer(a, b, serialNo), payer);

        // expect:
        assertFalse(nftChange.isApprovedAllowance());
        assertNotEquals(hbarChange, tokenChange);
        assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
        // and:
        assertSame(a, hbarChange.accountId());
        assertEquals(delta, hbarChange.getAggregatedUnits());
        assertEquals(t.asGrpcToken(), tokenChange.tokenId());
    }

    @Test
    void recognizesFungibleTypes() {
        // given:
        final var hbarChange = IdUtils.hbarChange(a, delta);
        final var tokenChange = IdUtils.tokenChange(t, a, delta);

        assertTrue(hbarChange.isForHbar());
        assertFalse(tokenChange.isForHbar());
        assertFalse(hbarChange.isForToken());
        // and:
        assertFalse(hbarChange.isForNft());
        assertFalse(tokenChange.isForNft());
        assertTrue(tokenChange.isForToken());
    }

    @Test
    void noTokenForHbarAdjust() {
        final var hbarChange = IdUtils.hbarChange(a, delta);
        assertSame(NO_TOKEN_FOR_HBAR_ADJUST, hbarChange.tokenId());
    }

    @Test
    void hbarAdjust() {
        final var hbarAdjust = BalanceChange.hbarCustomFeeAdjust(Id.DEFAULT, 10);
        assertEquals(Id.DEFAULT, hbarAdjust.getAccount());
        assertTrue(hbarAdjust.isForHbar());
        assertFalse(hbarAdjust.isForToken());
        assertEquals(0, hbarAdjust.getAllowanceUnits());
        assertEquals(10, hbarAdjust.getAggregatedUnits());
        assertEquals(10, hbarAdjust.originalUnits());
    }

    @Test
    void ownershipChangeFactoryWorks() {
        // setup:
        final var xfer = NftTransfer.newBuilder()
                .setSenderAccountID(a)
                .setReceiverAccountID(b)
                .setSerialNumber(serialNo)
                .setIsApproval(true)
                .build();

        // given:
        final var nftChange = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);

        // expect:
        assertEquals(a, nftChange.accountId());
        assertEquals(b, nftChange.counterPartyAccountId());
        assertEquals(t.asGrpcToken(), nftChange.tokenId());
        assertEquals(serialNo, nftChange.serialNo());
        // and:
        assertTrue(nftChange.isForNft());
        assertTrue(nftChange.isForToken());
        assertFalse(nftChange.hasNonEmptyCounterPartyAlias());
        assertTrue(nftChange.isApprovedAllowance());
        assertEquals(new NftId(t.shard(), t.realm(), t.num(), serialNo), nftChange.nftId());
    }

    @Test
    void canReplaceAlias() {
        final var created = domainBuilder.entityId().toAccountID();
        final var anAlias = ByteString.copyFromUtf8("abcdefg");
        final var subject = BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(1234)
                        .setAccountID(AccountID.newBuilder().setAlias(anAlias))
                        .build(),
                payer);

        subject.replaceNonEmptyAliasWith(EntityNum.fromAccountId(created));
        assertFalse(subject.hasAlias());
        assertEquals(created, subject.accountId());
        assertFalse(subject.hasNonEmptyCounterPartyAlias());
    }

    @Test
    void canReplaceCounterpartyAlias() {
        final var created = domainBuilder.entityId().toAccountID();
        final var anAlias = ByteString.copyFromUtf8("abcdefg");
        final var xfer = NftTransfer.newBuilder()
                .setSenderAccountID(domainBuilder.entityId().toAccountID())
                .setReceiverAccountID(asAccountWithAlias(String.valueOf(anAlias)))
                .setSerialNumber(serialNo)
                .setIsApproval(true)
                .build();
        final var subject = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);
        assertTrue(subject.hasNonEmptyCounterPartyAlias());
        assertFalse(subject.counterPartyAlias().isEmpty());

        subject.replaceNonEmptyAliasWith(EntityNum.fromAccountId(created));
        assertFalse(subject.hasNonEmptyCounterPartyAlias());
        assertEquals(created, subject.counterPartyAccountId());
        assertNotEquals(0, subject.counterPartyAccountId().getAccountNum());
        assertTrue(subject.isForNft());
    }

    @Test
    void replacedAlias() {
        final var created = domainBuilder.entityId().toAccountID();
        final var anAlias = ByteString.copyFromUtf8("abcdefg");
        final var subject = BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(1234)
                        .setAccountID(AccountID.newBuilder().setAlias(anAlias))
                        .build(),
                payer);

        subject.replaceNonEmptyAliasWith(EntityNum.fromAccountId(created));
        assertFalse(subject.hasAlias());
        assertEquals(created, subject.accountId());
        assertFalse(subject.hasNonEmptyCounterPartyAlias());
    }

    @Test
    void canSwitchHbarDebitToApprovedDebit() {
        final var unapproved = BalanceChange.changingHbar(aaWith(1234, -5678L, false), payer);
        final var approved = BalanceChange.changingHbar(aaWith(1234, -5678L, true), payer);

        assertNotEquals(unapproved, approved);

        unapproved.switchToApproved();

        assertEquals(approved, unapproved);
    }

    @Test
    void canSwitchFungibleDebitToApprovedDebit() {
        final var unapproved = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aaWith(1234, -5678L, false), payer);
        final var approved = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aaWith(1234, -5678L, true), payer);

        assertNotEquals(unapproved, approved);

        unapproved.switchToApproved();

        assertEquals(approved, unapproved);
    }

    @Test
    void canSwitchOwnershipChangeApproved() {
        final var unapproved =
                BalanceChange.changingNftOwnership(t, t.asGrpcToken(), ownershipChange(1234, 5678, 9, false), payer);
        final var approved =
                BalanceChange.changingNftOwnership(t, t.asGrpcToken(), ownershipChange(1234, 5678, 9, true), payer);

        assertNotEquals(unapproved, approved);

        unapproved.switchToApproved();

        assertEquals(approved, unapproved);
    }

    @Test
    void settersAndGettersOfDecimalsWorks() {
        final var created = new Id(1, 2, 3);
        final var token = new Id(4, 5, 6);
        final var subject = BalanceChange.changingFtUnits(
                token,
                token.asGrpcToken(),
                AccountAmount.newBuilder()
                        .setAmount(1234)
                        .setAccountID(created.asGrpcAccount())
                        .build(),
                payer);
        assertEquals(-1, subject.getExpectedDecimals());
        assertFalse(subject.hasExpectedDecimals());

        subject.setExpectedDecimals(2);

        assertEquals(2, subject.getExpectedDecimals());
        assertTrue(subject.hasExpectedDecimals());
    }

    @Test
    void switchingFungibleCreditsAndAlreadyApprovedAreNoops() {
        final var credit = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aaWith(1234, +5678L, false), payer);
        final var approved = BalanceChange.changingFtUnits(t, t.asGrpcToken(), aaWith(1234, -5678L, true), payer);

        assertDoesNotThrow(credit::switchToApproved);
        assertDoesNotThrow(approved::switchToApproved);
    }

    @Test
    void switchingHbarCreditsAndAlreadyApprovedAreNoops() {
        final var credit = BalanceChange.changingHbar(aaWith(1234, +5678L, false), payer);
        final var approved = BalanceChange.changingHbar(aaWith(1234, -5678L, true), payer);

        assertDoesNotThrow(credit::switchToApproved);
        assertDoesNotThrow(approved::switchToApproved);
    }

    private static AccountAmount aaWith(final long num, final long amount, final boolean approval) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(num).build())
                .setAmount(amount)
                .setIsApproval(approval)
                .build();
    }

    private static NftTransfer ownershipChange(
            final long from, final long to, final long serialNo, final boolean approval) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(AccountID.newBuilder().setAccountNum(from).build())
                .setReceiverAccountID(AccountID.newBuilder().setAccountNum(to).build())
                .setSerialNumber(serialNo)
                .setIsApproval(approval)
                .build();
    }
}
