// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract.precompile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class PrecompileMapperTest extends Web3IntegrationTest {

    private final PrecompileMapper precompileMapper;

    @Test
    void nonExistingAbiReturnsEmpty() {
        int functionSelector = 0x11111111;
        final var result = precompileMapper.lookup(functionSelector);
        assertThat(result).isEmpty();
    }

    @Test
    void supportedPrecompileIsFound() {
        int functionSelector = 0x00000000;
        final var result = precompileMapper.lookup(functionSelector);
        assertThat(result).isNotEmpty();
    }
}
