// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionRepositoryTest extends Web3IntegrationTest {
    private final TransactionRepository transactionRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        Transaction transaction = domainBuilder.transaction().persist();
        assertThat(transactionRepository.findById(transaction.getConsensusTimestamp()))
                .contains(transaction);
    }

    @Test
    void findByPayerAccountIdAndValidStartNsSuccessful() {
        // Given
        final var senderEntityId = domainBuilder.entityId();
        final var parentConsensusTimestamp = domainBuilder.timestamp();
        final var validStartNs = parentConsensusTimestamp - 1000L;

        var consensusTimestampInMillis = parentConsensusTimestamp;
        for (long i = 0; i < 10; i++) {
            final var consensusTimestampToSet = consensusTimestampInMillis;
            domainBuilder
                    .transaction()
                    .customize(transaction -> transaction
                            .consensusTimestamp(consensusTimestampToSet)
                            .payerAccountId(senderEntityId)
                            .validStartNs(validStartNs))
                    .persist();
            consensusTimestampInMillis += 1;
        }

        // When
        final var transactions = transactionRepository.findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
                senderEntityId, validStartNs);

        // Then
        var expectedConsensusTimestamp = parentConsensusTimestamp;
        for (final var transaction : transactions) {
            assertThat(transaction.getPayerAccountId()).isEqualTo(senderEntityId);
            assertThat(transaction.getValidStartNs()).isEqualTo(validStartNs);
            assertThat(transaction.getConsensusTimestamp()).isEqualTo(expectedConsensusTimestamp);
            expectedConsensusTimestamp += 1;
        }
    }
}
