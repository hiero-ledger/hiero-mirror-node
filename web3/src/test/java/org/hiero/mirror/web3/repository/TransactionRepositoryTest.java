// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CRYPTOCREATEACCOUNT;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
    void findByTransactionIdReturnsParentContractTransaction() {
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
        final var result = transactionRepository.findByTransactionId(
                senderEntityId.getId(), validStartNs, validStartNs, parentConsensusTimestamp + 10);

        // Then
        assertThat(result).contains(parentTransaction);
    }

    @Test
    void findByTransactionIdEmptyWhenOnlyNonContractTransactions() {
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
        assertThat(transactionRepository.findByTransactionId(
                        senderEntityId.getId(), validStartNs, validStartNs, consensusTimestamp + 10))
                .isEmpty();
    }

    @Test
    void findSuccessfulCryptoCreateChildEntityIdsReturnsSuccessfulChildren() {
        final var parentConsensusTimestamp = domainBuilder.timestamp();
        final var hollowAccountId = domainBuilder.entityId();
        final var failedAccountId = domainBuilder.entityId();
        final var otherParentAccountId = domainBuilder.entityId();

        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp - 1L)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(hollowAccountId)
                        .nonce(1)
                        .type(CRYPTOCREATEACCOUNT.getProtoId()))
                .persist();
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp - 2L)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(failedAccountId)
                        .nonce(2)
                        .type(CRYPTOCREATEACCOUNT.getProtoId())
                        .result(ResponseCodeEnum.INVALID_SIGNATURE.getNumber()))
                .persist();
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp + 1L)
                        .parentConsensusTimestamp(parentConsensusTimestamp + 100L)
                        .entityId(otherParentAccountId)
                        .nonce(1)
                        .type(CRYPTOCREATEACCOUNT.getProtoId()))
                .persist();
        final var outsideWindowAccountId = domainBuilder.entityId();
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(
                                parentConsensusTimestamp - TransactionRepository.PRECEDING_CRYPTO_CREATE_WINDOW_NS - 1L)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(outsideWindowAccountId)
                        .nonce(3)
                        .type(CRYPTOCREATEACCOUNT.getProtoId()))
                .persist();

        assertThat(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(parentConsensusTimestamp))
                .containsExactly(hollowAccountId.getId());
    }
}
