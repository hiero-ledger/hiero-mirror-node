// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionHashRepositoryTest extends ImporterIntegrationTest {
    private final ContractTransactionHashRepository contractTransactionHashRepository;

    @Test
    void prune() {
        domainBuilder.contractTransactionHash().persist();
        var deletePoint = domainBuilder.contractTransactionHash().persist();
        var keptHash = domainBuilder.contractTransactionHash().persist();

        contractTransactionHashRepository.prune(deletePoint.getConsensusTimestamp());
        assertThat(contractTransactionHashRepository.findAll()).containsExactly(keptHash);
    }

    @Test
    void save() {
        var hash = domainBuilder.contractTransactionHash().persist();
        assertThat(contractTransactionHashRepository.findById(hash.getHash()))
                .get()
                .isEqualTo(hash);
    }
}
