// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CRYPTOCREATEACCOUNT;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CRYPTOTRANSFER;
import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;
import static org.hiero.mirror.common.domain.transaction.TransactionType.SCHEDULECREATE;
import static org.hiero.mirror.common.util.DomainUtils.NANOS_PER_SECOND;
import static org.hiero.mirror.web3.utils.Constants.MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;
import static org.hiero.mirror.web3.utils.Constants.MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
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
        final var result = findByTransactionId(senderEntityId.getId(), validStartNs, parentConsensusTimestamp + 10);

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
        assertThat(findByTransactionId(senderEntityId.getId(), validStartNs, consensusTimestamp + 10))
                .isEmpty();
    }

    @Test
    void findByTransactionIdReturnsScheduledExecutionAfterNormalWindow() {
        final var payerAccountId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var consensusTimestamp = validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS + NANOS_PER_SECOND;
        final var scheduledTransaction =
                contractTransaction(payerAccountId, contractId, validStartNs, consensusTimestamp, 0, true, null);

        assertThat(findByTransactionId(
                        payerAccountId.getId(),
                        validStartNs,
                        validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS))
                .contains(scheduledTransaction);
    }

    @Test
    void findByTransactionIdEmptyWhenScheduledExecutionIsPastScheduledWindow() {
        final var payerAccountId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        contractTransaction(
                payerAccountId,
                domainBuilder.entityId(),
                validStartNs,
                validStartNs + MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS + NANOS_PER_SECOND,
                0,
                true,
                null);

        assertThat(findByTransactionId(
                        payerAccountId.getId(),
                        validStartNs,
                        validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS))
                .isEmpty();
    }

    @Test
    void findByTransactionIdReturnsScheduledExecutionOverScheduleCreate() {
        final var payerAccountId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var scheduleCreateTimestamp = validStartNs + 1_000L;

        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(scheduleCreateTimestamp)
                        .nonce(0)
                        .payerAccountId(payerAccountId)
                        .scheduled(false)
                        .type(SCHEDULECREATE.getProtoId())
                        .validStartNs(validStartNs))
                .persist();
        final var scheduledTransaction = contractTransaction(
                payerAccountId, domainBuilder.entityId(), validStartNs, scheduleCreateTimestamp + 1L, 0, true, null);

        assertThat(findByTransactionId(payerAccountId.getId(), validStartNs, scheduleCreateTimestamp + 10))
                .contains(scheduledTransaction);
    }

    @Test
    void findByTransactionIdIgnoresScheduledExecutionWithNonZeroNonce() {
        final var payerAccountId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var consensusTimestamp = validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS + NANOS_PER_SECOND;
        contractTransaction(payerAccountId, domainBuilder.entityId(), validStartNs, consensusTimestamp, 1, true, null);

        assertThat(findByTransactionId(
                        payerAccountId.getId(),
                        validStartNs,
                        validStartNs + MAX_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS))
                .isEmpty();
    }

    @Test
    void findByTransactionIdIgnoresDuplicateTransaction() {
        final var payerAccountId = domainBuilder.entityId();
        final var contractId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var consensusTimestamp = validStartNs + 1_000L;

        final var original = domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(consensusTimestamp)
                        .entityId(contractId)
                        .nonce(0)
                        .payerAccountId(payerAccountId)
                        .result(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED.getNumber())
                        .type(CONTRACTCALL.getProtoId())
                        .validStartNs(validStartNs))
                .persist();
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(consensusTimestamp + 1L)
                        .entityId(contractId)
                        .nonce(0)
                        .payerAccountId(payerAccountId)
                        .result(ResponseCodeEnum.DUPLICATE_TRANSACTION.getNumber())
                        .type(CONTRACTCALL.getProtoId())
                        .validStartNs(validStartNs))
                .persist();

        assertThat(findByTransactionId(payerAccountId.getId(), validStartNs, consensusTimestamp + 10))
                .contains(original);
    }

    @Test
    void findByTransactionIdIgnoresHookDispatch() {
        final var payerAccountId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var parentConsensusTimestamp = validStartNs + 1_000L;
        final var hookConsensusTimestamp = parentConsensusTimestamp + 1L;

        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp)
                        .entityId(payerAccountId)
                        .nonce(0)
                        .parentConsensusTimestamp(null)
                        .payerAccountId(payerAccountId)
                        .type(CRYPTOTRANSFER.getProtoId())
                        .validStartNs(validStartNs))
                .persist();
        contractTransaction(
                payerAccountId,
                hookContractEntityId(),
                validStartNs,
                hookConsensusTimestamp,
                1,
                false,
                parentConsensusTimestamp);

        assertThat(findByTransactionId(payerAccountId.getId(), validStartNs, parentConsensusTimestamp + 10))
                .isEmpty();
    }

    @Test
    void findByTransactionIdIgnoresInnerContractCall() {
        final var payerAccountId = domainBuilder.entityId();
        final var validStartNs = domainBuilder.timestamp();
        final var consensusTimestamp = validStartNs + 1_000L;
        contractTransaction(
                payerAccountId,
                domainBuilder.entityId(),
                validStartNs,
                consensusTimestamp,
                1,
                false,
                consensusTimestamp - 1L);

        assertThat(findByTransactionId(payerAccountId.getId(), validStartNs, consensusTimestamp + 10))
                .isEmpty();
    }

    @Test
    void findSuccessfulCryptoCreateChildEntityIdsReturnsSuccessfulChildren() {
        final var parentConsensusTimestamp = domainBuilder.timestamp();
        final var payerAccountId = domainBuilder.entityId();
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
                        .payerAccountId(payerAccountId)
                        .type(CRYPTOCREATEACCOUNT.getProtoId()))
                .persist();
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(parentConsensusTimestamp - 2L)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(failedAccountId)
                        .nonce(2)
                        .payerAccountId(payerAccountId)
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
                        .payerAccountId(payerAccountId)
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
                        .payerAccountId(payerAccountId)
                        .type(CRYPTOCREATEACCOUNT.getProtoId()))
                .persist();

        assertThat(transactionRepository.findSuccessfulCryptoCreateChildEntityIds(
                        parentConsensusTimestamp, payerAccountId.getId()))
                .containsExactly(hollowAccountId.getId());
    }

    private Optional<Transaction> findByTransactionId(
            final long payerAccountId, final long validStartNs, final long consensusTimestampEnd) {
        return transactionRepository.findByTransactionId(
                payerAccountId,
                validStartNs,
                validStartNs,
                consensusTimestampEnd,
                validStartNs + MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS);
    }

    private Transaction contractTransaction(
            final EntityId payerAccountId,
            final EntityId contractId,
            final long validStartNs,
            final long consensusTimestamp,
            final int nonce,
            final boolean scheduled,
            final Long parentConsensusTimestamp) {
        return domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(consensusTimestamp)
                        .entityId(contractId)
                        .nonce(nonce)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .payerAccountId(payerAccountId)
                        .result(ResponseCodeEnum.SUCCESS.getNumber())
                        .scheduled(scheduled)
                        .type(CONTRACTCALL.getProtoId())
                        .validStartNs(validStartNs))
                .persist();
    }

    private static EntityId hookContractEntityId() {
        final var commonProperties = CommonProperties.getInstance();
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), RecordItem.HOOK_CONTRACT_NUM);
    }
}
