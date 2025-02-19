/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.state.core.MapReadableKVState;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class ReadableKVStateBaseTest {

    @Test
    void testReadKeys() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final ReadableKVStateBase<AccountID, Account> readableKVStateBase = new MapReadableKVState<>(
                AccountReadableKVState.KEY, new ConcurrentHashMap<>(Map.of(accountID, account)));
        assertThat(readableKVStateBase.readKeys()).isEqualTo(Set.of(accountID));
    }

    @Test
    void testResetCache() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final ReadableKVStateBase<AccountID, Account> readableKVStateBase =
                new MapReadableKVState<>(AccountReadableKVState.KEY, new ConcurrentHashMap<>());
        readableKVStateBase.markRead(accountID, account);
        assertThat(readableKVStateBase.hasBeenRead(accountID)).isTrue();
        readableKVStateBase.reset();
        assertThat(readableKVStateBase.hasBeenRead(accountID)).isFalse();
    }
}
