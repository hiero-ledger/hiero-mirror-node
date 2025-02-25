// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractStateRepositoryTest extends ImporterIntegrationTest {

    private final ContractStateRepository contractStateRepository;

    @Test
    void save() {
        var contractState = domainBuilder.contractState().persist();
        assertThat(contractStateRepository.findById(contractState.getId()))
                .get()
                .isEqualTo(contractState);
    }
}
