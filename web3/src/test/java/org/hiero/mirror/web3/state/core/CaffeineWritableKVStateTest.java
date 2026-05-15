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
    private ReadableKVState<AccountID, Account> readableKVState;

    @Mock
    private AccountID accountID;

    @Mock
    private Account account;

    @BeforeEach
    void setup() {
        sharedStore = Caffeine.newBuilder().maximumSize(100).build();
        subject = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVState, sharedStore);
    }

    @Test
    void readFallsThroughToDelegateWhenCacheMiss() {
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(subject.readFromDataSource(accountID)).isEqualTo(account);
        verify(readableKVState).get(accountID);
    }

    @Test
    void readFromCaffeineBeforeDelegateOnCacheHit() {
        sharedStore.put(accountID, Optional.of(account));
        assertThat(subject.readFromDataSource(accountID)).isEqualTo(account);
        verify(readableKVState, never()).get(accountID);
    }

    @Test
    void tombstoneReturnsNullWithoutDelegateFallthrough() {
        sharedStore.put(accountID, Optional.empty());
        lenient().when(readableKVState.get(accountID)).thenReturn(account); // would return non-null if reached
        assertThat(subject.readFromDataSource(accountID)).isNull();
        verify(readableKVState, never()).get(accountID);
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
        verify(readableKVState, never()).get(accountID);
    }

    @Test
    void sizeOfDataSourceDelegatesToReadableDelegate() {
        assertThat(subject.sizeOfDataSource()).isEqualTo(readableKVState.size());
    }

    @Test
    void sizeCountsModificationAgainstDelegateNotCaffeine() {
        // key committed to Caffeine but absent from the readableKVState (backing store)
        sharedStore.put(accountID, Optional.of(account));
        when(readableKVState.get(accountID)).thenReturn(null);
        when(readableKVState.size()).thenReturn(0L);

        // same key modified again in the per-request write cache
        subject.put(accountID, account);

        // size() checks the readableKVState, not Caffeine: 0 (readableKVState) + 1 addition = 1
        assertThat(subject.size()).isEqualTo(1L);
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
                TokenService.NAME, AliasesReadableKVState.STATE_ID, readableKVState, sharedStore);
        assertThat(subject).isNotEqualTo(other);
    }

    @Test
    void equalsSameStateIdSameDelegate() {
        final var other = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVState, sharedStore);
        assertThat(subject).isEqualTo(other);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        final var other = new CaffeineWritableKVState<>(
                TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVState, sharedStore);
        assertThat(subject).hasSameHashCodeAs(other);
    }
}
