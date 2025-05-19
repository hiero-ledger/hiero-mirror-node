// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAllowanceDatabaseAccessorTest extends Web3IntegrationTest {

    private final TokenAllowanceDatabaseAccessor tokenAllowanceDatabaseAccessor;

    @Test
    void testGet() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(tokenAllowanceDatabaseAccessor.get(tokenAllowance.getId(), Optional.empty()))
                .get()
                .isEqualTo(tokenAllowance);
    }

    @Test
    void testGetHistorical() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(tokenAllowanceDatabaseAccessor.get(
                        tokenAllowance.getId(), Optional.of(tokenAllowance.getTimestampLower())))
                .get()
                .isEqualTo(tokenAllowance);
    }
}
