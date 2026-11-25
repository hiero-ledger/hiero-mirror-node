// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WrappedWritableKVState;
import java.util.concurrent.CompletableFuture;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MapWritableKVStateTest {

    private MapWritableKVState<AccountID, Account> mapWritableKVState;

    @Mock
    private ReadableKVState<AccountID, Account> readableKVState;

    @Mock
    private AccountID accountID;

    @Mock
    private Account account;

    @BeforeEach
    void setup() {
        mapWritableKVState =
                new MapWritableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVState);
    }

    @Test
    void testDataSourceSizeIsZero() {
        assertThat(mapWritableKVState.sizeOfDataSource()).isZero();
    }

    @Test
    void testReadFromDataSourceReturnsCorrectValue() {
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.readFromDataSource(accountID)).isEqualTo(account);
    }

    @Test
    void testPutIntoDataSource() {
        assertThat(mapWritableKVState.contains(accountID)).isFalse();
        mapWritableKVState.putIntoDataSource(accountID, account);
        assertThat(mapWritableKVState.contains(accountID)).isTrue();
    }

    @Test
    void testRemoveFromDataSource() {
        mapWritableKVState.putIntoDataSource(accountID, account);
        assertThat(mapWritableKVState.contains(accountID)).isTrue();
        mapWritableKVState.removeFromDataSource(accountID);
        assertThat(mapWritableKVState.contains(accountID)).isFalse();
    }

    @Test
    void testCommit() {
        mapWritableKVState.putIntoDataSource(accountID, account);
        assertThat(mapWritableKVState.contains(accountID)).isTrue();
        mapWritableKVState.commit(); // In-memory commits are disabled: writes remain pending.
    }

    @Test
    void inMemoryCommitKeepsWritesAndDeletionsInTheWriteCacheOnly() {
        final var context = ContractCallContext.get();
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
        final var updated = mock(Account.class);

        mapWritableKVState.put(accountID, updated);
        mapWritableKVState.commit();

        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).containsEntry(accountID, updated);
        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID)).containsEntry(accountID, account);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(updated);
        assertThat(mapWritableKVState.getOriginalValue(accountID)).isSameAs(account);

        mapWritableKVState.remove(accountID);
        mapWritableKVState.commit();
        assertThat(mapWritableKVState.get(accountID)).isNull();
        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).containsEntry(accountID, null);
        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID)).containsEntry(accountID, account);
    }

    @Test
    void resetDiscardsInMemoryCommitsAndReusesDatabaseReads() {
        final var context = ContractCallContext.get();
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);

        mapWritableKVState.put(accountID, mock(Account.class));
        mapWritableKVState.commit();
        final var layer = new WrappedWritableKVState<>(mapWritableKVState);
        layer.remove(accountID);
        layer.commit();
        mapWritableKVState.commit();
        assertThat(mapWritableKVState.get(accountID)).isNull();

        context.reset();

        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEmpty();
        assertThat(mapWritableKVState.modifiedKeys()).isEmpty();
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
        assertThat(new WrappedWritableKVState<>(mapWritableKVState).get(accountID))
                .isSameAs(account);
        verify(readableKVState, times(1)).get(accountID);
    }

    @Test
    void droppedWrapperDiscardsItsChangesAndPreservesEarlierCommit() {
        mapWritableKVState.put(accountID, account);
        mapWritableKVState.commit();

        final var dropped = new WrappedWritableKVState<>(mapWritableKVState);
        dropped.remove(accountID);
        assertThat(dropped.get(accountID)).isNull();
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);

        final var kept = new WrappedWritableKVState<>(mapWritableKVState);
        assertThat(kept.get(accountID)).isSameAs(account);
        kept.remove(accountID);
        kept.commit();
        assertThat(mapWritableKVState.get(accountID)).isNull();
    }

    @Test
    void commitWithoutInMemoryCommitsKeepsWritesPending() {
        final var context = ContractCallContext.get();
        final var updated = mock(Account.class);
        when(readableKVState.get(accountID)).thenReturn(account);

        mapWritableKVState.put(accountID, updated);
        mapWritableKVState.commit();

        assertThat(mapWritableKVState.get(accountID)).isSameAs(updated);
        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID)).doesNotContainKey(accountID);
        context.reset();
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
    }

    @Test
    void concurrentRequestsShareTheStateObjectButNotTheirCommittedValues() {
        final var other = mock(Account.class);
        final var first = CompletableFuture.supplyAsync(() -> ContractCallContext.run(ctx -> {
            mapWritableKVState.put(accountID, account);
            mapWritableKVState.commit();
            return mapWritableKVState.get(accountID);
        }));
        final var second = CompletableFuture.supplyAsync(() -> ContractCallContext.run(ctx -> {
            mapWritableKVState.put(accountID, other);
            mapWritableKVState.commit();
            return mapWritableKVState.get(accountID);
        }));

        assertThat(first.join()).isSameAs(account);
        assertThat(second.join()).isSameAs(other);
        assertThat(mapWritableKVState.get(accountID)).isNull();
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(mapWritableKVState).isEqualTo(mapWritableKVState);
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mapWritableKVState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mapWritableKVState).isNotEqualTo(null);
    }

    @Test
    void testEqualsDifferentKeys() {
        MapWritableKVState<AccountID, Account> other =
                new MapWritableKVState<>(TokenService.NAME, AliasesReadableKVState.STATE_ID, readableKVState);
        assertThat(mapWritableKVState).isNotEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        final var readableKVStateMock = mock(ReadableKVState.class);
        MapWritableKVState<AccountID, Account> other =
                new MapWritableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVStateMock);
        other.put(accountID, account);
        assertThat(mapWritableKVState).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapWritableKVState<AccountID, Account> other =
                new MapWritableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVState);
        assertThat(mapWritableKVState).hasSameHashCodeAs(other);
    }
}
