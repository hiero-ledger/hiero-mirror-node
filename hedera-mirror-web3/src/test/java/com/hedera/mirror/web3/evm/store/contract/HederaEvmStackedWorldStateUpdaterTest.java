// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.txns.validation.OptionValidator;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class HederaEvmStackedWorldStateUpdaterTest {
    private static final Address ALIAS = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address ALIAS_2 = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbc");
    private static final Address SPONSOR = Address.fromHexString("0xcba");
    private static final long A_BALANCE = 1_000L;
    private static final long A_NONCE = 1L;
    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final UpdateTrackingAccount<Account> updatedHederaEvmAccount = new UpdateTrackingAccount<>(address, null);

    @Mock
    private AccountAccessor accountAccessor;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private HederaEvmEntityAccess entityAccess;

    @Mock
    private AbstractLedgerWorldUpdater<HederaEvmMutableWorldState, Account> updater;

    @Mock
    private EvmProperties properties;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    private EntityAddressSequencer entityAddressSequencer;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private OptionValidator validator;

    private Store store;

    private HederaEvmStackedWorldStateUpdater subject;

    @BeforeEach
    void setUp() {
        final var entityDatabaseAccessor = new EntityDatabaseAccessor(entityRepository, new CommonProperties());
        final List<DatabaseAccessor<Object, ?>> accessors = List.of(
                entityDatabaseAccessor,
                new AccountDatabaseAccessor(
                        new CommonProperties(), entityDatabaseAccessor, null, null, null, null, null, null));
        final var stackedStateFrames = new StackedStateFrames(accessors);
        store = new StoreImpl(stackedStateFrames, validator);
        subject = new HederaEvmStackedWorldStateUpdater(
                updater,
                accountAccessor,
                entityAccess,
                tokenAccessor,
                properties,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
    }

    @Test
    void commitsNewlyCreatedAccountToStackedStateFrames() {
        final var accountFromTopFrame = createTestAccount(address);
        assertThat(accountFromTopFrame.getAccountAddress()).isEqualTo(address);
    }

    @Test
    void commitsNewlyCreatedAccountAsExpected() {
        updater = new MockLedgerWorldUpdater(null, accountAccessor);
        subject = new HederaEvmStackedWorldStateUpdater(
                updater,
                accountAccessor,
                entityAccess,
                tokenAccessor,
                properties,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
        when(mirrorEvmContractAliases.resolveForEvm(address)).thenReturn(address);
        store.wrap();
        subject.createAccount(address, A_NONCE, Wei.of(A_BALANCE));
        assertNull(updater.getAccount(address));
        subject.commit();
        assertThat(subject.getAccount(address).getNonce()).isEqualTo(A_NONCE);
        assertThat(updater.getAccount(address).getNonce()).isEqualTo(A_NONCE);
    }

    @Test
    void commitsDeletedAccountsAsExpected() {
        updater = new MockLedgerWorldUpdater(null, accountAccessor);
        subject = new HederaEvmStackedWorldStateUpdater(
                updater,
                accountAccessor,
                entityAccess,
                tokenAccessor,
                properties,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
        when(mirrorEvmContractAliases.resolveForEvm(address)).thenReturn(address);
        store.wrap();
        subject.createAccount(address, A_NONCE, Wei.of(A_BALANCE));
        subject.deleteAccount(address);
        assertThat(updater.getDeletedAccountAddresses()).isEmpty();
        subject.commit();
        assertThat(subject.getDeletedAccountAddresses()).hasSize(1);
        var accountFromTopFrame = store.getAccount(address, OnMissing.DONT_THROW);
        assertEquals(com.hedera.services.store.models.Account.getEmptyAccount(), accountFromTopFrame);
    }

    @Test
    void accountTests() {
        updatedHederaEvmAccount.setBalance(Wei.of(100));
        when(mirrorEvmContractAliases.resolveForEvm(address)).thenReturn(address);
        store.wrap();
        assertThat(subject.createAccount(address, 1, Wei.ONE).getAddress()).isEqualTo(address);
        assertThat(subject.getAccount(address).getBalance()).isEqualTo(Wei.ONE);
        assertThat(subject.getTouchedAccounts()).isNotEmpty();
        assertThat(subject.getDeletedAccountAddresses()).isEmpty();
        var accountFromTopFrame = store.getAccount(address, OnMissing.DONT_THROW);
        assertNotEquals(com.hedera.services.store.models.Account.getEmptyAccount(), accountFromTopFrame);
        subject.commit();
        deleteTestAccount(address);
        accountFromTopFrame = store.getAccount(address, OnMissing.DONT_THROW);
        assertEquals(com.hedera.services.store.models.Account.getEmptyAccount(), accountFromTopFrame);
    }

    @Test
    void get() {
        when(updater.get(address)).thenReturn(updatedHederaEvmAccount);
        when(accountAccessor.canonicalAddress(address)).thenReturn(address);

        final var actual = subject.get(address);
        assertThat(actual.getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getForRedirect() {
        givenForRedirect();
        assertThat(subject.get(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getWithTrack() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        when(accountAccessor.canonicalAddress(address)).thenReturn(address);

        subject.getAccount(address);
        subject.get(address);
        assertThat(subject.get(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getWithNonCanonicalAddress() {
        when(accountAccessor.canonicalAddress(any())).thenReturn(Address.ZERO);
        assertThat(subject.get(address)).isNull();
    }

    @Test
    void getAccount() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getAccountWithTrack() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        subject.getAccount(address);
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getAccountWithMissingWorldReturnsNull() {
        assertThat(subject.getAccount(address)).isNull();
    }

    @Test
    void getAccountForRedirect() {
        givenForRedirect();
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void tracksLazyCreateAccountAsExpected() {
        subject.trackLazilyCreatedAccount(Address.ALTBN128_MUL);

        final var lazyAccount = subject.getUpdatedAccounts().get(Address.ALTBN128_MUL);
        assertNotNull(lazyAccount);
        assertEquals(Wei.ZERO, lazyAccount.getBalance());
        assertFalse(subject.getDeletedAccounts().contains(Address.ALTBN128_MUL));
    }

    @Test
    void updaterTest() {
        assertThat(subject.tokenAccessor()).isEqualTo(tokenAccessor);
        assertThat(subject.parentUpdater()).isEmpty();
        assertThat(subject.updater()).isEqualTo(subject);
    }

    @Test
    void namedelegatesTokenAccountTest() {
        final var someAddress = Address.BLS12_MAP_FP2_TO_G2;
        assertThat(subject.isTokenAddress(someAddress)).isFalse();
    }

    @Test
    void getSbhRefundReturnsZero() {
        assertThat(subject.getSbhRefund()).isZero();
    }

    @Test
    void usesAliasesForDecodingHelpForV38() {
        given(mirrorEvmContractAliases.resolveForEvm(ALIAS)).willReturn(SPONSOR);
        given(tokenAccessor.canonicalAddress(ALIAS)).willReturn(ALIAS);

        final var resolved = subject.unaliased(ALIAS.toArrayUnsafe());
        assertArrayEquals(SPONSOR.toArrayUnsafe(), resolved);
    }

    @Test
    void unaliasingFailsWhenNotUsingCanonicalAddressForV38() {
        given(tokenAccessor.canonicalAddress(ALIAS)).willReturn(ALIAS_2);

        assertArrayEquals(new byte[20], subject.unaliased(ALIAS.toArrayUnsafe()));
    }

    @Test
    void recognizesTreasuryAccount() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        var account = createTestAccount(treasuryAddress);
        account = account.setNumTreasuryTitles(4);
        store.updateAccount(account);
        assertThat(account.getAccountAddress()).isEqualTo(treasuryAddress);
        assertTrue(subject.contractIsTokenTreasury(treasuryAddress));
        account = account.setNumTreasuryTitles(0);
        store.updateAccount(account);
        assertFalse(subject.contractIsTokenTreasury(treasuryAddress));
        assertTrue(true);
        deleteTestAccount(treasuryAddress);
    }

    @Test
    void recognizesNonZeroTokenBalanceAccount() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        var account = createTestAccount(treasuryAddress);
        account = account.setNumPositiveBalances(2);
        store.updateAccount(account);
        assertThat(account.getAccountAddress()).isEqualTo(treasuryAddress);
        assertTrue(subject.contractHasAnyBalance(treasuryAddress));
        account = account.setNumPositiveBalances(0);
        store.updateAccount(account);
        assertFalse(subject.contractHasAnyBalance(treasuryAddress));
        assertTrue(true);
        deleteTestAccount(treasuryAddress);
    }

    @Test
    void recognizesAccountWhoStillOwnsNfts() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        var account = createTestAccount(treasuryAddress);
        account = account.setOwnedNfts(2);
        store.updateAccount(account);
        assertThat(account.getAccountAddress()).isEqualTo(treasuryAddress);
        assertTrue(subject.contractOwnsNfts(treasuryAddress));
        account = account.setOwnedNfts(0);
        store.updateAccount(account);
        assertFalse(subject.contractHasAnyBalance(treasuryAddress));
        assertTrue(true);
        deleteTestAccount(treasuryAddress);
    }

    private void deleteTestAccount(Address treasuryAddress) {
        subject.revert();
        subject.deleteAccount(treasuryAddress);
    }

    private com.hedera.services.store.models.Account createTestAccount(Address treasuryAddress) {
        when(mirrorEvmContractAliases.resolveForEvm(treasuryAddress)).thenReturn(treasuryAddress);
        store.wrap();
        subject.createAccount(treasuryAddress, A_NONCE, Wei.of(A_BALANCE));
        subject.commit();
        return store.getAccount(treasuryAddress, OnMissing.THROW);
    }

    private void givenForRedirect() {
        when(properties.isRedirectTokenCallsEnabled()).thenReturn(true);
        when(entityAccess.isTokenAccount(address)).thenReturn(true);
    }
}
