// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractLogRepositoryTest extends ImporterIntegrationTest {

    private final ContractLogRepository contractLogRepository;

    @Test
    void prune() {
        domainBuilder.contractLog().persist();
        var contractLog2 = domainBuilder.contractLog().persist();
        var contractLog3 = domainBuilder.contractLog().persist();

        contractLogRepository.prune(contractLog2.getConsensusTimestamp());

        assertThat(contractLogRepository.findAll()).containsExactly(contractLog3);
    }

    @Test
    void save() {
        ContractLog contractLog = domainBuilder.contractLog().persist();
        assertThat(contractLogRepository.findById(contractLog.getId())).get().isEqualTo(contractLog);
    }
}
