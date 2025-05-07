// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionRepositoryTest extends ImporterIntegrationTest {
    private final ContractTransactionRepository contractTransactionRepository;

    @Test
    void prune() {
        domainBuilder.contractTransaction().persist();
        var deletePoint = domainBuilder.contractTransaction().persist();
        var keptTransaction = domainBuilder.contractTransaction().persist();

        contractTransactionRepository.prune(deletePoint.getConsensusTimestamp());
        assertThat(contractTransactionRepository.findAll()).containsExactly(keptTransaction);
    }

    @Test
    void save() {
        var contractTransaction = domainBuilder.contractTransaction().persist();
        assertThat(contractTransactionRepository.findById(contractTransaction.getId()))
                .get()
                .isEqualTo(contractTransaction);
    }
}
