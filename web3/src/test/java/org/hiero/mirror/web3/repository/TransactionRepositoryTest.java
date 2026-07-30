// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CRYPTOCREATEACCOUNT;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;

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
    void findByPayerAccountIdAndValidStartNsReturnsParentContractTransaction() {
        // Given
        final var senderEntityId = domainBuilder.entityId();
        final var parentConsensusTimestamp = domainBuilder.timestamp();
        final var validStartNs = parentConsensusTimestamp - 1000L;
        final var precedingHollowCreateTimestamp = parentConsensusTimestamp - 1L;

        // Preceding hollow account create shares the transaction ID but has nonce > 0 and an earlier timestamp
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(precedingHollowCreateTimestamp)
                        .nonce(1)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(senderEntityId)
                        .type(CRYPTOCREATEACCOUNT.getProtoId())
                        .validStartNs(validStartNs))
                .persist();

        final var parentTransaction = domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp)
                        .nonce(0)
                        .payerAccountId(senderEntityId)
                        .type(ETHEREUMTRANSACTION.getProtoId())
                        .validStartNs(validStartNs))
                .persist();

        // Later child contract call with the same transaction ID
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp + 1L)
                        .nonce(2)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(senderEntityId)
                        .type(CONTRACTCALL.getProtoId())
                        .validStartNs(validStartNs))
                .persist();

        // When
        final var result = transactionRepository.findByPayerAccountIdAndValidStartNs(
                senderEntityId.getId(), validStartNs, validStartNs, parentConsensusTimestamp + 10);

        // Then
        assertThat(result).contains(parentTransaction);
    }

    @Test
    void findByPayerAccountIdAndValidStartNsEmptyWhenOnlyNonContractTransactions() {
        // Given
        final var senderEntityId = domainBuilder.entityId();
        final var consensusTimestamp = domainBuilder.timestamp();
        final var validStartNs = consensusTimestamp - 1000L;

        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(consensusTimestamp)
                        .nonce(0)
                        .payerAccountId(senderEntityId)
                        .type(CRYPTOCREATEACCOUNT.getProtoId())
                        .validStartNs(validStartNs))
                .persist();

        // When / Then
        assertThat(transactionRepository.findByPayerAccountIdAndValidStartNs(
                        senderEntityId.getId(), validStartNs, validStartNs, consensusTimestamp + 10))
                .isEmpty();
    }
}
