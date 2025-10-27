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
    void findInTimestampRange() {
        // given
        var transaction1 = domainBuilder.transaction().persist();
        var transaction2 = domainBuilder.transaction().persist();

        // when, then
        assertThat(transactionRepository.findInTimestampRange(
                        transaction2.getConsensusTimestamp(), transaction1.getConsensusTimestamp() - 1, 2))
                .containsExactly(transaction1, transaction2);
        assertThat(transactionRepository.findInTimestampRange(
                        transaction2.getConsensusTimestamp(), transaction1.getConsensusTimestamp() - 1, 1))
                .containsExactly(transaction1);
        assertThat(transactionRepository.findInTimestampRange(
                        transaction2.getConsensusTimestamp(), transaction1.getConsensusTimestamp(), 2))
                .containsExactly(transaction2);
        assertThat(transactionRepository.findInTimestampRange(Long.MAX_VALUE, transaction2.getConsensusTimestamp(), 2))
                .isEmpty();
    }

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
