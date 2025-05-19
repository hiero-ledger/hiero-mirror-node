// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static org.hiero.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAccountDatabaseAccessorTest extends Web3IntegrationTest {

    private final TokenAccountDatabaseAccessor tokenAccountDatabaseAccessor;

    @Test
    void testGet() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED))
                .persist();

        assertThat(tokenAccountDatabaseAccessor
                        .get(tokenAccount.getId(), Optional.empty())
                        .get())
                .returns(tokenAccount.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(tokenAccount.getKycStatus(), TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance);
    }

    @Test
    void testGetHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED))
                .persist();

        assertThat(tokenAccountDatabaseAccessor
                        .get(tokenAccount.getId(), Optional.of(tokenAccount.getTimestampLower()))
                        .get())
                .returns(tokenAccount.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(tokenAccount.getKycStatus(), TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance);
    }
}
