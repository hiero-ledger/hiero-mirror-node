// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.state.spi.ReadableKVState;
import java.util.Collections;
import org.hiero.mirror.web3.ContextExtension;
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
    void testIterateFromDataSourceReturnsEmptyIterator() {
        when(readableKVState.keys()).thenReturn(Collections.emptyIterator());
        assertThat(mapWritableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
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
        mapWritableKVState.commit(); // Does nothing, just for test coverage.
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
