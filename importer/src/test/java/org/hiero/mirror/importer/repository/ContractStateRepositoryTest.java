// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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
