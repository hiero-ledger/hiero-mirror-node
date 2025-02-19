/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import com.swirlds.state.spi.ReadableKVState;
import java.util.Collections;
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
        mapWritableKVState = new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVState);
    }

    @Test
    void testGetForModifyReturnsCorrectValue() {
        when(readableKVState.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.getForModify(accountID)).isEqualTo(account);
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
                new MapWritableKVState<>(AliasesReadableKVState.KEY, readableKVState);
        assertThat(mapWritableKVState).isNotEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        final var readableKVStateMock = mock(ReadableKVState.class);
        MapWritableKVState<AccountID, Account> other =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateMock);
        other.put(accountID, account);
        assertThat(mapWritableKVState).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapWritableKVState<AccountID, Account> other =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVState);
        assertThat(mapWritableKVState).hasSameHashCodeAs(other);
    }
}
