// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AccountReadableKVStateIntegrationTest extends Web3IntegrationTest {

    private final AccountReadableKVState accountReadableKVState;

    @Test
    void delegationAddressMatchesEntityField() {
        final var rawDelegationAddress = new byte[] {0x01, 0x02, 0x03, 0x04};
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.delegationAddress(rawDelegationAddress))
                .persist();
        final var accountId = new AccountID(
                entity.getShard(), entity.getRealm(), new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum()));

        final Account account = ContractCallContext.run(ctx -> accountReadableKVState.get(accountId));

        assertThat(account).isNotNull();
        assertThat(account.delegationAddress()).isEqualTo(Bytes.wrap(rawDelegationAddress));
    }

    @Test
    void delegationAddressIsEmptyWhenNotSet() {
        final var entity =
                domainBuilder.entity().customize(e -> e.delegationAddress(null)).persist();
        final var accountId = new AccountID(
                entity.getShard(), entity.getRealm(), new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum()));

        final Account account = ContractCallContext.run(ctx -> accountReadableKVState.get(accountId));

        assertThat(account).isNotNull();
        assertThat(account.delegationAddress()).isEqualTo(Bytes.EMPTY);
    }
}
