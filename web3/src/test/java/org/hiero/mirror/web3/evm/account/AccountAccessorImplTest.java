// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.account;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.services.store.models.Account;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
class AccountAccessorImplTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final String ALIAS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final ByteString ECDSA_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    protected static final Address ECDSA_KEY_ALIAS_ADDRESS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(ECDSA_KEY.substring(2).toByteArray())));
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ALIAS_ADDRESS = Address.fromHexString(ALIAS_HEX);
    public AccountAccessorImpl accountAccessor;

    @Mock
    private HederaEvmEntityAccess mirrorEntityAccess;

    @Mock
    private Store store;

    @Mock
    private Account account;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @BeforeEach
    void setUp() {
        accountAccessor = new AccountAccessorImpl(store, mirrorEntityAccess, mirrorEvmContractAliases);
    }

    @Test
    void isTokenAddressTrue() {
        when(mirrorEntityAccess.isTokenAccount(ADDRESS)).thenReturn(true);
        final var result = accountAccessor.isTokenAddress(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isTokenAddressFalse() {
        when(mirrorEntityAccess.isTokenAccount(ADDRESS)).thenReturn(false);
        final var result = accountAccessor.isTokenAddress(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void canonicalAddressForEvmAddress() {
        when(mirrorEvmContractAliases.isMirror(ADDRESS)).thenReturn(true);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.canonicalAddress()).thenReturn(ALIAS_ADDRESS);
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void canonicalAddressIsAlreadyResolved() {
        final var result = accountAccessor.canonicalAddress(ALIAS_ADDRESS);
        assertThat(result).isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void canonicalAddressForRecoveredEcdsaKey() {
        when(mirrorEvmContractAliases.isMirror(ADDRESS)).thenReturn(true);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.canonicalAddress()).thenReturn(ECDSA_KEY_ALIAS_ADDRESS);
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ECDSA_KEY_ALIAS_ADDRESS);
    }

    @Test
    void getAddress() {
        when(mirrorEntityAccess.isExtant(ADDRESS)).thenReturn(true);
        final var result = accountAccessor.canonicalAddress(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS);
    }
}
