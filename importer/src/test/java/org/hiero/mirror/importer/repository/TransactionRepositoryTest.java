// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionRepositoryTest extends ImporterIntegrationTest {

    private final TransactionRepository transactionRepository;

    @Test
    void prune() {
        domainBuilder.transaction().persist();
        var transaction2 = domainBuilder.transaction().persist();
        var transaction3 = domainBuilder.transaction().persist();

        transactionRepository.prune(transaction2.getConsensusTimestamp());

        assertThat(transactionRepository.findAll()).containsExactly(transaction3);
    }

    @Test
    void save() {
        var transaction = domainBuilder.transaction().get();
        transactionRepository.save(transaction);
        assertThat(transactionRepository.findById(transaction.getConsensusTimestamp()))
                .get()
                .isEqualTo(transaction);

        var t2 = domainBuilder
                .transaction()
                .customize(t -> t.maxCustomFees(null))
                .get();
        transactionRepository.save(t2);
        var actual = transactionRepository.findById(t2.getConsensusTimestamp());
        assertThat(actual).contains(t2);
    }
}
