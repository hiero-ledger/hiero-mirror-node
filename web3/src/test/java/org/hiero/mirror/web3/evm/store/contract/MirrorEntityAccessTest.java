// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static com.google.protobuf.ByteString.EMPTY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.domain.entity.AbstractEntity.DEFAULT_EXPIRY_TIMESTAMP;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hyperledger.besu.datatypes.Address.ZERO;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEntityAccessTest {
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final EntityId ENTITY =
            EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 1252);
    private static final Address ADDRESS = toAddress(ENTITY);
    private static final String HEX = ADDRESS.toHexString();
    private static final Bytes BYTES = Bytes.fromHexString(HEX);
    private static final byte[] DATA = BYTES.toArrayUnsafe();
    private static final Long ENTITY_ID = ENTITY.getId();
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final Address NON_MIRROR_ADDRESS =
            Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");

    @Mock
    private ContractStateService contractStateService;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private Account account;

    @Mock
    private Token token;

    @Mock
    private Store store;

    private MirrorEntityAccess mirrorEntityAccess;

    @BeforeEach
    void setUp() {
        mirrorEntityAccess = new MirrorEntityAccess(contractRepository, contractStateService, store);
    }

    @Test
    void isUsableWithPositiveBalance() {
        final long balance = 23L;
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isNotUsableWithWrongAlias() {
        final var address = Address.fromHexString("0x0");
        when(store.getAccount(address, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isUsable(address);
        assertThat(result).isFalse();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isNotUsableWithExpiredTimestamp() {
        Account nonEmptyAccount = new Account(1234L, new Id(0, 0, 1234), 200L);
        nonEmptyAccount.setExpiry(-1);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(nonEmptyAccount);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isNotUsableWithExpiredTimestampAndNullBalance() {
        Account nullBalanceAccount = new Account(1234L, new Id(0, 0, 1234), 0L);
        nullBalanceAccount.setExpiry(-1);
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(nullBalanceAccount);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isUsableWithNotExpiredTimestamp() {
        final long expiredTimestamp = Instant.MAX.getEpochSecond();
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(expiredTimestamp);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isNotUsableWithExpiredAutoRenewTimestamp() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isFalse();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isUsableWithNotExpiredAutoRenewTimestamp() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(Instant.MAX.getEpochSecond());
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Disabled("Expiry not enabled on network; these tests need to account for feature flags; see #6941")
    @Test
    void isUsableWithEmptyExpiry() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getExpiry()).thenReturn(DEFAULT_EXPIRY_TIMESTAMP);
        final var result = mirrorEntityAccess.isUsable(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void getBalance() {
        final long balance = 23L;
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getBalance()).thenReturn(balance);
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isEqualTo(balance);
    }

    @Test
    void getBalanceForAccountWithEmptyOne() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        final var result = mirrorEntityAccess.getBalance(ADDRESS);
        assertThat(result).isZero();
    }

    @Test
    void getNonce() {
        final long nonce = 2;
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getEthereumNonce()).thenReturn(nonce);
        final var result = mirrorEntityAccess.getNonce(ADDRESS);
        assertThat(result).isEqualTo(nonce);
    }

    @Test
    void getNonceForEmptyAccount() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        final var result = mirrorEntityAccess.getNonce(ADDRESS);
        assertThat(result).isZero();
    }

    @Test
    void isExtant() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        final var result = mirrorEntityAccess.isExtant(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isExtantForZeroAddress() {
        when(store.getAccount(ZERO, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.isExtant(ZERO);
        assertThat(result).isFalse();
    }

    @Test
    void isTokenAccount() {
        when(store.getToken(ADDRESS, OnMissing.DONT_THROW)).thenReturn(token);
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isTrue();
    }

    @Test
    void isATokenAccountForMissingEntity() {
        when(store.getToken(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        final var result = mirrorEntityAccess.isTokenAccount(ADDRESS);
        assertThat(result).isFalse();
    }

    @Test
    void getAlias() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getAlias()).thenReturn(ByteString.copyFrom(DATA));
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isNotEqualTo(EMPTY);
    }

    @Test
    void getAliasForAccountWithEmptyOne() {
        when(store.getAccount(ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        final var result = mirrorEntityAccess.alias(ADDRESS);
        assertThat(result).isEqualTo(EMPTY);
    }

    @Test
    void getStorage() {
        when(store.getHistoricalTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY, BYTES.toArrayUnsafe())).thenReturn(Optional.of(DATA));
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ADDRESS, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(HEX));
    }

    @Test
    void getStorageHistorical() {
        byte[] trimmedKey = ADDRESS.trimLeadingZeros().toArrayUnsafe();
        when(store.getHistoricalTimestamp()).thenReturn(timestamp);
        when(contractStateService.findStorageByBlockTimestamp(EntityId.of(ENTITY_ID), trimmedKey, timestamp.get()))
                .thenReturn(Optional.of(DATA));
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ADDRESS, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(HEX));
    }

    @Test
    void getStorageFailsForNonMirrorAddress() {
        when(store.getAccount(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(Account.getEmptyAccount());
        when(store.getToken(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        final var key = Bytes.fromHexString(NON_MIRROR_ADDRESS.toHexString());
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(NON_MIRROR_ADDRESS, key));
        assertThat(result).isEqualTo(UInt256.fromHexString(ZERO.toHexString()));
    }

    @Test
    void getStorageFailsForZeroAddress() {
        final var result = UInt256.fromBytes(mirrorEntityAccess.getStorage(ZERO, BYTES));
        assertThat(result).isEqualTo(UInt256.fromHexString(ZERO.toHexString()));
    }

    @Test
    void fetchCodeIfPresent() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentForNonMirrorEvm() {
        when(store.getAccount(NON_MIRROR_ADDRESS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getEntityId()).thenReturn(ENTITY_ID);
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.of(DATA));
        final var result = mirrorEntityAccess.fetchCodeIfPresent(NON_MIRROR_ADDRESS);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    void fetchCodeIfPresentReturnsNull() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID)).thenReturn(Optional.empty());
        final var result = mirrorEntityAccess.fetchCodeIfPresent(ADDRESS);
        assertThat(result).isNull();
    }
}
