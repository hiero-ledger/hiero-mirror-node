// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import java.util.concurrent.CompletableFuture;
import org.hiero.mirror.web3.ApiEndpointName;
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
        mapWritableKVState.commit(); // No actions context: writes remain pending.
    }

    @Test
    void actionsCommitRetainsWritesAndDeletionsAcrossPendingWriteResets() {
        final var context = ContractCallContext.get();
        context.setApi(ApiEndpointName.ACTIONS);
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
        final var updated = mock(Account.class);

        mapWritableKVState.put(accountID, updated);
        mapWritableKVState.commit();
        context.reset();

        assertThat(context.getReadCacheState(AccountReadableKVState.STATE_ID)).containsEntry(accountID, updated);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(updated);
        assertThat(mapWritableKVState.getOriginalValue(accountID)).isSameAs(updated);
        assertThat(context.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEmpty();

        mapWritableKVState.remove(accountID);
        mapWritableKVState.commit();
        context.reset();
        assertThat(mapWritableKVState.get(accountID)).isNull();
        assertThat(mapWritableKVState.getOriginalValue(accountID)).isNull();
        assertThat(context.getCommittedCacheState(AccountReadableKVState.STATE_ID))
                .containsEntry(accountID, null);

        final Account freshRead = ContractCallContext.run(ctx -> mapWritableKVState.get(accountID));
        assertThat(freshRead).isSameAs(account);
    }

    @Test
    void restoreSnapshotDiscardsRevertedTransactionAndPreservesEarlierCommit() {
        final var context = ContractCallContext.get();
        context.setApi(ApiEndpointName.ACTIONS);
        mapWritableKVState.put(accountID, account);
        mapWritableKVState.commit();
        final var snapshot = context.captureCaches();

        mapWritableKVState.remove(accountID);
        mapWritableKVState.commit();
        assertThat(mapWritableKVState.get(accountID)).isNull();

        context.restoreCaches(snapshot);
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
        assertThat(mapWritableKVState.getOriginalValue(accountID)).isSameAs(account);
    }

    @Test
    void ordinaryCallCommitDoesNotPublishWritesToTheTraceOverlay() {
        final var context = ContractCallContext.get();
        context.setApi(ApiEndpointName.CALL);
        final var updated = mock(Account.class);
        when(readableKVState.get(accountID)).thenReturn(account);

        mapWritableKVState.put(accountID, updated);
        mapWritableKVState.commit();

        assertThat(mapWritableKVState.get(accountID)).isSameAs(updated);
        assertThat(context.getCommittedCacheState(AccountReadableKVState.STATE_ID))
                .isEmpty();
        context.reset();
        assertThat(mapWritableKVState.get(accountID)).isSameAs(account);
    }

    @Test
    void simultaneousTracesShareTheStateObjectButNotItsSimulationValues() {
        final var other = mock(Account.class);
        final var first = CompletableFuture.supplyAsync(() -> ContractCallContext.run(ctx -> {
            ctx.setApi(ApiEndpointName.ACTIONS);
            mapWritableKVState.put(accountID, account);
            mapWritableKVState.commit();
            return mapWritableKVState.get(accountID);
        }));
        final var second = CompletableFuture.supplyAsync(() -> ContractCallContext.run(ctx -> {
            ctx.setApi(ApiEndpointName.ACTIONS);
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
