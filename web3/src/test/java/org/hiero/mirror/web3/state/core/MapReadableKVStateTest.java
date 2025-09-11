// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import java.util.Map;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        accountMap = Map.of(accountID, account);
        mapReadableKVState = new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.KEY, accountMap);
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
                TokenService.NAME,
                AccountReadableKVState.KEY,
                Map.of(
                        accountID1,
                        Account.newBuilder().accountId(accountID1).build(),
                        accountID2,
                        Account.newBuilder().accountId(accountID2).build()));
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
                new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.KEY, accountMap);
        assertThat(mapReadableKVState).isEqualTo(other);
    }

    @Test
    void testEqualsDifferentKeys() {
        MapReadableKVState<AccountID, Account> other =
                new MapReadableKVState<>(TokenService.NAME, AliasesReadableKVState.KEY, accountMap);
        assertThat(mapReadableKVState).isNotEqualTo(other);
    }

    @Test
    void testEqualsDifferentValues() {
        final var accountMapOther = Map.of(AccountID.newBuilder().accountNum(3L).build(), account);
        MapReadableKVState<AccountID, Account> other =
                new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.KEY, accountMapOther);
        assertThat(mapReadableKVState).isNotEqualTo(other);
    }

    @Test
    void testHashCode() {
        MapReadableKVState<AccountID, Account> other =
                new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.KEY, accountMap);
        assertThat(mapReadableKVState).hasSameHashCodeAs(other);
    }
}
