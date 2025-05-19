// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractRepositoryTest extends ImporterIntegrationTest {

    private final ContractRepository contractRepository;

    @Test
    void save() {
        Contract contract = domainBuilder.contract().get();
        contractRepository.save(contract);
        assertThat(contractRepository.findById(contract.getId())).get().isEqualTo(contract);
    }
}
