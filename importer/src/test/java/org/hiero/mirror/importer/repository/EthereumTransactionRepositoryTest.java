// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EthereumTransactionRepositoryTest extends ImporterIntegrationTest {

    private final EthereumTransactionRepository ethereumTransactionRepository;

    @Test
    void prune() {
        domainBuilder.ethereumTransaction(true).persist();
        var ethereumTransaction2 = domainBuilder.ethereumTransaction(false).persist();
        var ethereumTransaction3 = domainBuilder.ethereumTransaction(true).persist();

        ethereumTransactionRepository.prune(ethereumTransaction2.getConsensusTimestamp());

        assertThat(ethereumTransactionRepository.findAll()).containsExactly(ethereumTransaction3);
    }

    @Test
    void saveInitCode() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(true).persist();
        assertThat(ethereumTransactionRepository.findById(ethereumTransaction.getId()))
                .get()
                .isEqualTo(ethereumTransaction);
    }

    @Test
    void saveFileId() {
        var ethereumTransaction = domainBuilder.ethereumTransaction(false).persist();
        assertThat(ethereumTransactionRepository.findById(ethereumTransaction.getId()))
                .get()
                .isEqualTo(ethereumTransaction);
    }
}
