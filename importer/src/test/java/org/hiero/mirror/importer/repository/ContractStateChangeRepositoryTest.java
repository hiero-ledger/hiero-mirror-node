// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractStateChangeRepositoryTest extends ImporterIntegrationTest {

    private final ContractStateChangeRepository contractStateChangeRepository;

    @Test
    void prune() {
        domainBuilder.contractStateChange().persist();
        var contractStateChange2 = domainBuilder.contractStateChange().persist();
        var contractStateChange3 = domainBuilder.contractStateChange().persist();

        contractStateChangeRepository.prune(contractStateChange2.getConsensusTimestamp());

        assertThat(contractStateChangeRepository.findAll()).containsExactly(contractStateChange3);
    }

    @Test
    void save() {
        ContractStateChange contractStateChange =
                domainBuilder.contractStateChange().persist();
        assertThat(contractStateChangeRepository.findById(contractStateChange.getId()))
                .get()
                .isEqualTo(contractStateChange);
    }
}
