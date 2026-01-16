// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractResultRepositoryTest extends Web3IntegrationTest {

    private final ContractResultRepository contractResultRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var contractResult = domainBuilder.contractResult().persist();
        assertThat(contractResultRepository.findById(contractResult.getConsensusTimestamp()))
                .contains(contractResult);
    }
}
