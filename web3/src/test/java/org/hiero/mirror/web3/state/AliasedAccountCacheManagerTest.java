// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;
import java.util.stream.Stream;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.CaffeineWritableKVState;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(ContextExtension.class)
class AliasedAccountCacheManagerTest {

    private static final AccountID ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1252L).build();
    private static final Bytes ALIAS = Bytes.fromHex("67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69");
    private static final Account ACCOUNT = Account.newBuilder()
            .accountId(ACCOUNT_ID)
            .alias(ALIAS)
            .tinybarBalance(1000L)
            .ethereumNonce(1L)
            .build();

    private final AliasedAccountCacheManager subject = new AliasedAccountCacheManager();

    @Test
    void cachesOverriddenBalanceAndNonce() {
        final var context = ContractCallContext.get();
        context.setStateOverrides(Map.of(ALIAS, override(ALIAS)));

        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);

        final var cached = (Account)
                context.getReadCacheState(AccountReadableKVState.STATE_ID).get(ACCOUNT_ID);
        assertThat(cached.tinybarBalance()).isEqualTo(200L);
        assertThat(cached.ethereumNonce()).isEqualTo(42L);
        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).containsEntry(ACCOUNT_ID, cached);
        assertThat(ACCOUNT.tinybarBalance()).isEqualTo(1000L);
        assertThat(ACCOUNT.ethereumNonce()).isEqualTo(1L);
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("nonMatchingOverrides")
    void cachesOriginalAccountWithoutMatchingOverrides(Map<Bytes, StateOverride> overrides) {
        final var context = ContractCallContext.get();
        context.setStateOverrides(overrides);

        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);

        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID)).containsEntry(ACCOUNT_ID, ACCOUNT);
        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEmpty();
    }

    @Test
    void repeatedAliasLookupPreservesExistingReadCacheEntry() {
        final var context = ContractCallContext.get();
        context.setStateOverrides(Map.of(ALIAS, override(ALIAS)));
        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);
        final var cached =
                context.getReadCacheState(AccountReadableKVState.STATE_ID).get(ACCOUNT_ID);
        context.getWriteCacheState(AccountReadableKVState.STATE_ID).clear();

        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);

        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID).get(ACCOUNT_ID))
                .isSameAs(cached);
        // A cache hit must not apply the override again or seed the write cache a second time.
        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aliasLookupPreservesSimulatedBalanceAndNonceChanges(boolean alreadyCached) {
        final var context = ContractCallContext.get();
        context.setStateOverrides(Map.of(ALIAS, override(ALIAS)));
        if (alreadyCached) {
            subject.putAccountNum(ACCOUNT_ID, ACCOUNT);
        }
        final var writable = writableAccounts();
        final var updated =
                ACCOUNT.copyBuilder().tinybarBalance(190L).ethereumNonce(43L).build();
        writable.put(ACCOUNT_ID, updated);

        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);

        assertThat(writable.get(ACCOUNT_ID)).isSameAs(updated);
        final var initial = (Account)
                context.getReadCacheState(AccountReadableKVState.STATE_ID).get(ACCOUNT_ID);
        assertThat(initial.tinybarBalance()).isEqualTo(200L);
        assertThat(initial.ethereumNonce()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aliasLookupPreservesSimulatedDeletion(boolean alreadyCached) {
        final var context = ContractCallContext.get();
        context.setStateOverrides(Map.of(ALIAS, override(ALIAS)));
        if (alreadyCached) {
            subject.putAccountNum(ACCOUNT_ID, ACCOUNT);
        }
        final var writable = writableAccounts();
        writable.remove(ACCOUNT_ID);

        subject.putAccountNum(ACCOUNT_ID, ACCOUNT);

        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).containsEntry(ACCOUNT_ID, null);
        assertThat(writable.get(ACCOUNT_ID)).isNull();
    }

    @Test
    void cachesAliasToAccountIdWithOverrides() {
        final var context = ContractCallContext.get();
        context.setStateOverrides(Map.of(ALIAS, override(ALIAS)));

        subject.putAccountAlias(ALIAS, ACCOUNT_ID);

        assertThat(context.getReadCacheState(AliasesReadableKVState.STATE_ID))
                .containsEntry(new ProtoBytes(ALIAS), ACCOUNT_ID);
    }

    private static Stream<Map<Bytes, StateOverride>> nonMatchingOverrides() {
        final var otherAddress = Bytes.fromHex("000000000000000000000000000000000000dead");
        return Stream.of(Map.of(), Map.of(otherAddress, override(otherAddress)));
    }

    private static StateOverride override(Bytes address) {
        final var override = new StateOverride();
        override.setAddress(address.toHex());
        override.setBalance(200L);
        override.setNonce(42L);
        return override;
    }

    private CaffeineWritableKVState<AccountID, Account> writableAccounts() {
        final var readable = new MapReadableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, Map.of(ACCOUNT_ID, ACCOUNT));
        return new CaffeineWritableKVState<>(
                TokenService.NAME,
                AccountReadableKVState.STATE_ID,
                readable,
                Caffeine.newBuilder().build());
    }
}
