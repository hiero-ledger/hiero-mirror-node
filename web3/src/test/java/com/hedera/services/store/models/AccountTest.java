// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.base.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.protobuf.ByteString;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class AccountTest {
    private static final byte[] mockCreate2Addr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
    private final Address testAlias = Address.fromHexString("0x6aF23eBbA9CbEd6A6a8cA16a6f9C8FAf7E8d8c90");
    private final long miscAccountNum = 12345;
    private final Id subjectId = new Id(0, 0, miscAccountNum);
    private final int numAssociations = 3;
    private final int numPositiveBalances = 2;
    private final int numTreasuryTitles = 0;
    private final int alreadyUsedAutoAssociations = 123;
    private final long defaultLongValue = 0;
    private final long ownedNfts = 5;
    private Account subject;

    @BeforeEach
    void setUp() {
        subject = new Account(
                ByteString.EMPTY,
                0L,
                subjectId,
                defaultLongValue,
                () -> defaultLongValue,
                false,
                () -> ownedNfts,
                defaultLongValue,
                Id.DEFAULT,
                alreadyUsedAutoAssociations,
                null,
                null,
                null,
                () -> numAssociations,
                () -> numPositiveBalances,
                numTreasuryTitles,
                0L,
                false,
                null,
                0L,
                0);
    }

    @Test
    void objectContractWorks() {
        final var TEST_LONG_VALUE = 0;

        assertEquals(subjectId, subject.getId());
        assertEquals(TEST_LONG_VALUE, subject.getExpiry());
        assertFalse(subject.isDeleted());
        assertEquals(TEST_LONG_VALUE, subject.getBalance());
        assertEquals(TEST_LONG_VALUE, subject.getAutoRenewSecs());
        assertEquals(ownedNfts, subject.getOwnedNfts());
        assertEquals(Id.DEFAULT, subject.getProxy());
        assertEquals(subjectId.asEvmAddress(), subject.getAccountAddress());
        assertEquals(subject.getCryptoAllowances(), new TreeMap<>());
        assertEquals(subject.getFungibleTokenAllowances(), new TreeMap<>());
        assertEquals(subject.getApproveForAllNfts(), new TreeSet<>());
        assertEquals(numTreasuryTitles, subject.getNumTreasuryTitles());
    }

    @Test
    void autoAssociate() {
        var account = Account.getEmptyAccount()
                .setMaxAutoAssociations(10)
                .setNumAssociations(7)
                .setUsedAutoAssociations(5);
        assertThat(account.autoAssociate())
                .returns(10, Account::getMaxAutoAssociations)
                .returns(8, Account::getNumAssociations)
                .returns(6, Account::getUsedAutoAssociations);
    }

    @Test
    void canAutoAssociate() {
        assertThat(Account.getEmptyAccount().setMaxAutoAssociations(0).canAutoAssociate())
                .isFalse();
        assertThat(Account.getEmptyAccount()
                        .setMaxAutoAssociations(1)
                        .setUsedAutoAssociations(1)
                        .canAutoAssociate())
                .isFalse();
        assertThat(Account.getEmptyAccount().setMaxAutoAssociations(1).canAutoAssociate())
                .isTrue();
        assertThat(Account.getEmptyAccount()
                        .setMaxAutoAssociations(-1)
                        .setUsedAutoAssociations(65536)
                        .canAutoAssociate())
                .isTrue();
    }

    @Test
    void canonicalAddressIs20ByteAliasIfPresent() {
        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
        assertEquals(Address.wrap(Bytes.wrap(mockCreate2Addr)), subject.canonicalAddress());
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void canonicalAddressIsEVMAddressIfCorrectAlias() {
        // default truffle address #0
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(
                Address.wrap(Bytes.fromHexString("627306090abaB3A6e1400e9345bC60c78a8BEf57")),
                subject.canonicalAddress());
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void invalidCanonicalAddresses() {
        Address untranslatedAddress = Address.wrap(Bytes.fromHexString("0000000000000000000000000000000000003039"));

        // bogus alias
        subject.setAlias(ByteString.copyFromUtf8("This alias is invalid"));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect starting bytes for ECDSA
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("ffff03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect ECDSA key
        subject.setAlias(ByteString.copyFrom(
                Hex.decode("3a21ffaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());
    }

    @Test
    void isAutoAssociateEnabled() {
        assertThat(Account.getEmptyAccount().isAutoAssociateEnabled()).isFalse();
        assertThat(Account.getEmptyAccount().setMaxAutoAssociations(1).isAutoAssociateEnabled())
                .isTrue();
        assertThat(Account.getEmptyAccount().setMaxAutoAssociations(-1).isAutoAssociateEnabled())
                .isTrue();
    }

    @Test
    void getDummySenderWithAliasAsExpected() {
        assertThat(Account.getDummySenderAccountWithAlias(testAlias).getAlias())
                .isEqualTo(ByteString.copyFrom(testAlias.toArray()));
    }

    @Test
    void toStringAsExpected() {
        final var desired =
                "Account{entityId=0, id=0.0.12345, alias=, address=0x0000000000000000000000000000000000003039, "
                        + "expiry=0, balance=0, deleted=false, ownedNfts=5, autoRenewSecs=0, proxy=0.0.0, "
                        + "accountAddress=0x0000000000000000000000000000000000003039, maxAutoAssociations=123, "
                        + "cryptoAllowances={}, fungibleTokenAllowances={}, approveForAllNfts=[], numAssociations=3, "
                        + "numPositiveBalances=2, numTreasuryTitles=0, ethereumNonce=0, isSmartContract=false, "
                        + "key=null, createdTimestamp=0, usedAutoAssociations=0}";
        assertEquals(desired, subject.toString());
    }
}
