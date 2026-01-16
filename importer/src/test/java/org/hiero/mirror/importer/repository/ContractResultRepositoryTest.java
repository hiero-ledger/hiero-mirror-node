// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractResultRepositoryTest extends ImporterIntegrationTest {

    private final ContractResultRepository contractResultRepository;

    @Test
    void prune() {
        domainBuilder.contractResult().persist();
        var contractResult2 = domainBuilder.contractResult().persist();
        var contractResult3 = domainBuilder.contractResult().persist();

        contractResultRepository.prune(contractResult2.getConsensusTimestamp());

        assertThat(contractResultRepository.findAll()).containsExactly(contractResult3);
    }

    @Test
    void save() {
        ContractResult contractResult = domainBuilder.contractResult().persist();
        assertThat(contractResultRepository.findById(contractResult.getId()))
                .get()
                .isEqualTo(contractResult);
    }
}
