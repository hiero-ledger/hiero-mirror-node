// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionHashRepositoryTest extends Web3IntegrationTest {
    private final ContractTransactionHashRepository contractTransactionHashRepository;

    @Test
    void findByHashSuccessful() {
        var contractTransactionHash = domainBuilder.contractTransactionHash().persist();
        assertThat(contractTransactionHashRepository.findByHash(contractTransactionHash.getHash()))
                .contains(contractTransactionHash);
    }
}
