// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
