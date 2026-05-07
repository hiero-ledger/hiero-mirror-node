// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import java.util.Optional;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class CaffeineWritableKVStateTest {

    private Cache<AccountID, Optional<Account>> sharedStore;
    private CaffeineWritableKVState<AccountID, Account> subject;

    @Mock
    private ReadableKVState<AccountID, Account> delegate;

    @Mock
    private AccountID accountID;

    @Mock
    private Account account;

    @BeforeEach
    void setup() {
        sharedStore = Caffeine.newBuilder().maximumSize(100).build();
        subject = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, delegate, sharedStore);
    }

    @Test
    void readFallsThroughToDelegateWhenCacheMiss() {
        when(delegate.get(accountID)).thenReturn(account);
        assertThat(subject.readFromDataSource(accountID)).isEqualTo(account);
        verify(delegate).get(accountID);
    }

    @Test
    void readFromCaffeineBeforeDelegateOnCacheHit() {
        sharedStore.put(accountID, Optional.of(account));
        assertThat(subject.readFromDataSource(accountID)).isEqualTo(account);
        verify(delegate, never()).get(accountID);
    }

    @Test
    void tombstoneReturnsNullWithoutDelegateFallthrough() {
        sharedStore.put(accountID, Optional.empty());
        lenient().when(delegate.get(accountID)).thenReturn(account); // would return non-null if reached
        assertThat(subject.readFromDataSource(accountID)).isNull();
        verify(delegate, never()).get(accountID);
    }

    @Test
    void putIntoDataSourceStoresInCaffeine() {
        subject.putIntoDataSource(accountID, account);
        assertThat(sharedStore.getIfPresent(accountID)).isEqualTo(Optional.of(account));
    }

    @Test
    void removeFromDataSourceStoresTombstoneInCaffeine() {
        subject.removeFromDataSource(accountID);
        assertThat(sharedStore.getIfPresent(accountID)).isEqualTo(Optional.empty());
    }

    @Test
    void commitFlushesWriteCacheToSharedStore() {
        subject.put(accountID, account);

        subject.commit();

        assertThat(sharedStore.getIfPresent(accountID)).isEqualTo(Optional.of(account));
    }

    @Test
    void commitFlushesRemovalAsTombstone() {
        sharedStore.put(accountID, Optional.of(account)); // pre-existing
        subject.remove(accountID);

        subject.commit();

        assertThat(sharedStore.getIfPresent(accountID)).isEqualTo(Optional.empty());
    }

    @Test
    void resetClearsWriteCacheButNotSharedStore() {
        subject.put(accountID, account);
        subject.commit();

        ContractCallContext.get().reset();

        // shared store still holds the committed value
        assertThat(sharedStore.getIfPresent(accountID)).isEqualTo(Optional.of(account));
        // but the per-request write cache is cleared
        assertThat(ContractCallContext.get().getWriteCacheState(AccountReadableKVState.STATE_ID))
                .isEmpty();
    }

    @Test
    void committedValueVisibleAcrossSubsequentContexts() {
        subject.put(accountID, account);
        subject.commit();

        // new request context sees the committed value via readFromDataSource
        final Account readBack = ContractCallContext.run(ctx -> subject.readFromDataSource(accountID));
        assertThat(readBack).isEqualTo(account);
        verify(delegate, never()).get(accountID);
    }

    @Test
    void sizeOfDataSourceDelegatesToReadableDelegate() {
        assertThat(subject.sizeOfDataSource()).isEqualTo(delegate.size());
    }

    @Test
    void equalsSameInstance() {
        assertThat(subject).isEqualTo(subject);
    }

    @Test
    void equalsDifferentType() {
        assertThat(subject).isNotEqualTo("other");
    }

    @Test
    void equalsNullReturnsFalse() {
        assertThat(subject).isNotEqualTo(null);
    }

    @Test
    void equalsDifferentStateId() {
        final var other = new CaffeineWritableKVState<>(
                TokenService.NAME, AliasesReadableKVState.STATE_ID, delegate, sharedStore);
        assertThat(subject).isNotEqualTo(other);
    }

    @Test
    void equalsSameStateIdSameDelegate() {
        final var other = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, delegate, sharedStore);
        assertThat(subject).isEqualTo(other);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        final var other = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, delegate, sharedStore);
        assertThat(subject).hasSameHashCodeAs(other);
    }
}
