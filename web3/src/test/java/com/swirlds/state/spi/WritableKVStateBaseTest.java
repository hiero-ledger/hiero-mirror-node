// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import java.util.Map;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.MapWritableKVState;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class WritableKVStateBaseTest {

    @Mock
    private ReadableKVStateBase<AccountID, Account> readableKVStateBase;

    @Test
    void testResetCache() {
        final var ctx = ContractCallContext.get();
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final Map<Object, Object> map = Map.of(accountID, account);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVStateBase);
        ctx.getWriteCacheState(AccountReadableKVState.STATE_ID).put(accountID, account);
        assertThat(ctx.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEqualTo(map);
        writableKVStateBase.reset();
        assertThat(ctx.getWriteCacheState(AccountReadableKVState.STATE_ID)).isEqualTo(Map.of());
    }

    @Test
    void testGetOriginalValue() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, readableKVStateBase);
        when(readableKVStateBase.get(accountID)).thenReturn(account);
        assertThat(writableKVStateBase.getOriginalValue(accountID)).isEqualTo(account);
    }
}
