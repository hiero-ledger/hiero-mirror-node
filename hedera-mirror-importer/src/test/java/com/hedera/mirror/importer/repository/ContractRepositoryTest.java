// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
