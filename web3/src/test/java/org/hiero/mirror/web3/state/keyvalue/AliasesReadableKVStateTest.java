// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.AliasedAccountCacheManager;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AliasesReadableKVStateTest {

    @InjectMocks
    private AliasesReadableKVState aliasesReadableKVState;

    @Spy
    private ContractCallContext contractCallContext;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Spy
    private AliasedAccountCacheManager aliasedAccountCacheManager;

    @Mock
    private EntityIdSingleton entityIdSingleton;

    @Spy
    private EvmProperties evmProperties;

    private static final ProtoBytes EVM_ADDRESS_BYTES =
            new ProtoBytes(Bytes.wrap("67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69".getBytes()));

    private static final ProtoBytes ALIAS_BYTES = new ProtoBytes(
            Bytes.wrap("3a2102b3c641418e89452cd5202adfd4758f459acb8e364f741fd16cd2db79835d39d2".getBytes()));

    private static final EntityId ENTITY_ID = EntityId.of(1L, 2L, 3L);

    private static final Entity ENTITY_WITH_ALIAS = Entity.builder()
            .id(ENTITY_ID.getId())
            .shard(ENTITY_ID.getShard())
            .realm(ENTITY_ID.getRealm())
            .num(ENTITY_ID.getNum())
            .alias(ALIAS_BYTES.value().toByteArray())
            .build();

    private static final Entity ENTITY_WITH_EVM_ADDRESS = Entity.builder()
            .id(ENTITY_ID.getId())
            .shard(ENTITY_ID.getShard())
            .realm(ENTITY_ID.getRealm())
            .num(ENTITY_ID.getNum())
            .alias(EVM_ADDRESS_BYTES.value().toByteArray())
            .build();

    private static final AccountID ACCOUNT_ID =
            toAccountId(ENTITY_WITH_ALIAS.getShard(), ENTITY_WITH_ALIAS.getRealm(), ENTITY_WITH_ALIAS.getNum());

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void accountNotFoundReturnsNullWithAlias() {
        when(commonEntityAccessor.get(any(Bytes.class), any())).thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void accountNotFoundReturnsNullWithEvmAddress() {
        when(commonEntityAccessor.get(any(Bytes.class), any())).thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenTimestampIsNullReturnLatestAccountIDWithAlias() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY_WITH_ALIAS));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsNullReturnLatestAccountIDWithEvmAddress() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY_WITH_EVM_ADDRESS));
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsHistoricalReturnCorrectAccountIDWithAlias() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.of(ENTITY_WITH_ALIAS));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsHistoricalReturnCorrectAccountIDWithEvmAddress() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.of(ENTITY_WITH_EVM_ADDRESS));
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsLaterReturnNullWithAlias() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenTimestampIsLaterReturnNullWithEvmAddress() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenAliasIsReadPutAccountNumInCache() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY_WITH_ALIAS));
        assertThat(contractCallContext
                        .getReadCacheState(AccountReadableKVState.STATE_ID)
                        .containsKey(ACCOUNT_ID))
                .isFalse();
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
        assertThat(contractCallContext
                        .getReadCacheState(AccountReadableKVState.STATE_ID)
                        .containsKey(ACCOUNT_ID))
                .isTrue();
    }

    @Test
    void aliasResolutionCachesOverriddenBalanceAndNonce() {
        final var address = Bytes.fromHex("67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69");
        final var entity = Entity.builder()
                .id(ENTITY_ID.getId())
                .shard(ENTITY_ID.getShard())
                .realm(ENTITY_ID.getRealm())
                .num(ENTITY_ID.getNum())
                .type(EntityType.ACCOUNT)
                .evmAddress(address.toByteArray())
                .balance(1000L)
                .ethereumNonce(1L)
                .build();
        final var override = new StateOverride();
        override.setAddress(address.toHex());
        override.setBalance("0xc8");
        override.setNonce("0x2a");
        contractCallContext.setStateOverrides(Map.of(address, override));
        when(commonEntityAccessor.get(address, Optional.empty())).thenReturn(Optional.of(entity));

        assertThat(aliasesReadableKVState.get(new ProtoBytes(address))).isEqualTo(ACCOUNT_ID);

        final var cached = (Account) contractCallContext
                .getReadCacheState(AccountReadableKVState.STATE_ID)
                .get(ACCOUNT_ID);
        assertThat(cached.tinybarBalance()).isEqualTo(200L);
        assertThat(cached.ethereumNonce()).isEqualTo(42L);
        assertThat(entity.getBalance()).isEqualTo(1000L);
    }

    @Test
    void missingAddressesWithStateOverridesReceiveDistinctAccountIds() {
        final var address1 = Bytes.fromHex("a11ce00000000000000000000000000000000001");
        final var address2 = Bytes.fromHex("b0b0000000000000000000000000000000000002");
        final var override1 = new StateOverride();
        override1.setAddress(address1.toHex());
        final var override2 = new StateOverride();
        override2.setAddress(address2.toHex());
        contractCallContext.setStateOverrides(Map.of(address1, override1, address2, override2));
        when(commonEntityAccessor.get(any(Bytes.class), any())).thenReturn(Optional.empty());
        when(entityIdSingleton.get()).thenReturn(new EntityNumber(5000L));

        final var accountId1 = aliasesReadableKVState.get(new ProtoBytes(address1));
        final var accountId2 = aliasesReadableKVState.get(new ProtoBytes(address2));

        assertThat(accountId1).isNotNull();
        assertThat(accountId2).isNotNull();
        assertThat(accountId1).isNotEqualTo(accountId2);
        assertThat(accountId1.accountNum()).isEqualTo(4999L);
        assertThat(accountId2.accountNum()).isEqualTo(4998L);

        // Reading the same alias again resolves to the same account without allocating a new number
        assertThat(aliasesReadableKVState.get(new ProtoBytes(address1))).isEqualTo(accountId1);

        final var accountCache = contractCallContext.getReadCacheState(AccountReadableKVState.STATE_ID);
        final var account1 = (Account) accountCache.get(accountId1);
        final var account2 = (Account) accountCache.get(accountId2);
        assertThat(account1).isNotEqualTo(account2);
        assertThat(account1.alias()).isEqualTo(address1);
        assertThat(account2.alias()).isEqualTo(address2);
    }

    @Test
    void getExpectedSize() {
        assertThat(aliasesReadableKVState.size()).isZero();
    }
}
