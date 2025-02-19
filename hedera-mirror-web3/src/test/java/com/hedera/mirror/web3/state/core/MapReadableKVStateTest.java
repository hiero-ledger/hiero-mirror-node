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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MapReadableKVStateTest {

    private MapReadableKVState<AccountID, Account> mapReadableKVState;

    private Map<AccountID, Account> accountMap;

    @Mock
    private AccountID accountID;

    @Mock
    private Account account;

    @BeforeEach
    void setup() {
        accountMap = new ConcurrentHashMap<>(Map.of(accountID, account));
        mapReadableKVState = new MapReadableKVState<>(AccountReadableKVState.KEY, accountMap);
    }

    @Test
    void testReadFromDataSource() {
        assertThat(mapReadableKVState.readFromDataSource(accountID)).isEqualTo(account);
    }

    @Test
    void testReadFromDataSourceNotExisting() {
        assertThat(mapReadableKVState.readFromDataSource(
                        AccountID.newBuilder().accountNum(1L).build()))
                .isNull();
    }

    @Test
    void testIterateFromDataSource() {
        assertThat(mapReadableKVState.iterateFromDataSource().hasNext()).isTrue();
        assertThat(mapReadableKVState.iterateFromDataSource().next()).isEqualTo(accountID);
    }

    @Test
    void testSize() {
        assertThat(mapReadableKVState.size()).isEqualTo(1L);
        final var accountID1 = AccountID.newBuilder().accountNum(1L).build();
        final var accountID2 = AccountID.newBuilder().accountNum(2L).build();
        final var mapReadableKVStateBigger = new MapReadableKVState<>(
                AccountReadableKVState.KEY,
                new ConcurrentHashMap<>(Map.of(
                        accountID1,
                        Account.newBuilder().accountId(accountID1).build(),
                        accountID2,
                        Account.newBuilder().accountId(accountID2).build())));
        assertThat(mapReadableKVStateBigger.size()).isEqualTo(2L);
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(mapReadableKVState).isEqualTo(mapReadableKVState);
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mapReadableKVState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mapReadableKVState).isNotEqualTo(null);
    }

    @Test
    void testEqualsSameValues() {
        MapReadableKVState<AccountID, Account> other =
                new MapReadableKVState<>(AccountReadableKVState.KEY, new ConcurrentHashMap<>(accountMap));
        assertThat(mapReadableKVState).isEqualTo(other);
    }

    @Test
    void testEqualsDifferentKeys() {
        MapReadableKVState<AccountID, Account> other = new MapReadableKVState<>(AliasesReadableKVState.KEY, accountMap);
        assertThat(mapReadableKVState).isNotEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        final var accountMapOther = new ConcurrentHashMap<>(
                Map.of(AccountID.newBuilder().accountNum(3L).build(), account));
        MapReadableKVState<AccountID, Account> other =
                new MapReadableKVState<>(AccountReadableKVState.KEY, accountMapOther);
        assertThat(mapReadableKVState).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapReadableKVState<AccountID, Account> other = new MapReadableKVState<>(AccountReadableKVState.KEY, accountMap);
        assertThat(mapReadableKVState).hasSameHashCodeAs(other);
    }
}
