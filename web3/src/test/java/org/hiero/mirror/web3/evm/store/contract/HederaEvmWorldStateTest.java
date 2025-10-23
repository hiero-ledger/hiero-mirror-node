// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.StackedStateFrames;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.CustomFeeDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.TokenRelationshipDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.UniqueTokenDatabaseAccessor;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenBalanceRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class HederaEvmWorldStateTest {
    final long balance = 1_234L;
    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");

    @Mock
    AccountAccessor accountAccessor;

    @Mock
    TokenAccessor tokenAccessor;

    @Mock
    MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock
    EntityAddressSequencer entityAddressSequencer;

    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private AbstractCodeCache abstractCodeCache;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private OptionValidator validator;

    private StoreImpl store;

    private HederaEvmWorldState subject;

    @BeforeEach
    void setUp() {
        var commProperties = new CommonProperties();
        var systemEntity = new SystemEntity(commProperties);
        final var accountDatabaseAccessor =
                new AccountDatabaseAccessor(entityDatabaseAccessor, null, null, null, null, null, null, systemEntity);
        final var tokenDatabaseAccessor = new TokenDatabaseAccessor(
                tokenRepository,
                entityDatabaseAccessor,
                entityRepository,
                customFeeDatabaseAccessor,
                nftRepository,
                systemEntity);
        final var tokenRelationshipDatabaseAccessor = new TokenRelationshipDatabaseAccessor(
                tokenDatabaseAccessor,
                accountDatabaseAccessor,
                tokenAccountRepository,
                tokenBalanceRepository,
                nftRepository,
                systemEntity);
        final var uniqueTokenDatabaseAccessor = new UniqueTokenDatabaseAccessor(nftRepository);
        final List<DatabaseAccessor<Object, ?>> accessors = List.of(
                accountDatabaseAccessor,
                tokenDatabaseAccessor,
                tokenRelationshipDatabaseAccessor,
                uniqueTokenDatabaseAccessor);
        final var stackedStateFrames = new StackedStateFrames(accessors);
        store = new StoreImpl(stackedStateFrames, validator);
        subject = new HederaEvmWorldState(
                hederaEvmEntityAccess,
                evmProperties,
                abstractCodeCache,
                accountAccessor,
                tokenAccessor,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);
    }

    @Test
    void rootHash() {
        assertThat(subject.rootHash()).isEqualTo(Hash.EMPTY);
    }

    @Test
    void frontierRootHash() {
        assertThat(subject.frontierRootHash()).isEqualTo(Hash.EMPTY);
    }

    @Test
    void streamAccounts() {
        assertThrows(UnsupportedOperationException.class, () -> subject.streamAccounts(null, 10));
    }

    @Test
    void returnsNullForNull() {
        assertThat(subject.get(null)).isNull();
    }

    @Test
    void returnsNull() {
        assertThat(subject.get(address)).isNull();
    }

    // There are uncompatible changes between hedera-evm and latest besu version
    @Test
    @Disabled
    void returnsWorldStateAccount() {
        final var ripemd160Address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.getBalance(ripemd160Address)).thenReturn(balance);
        when(hederaEvmEntityAccess.isUsable(any())).thenReturn(true);

        final var account = subject.get(ripemd160Address);

        assertThat(account.getCode().isEmpty()).isTrue();
        assertThat(account.hasCode()).isFalse();
    }

    // There are uncompatible changes between hedera-evm and latest besu version
    @Test
    @Disabled
    void returnsHederaEvmWorldStateTokenAccount() {
        final var ripemd160Address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.isTokenAccount(ripemd160Address)).thenReturn(true);
        when(evmProperties.isRedirectTokenCallsEnabled()).thenReturn(true);

        final var account = subject.get(ripemd160Address);

        assertThat(account.getCode().isEmpty()).isFalse();
        assertThat(account.hasCode()).isTrue();
    }

    @Test
    void returnsNull2() {
        final var ripemd160Address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.isTokenAccount(ripemd160Address)).thenReturn(true);
        when(evmProperties.isRedirectTokenCallsEnabled()).thenReturn(false);

        assertThat(subject.get(ripemd160Address)).isNull();
    }

    @Test
    void commitsNewlyCreatedAccountToStackedStateFrames() {
        final var actualSubject = subject.updater();

        final var accountModel = new com.hedera.services.store.models.Account(
                ByteString.EMPTY,
                0L,
                Id.fromGrpcAccount(accountIdFromEvmAddress(address.toArrayUnsafe())),
                0L,
                () -> 123L,
                false,
                () -> 0L,
                0L,
                null,
                0,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                () -> 0,
                () -> 0,
                0,
                0L,
                false,
                null,
                0L,
                0);
        store.wrap();
        store.updateAccount(accountModel);
        actualSubject.commit();
        final var accountFromTopFrame = store.getAccount(address, OnMissing.DONT_THROW);
        assertThat(accountFromTopFrame).isEqualTo(accountModel);
    }

    @Test
    void updater() {
        final var nonSystemAddress = Address.fromHexString("0x0000000000000000000000000000000000000436");
        var actualSubject = subject.updater();
        assertThat(actualSubject.getSbhRefund()).isZero();
        assertThat(actualSubject.updater().get(nonSystemAddress)).isNull();
    }

    @Test
    void newContractAddressReturnsSequencerValueAsTypedAddress() {
        var actualSubject = subject.updater();
        final Address sponsor = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        final ContractID contractID = ContractID.newBuilder().build();

        when(entityAddressSequencer.getNewContractId(sponsor)).thenReturn(contractID);

        final var actual = actualSubject.newContractAddress(sponsor);
        assertThat(actual).isEqualTo(asTypedEvmAddress(contractID));
    }
}
