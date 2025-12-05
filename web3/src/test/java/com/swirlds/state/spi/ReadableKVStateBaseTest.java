// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import java.util.Map;
import java.util.Set;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.MapReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ExtendWith(ContextExtension.class)
class ReadableKVStateBaseTest {

    @Test
    void testReadKeys() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final ReadableKVStateBase<AccountID, Account> readableKVStateBase =
                new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, Map.of());
        ContractCallContext.get()
                .getReadCacheState(AccountReadableKVState.STATE_ID)
                .put(accountID, account);
        assertThat(readableKVStateBase.readKeys()).isEqualTo(Set.of(accountID));
    }

    @Test
    void testResetCache() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final ReadableKVStateBase<AccountID, Account> readableKVStateBase =
                new MapReadableKVState<>(TokenService.NAME, AccountReadableKVState.STATE_ID, Map.of());
        readableKVStateBase.markRead(accountID, account);
        assertThat(readableKVStateBase.hasBeenRead(accountID)).isTrue();
        readableKVStateBase.reset();
        assertThat(readableKVStateBase.hasBeenRead(accountID)).isFalse();
    }
}
