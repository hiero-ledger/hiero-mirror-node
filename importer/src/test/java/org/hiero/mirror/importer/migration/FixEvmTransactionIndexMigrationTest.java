// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixEvmTransactionIndexMigrationTest
        extends AbstractAsyncJavaMigrationTest<FixEvmTransactionIndexMigration> {

    private static final long INTERVAL = Duration.ofHours(12).toNanos();

    @Getter
    private final FixEvmTransactionIndexMigration migration;

    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final RecordFileRepository recordFileRepository;

    @Test
    void emptyDatabase() {
        runMigration();
        waitForCompletion();

        assertThat(recordFileRepository.findAll()).isEmpty();
        assertThat(contractResultRepository.findAll()).isEmpty();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void topLevelEvmTransactionsGetSequentialIndices() {
        // given
        final var block = persistBlock(0);
        final var contractCallTimestamp = block.getConsensusStart() + 100;
        final var contractCreateTimestamp = block.getConsensusStart() + 200;
        final var ethereumTxTimestamp = block.getConsensusStart() + 300;

        persistTransaction(contractCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(contractCreateTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 0, false, null);
        persistTransaction(ethereumTxTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);

        final var contractCallResult = persistContractResult(contractCallTimestamp, 0);
        final var contractCreateResult = persistContractResult(contractCreateTimestamp, 1);
        final var ethereumTxResult = persistContractResult(ethereumTxTimestamp, 2);
        final var contractCallLog = persistContractLog(contractCallTimestamp, 5);
        final var ethereumTxLog = persistContractLog(ethereumTxTimestamp, 7);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(contractCreateResult.getConsensusTimestamp(), 1);
        assertContractResultIndex(ethereumTxResult.getConsensusTimestamp(), 2);
        assertContractLogIndex(contractCallLog.getConsensusTimestamp(), 0);
        assertContractLogIndex(ethereumTxLog.getConsensusTimestamp(), 2);
    }

    @Test
    void scheduledEvmTransactionGetsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var scheduledCallTimestamp = block.getConsensusStart() + 100;

        persistTransaction(scheduledCallTimestamp, TransactionType.CONTRACTCALL, 0, true, null);

        final var contractResult = persistContractResult(scheduledCallTimestamp, 99);
        final var contractLog = persistContractLog(scheduledCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(contractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void evmChildInheritsParentIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var parentCallTimestamp = block.getConsensusStart() + 200;
        final var childCreateTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistTransaction(parentCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(childCreateTimestamp, TransactionType.CONTRACTCREATEINSTANCE, 1, false, parentCallTimestamp);

        final var parentContractResult = persistContractResult(parentCallTimestamp, 99);
        final var childContractResult = persistContractResult(childCreateTimestamp, 99);
        final var childContractLog = persistContractLog(childCreateTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(parentContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(childContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(childContractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void deeplyNestedEvmTransactionsInheritRootIndexAtAnyDepth() {
        // given
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        final var rootContractResult = persistContractResult(rootTimestamp, 99);

        final var depth = 10;
        var parentTimestamp = rootTimestamp;
        final var descendantResults = new ArrayList<ContractResult>();
        final var descendantLogs = new ArrayList<ContractLog>();
        for (int level = 1; level <= depth; level++) {
            final var timestamp = block.getConsensusStart() + 100 + (level * 100L);
            persistTransaction(timestamp, TransactionType.CONTRACTCALL, level, false, parentTimestamp);
            descendantResults.add(persistContractResult(timestamp, 99));
            descendantLogs.add(persistContractLog(timestamp, 99));
            parentTimestamp = timestamp;
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootContractResult.getConsensusTimestamp(), 0);
        descendantResults.forEach(result -> assertContractResultIndex(result.getConsensusTimestamp(), 0));
        descendantLogs.forEach(log -> assertContractLogIndex(log.getConsensusTimestamp(), 0));
    }

    @Test
    void atomicBatchInnerTransactionsGetSequentialIndices() {
        // given
        final var block = persistBlock(0);
        final var batchTimestamp = block.getConsensusStart() + 100;
        final var innerCallTimestamp = block.getConsensusStart() + 200;
        final var innerEthereumTimestamp = block.getConsensusStart() + 300;

        persistTransaction(batchTimestamp, TransactionType.ATOMIC_BATCH, 0, false, null);
        // Each inner transaction is independently signed and keeps nonce 0, so each is treated as a root.
        persistTransaction(innerCallTimestamp, TransactionType.CONTRACTCALL, 0, false, batchTimestamp);
        persistTransaction(innerEthereumTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, batchTimestamp);

        final var innerCallResult = persistContractResult(innerCallTimestamp, 99);
        final var innerEthereumResult = persistContractResult(innerEthereumTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(innerCallResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(innerEthereumResult.getConsensusTimestamp(), 1);
    }

    @Test
    void hookEvmChildGetsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCall1Timestamp = block.getConsensusStart() + 200;
        final var hookCall2Timestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCall1Timestamp, 1, cryptoTransferTimestamp);
        persistHookDispatchTransaction(hookCall2Timestamp, 2, cryptoTransferTimestamp);

        final var hookContractResult1 = persistHookDispatchContractResult(hookCall1Timestamp, 42);
        final var hookContractLog1 = persistContractLog(hookCall1Timestamp, 42);
        final var hookContractResult2 = persistHookDispatchContractResult(hookCall2Timestamp, 43);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult1.getConsensusTimestamp(), 0);
        assertContractLogIndex(hookContractLog1.getConsensusTimestamp(), 0);
        assertContractResultIndex(hookContractResult2.getConsensusTimestamp(), 1);
    }

    @Test
    void nestedHookEvmTransactionInheritsHookIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var nestedHookCallTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(nestedHookCallTimestamp, TransactionType.CONTRACTCALL, 2, false, hookCallTimestamp);

        final var hookContractResult = persistHookDispatchContractResult(hookCallTimestamp, 42);
        final var nestedHookContractResult = persistContractResult(nestedHookCallTimestamp, 43);
        final var nestedHookContractLog = persistContractLog(nestedHookCallTimestamp, 43);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(nestedHookContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(nestedHookContractLog.getConsensusTimestamp(), 0);
    }

    @Test
    void hookDescendantSharingOriginalTriggerInheritsHookIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var hookInternalCallTimestamp = block.getConsensusStart() + 300;
        final var unrelatedTopLevelCallTimestamp = block.getConsensusStart() + 400;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(hookInternalCallTimestamp, TransactionType.CONTRACTCALL, 2, false, cryptoTransferTimestamp);
        persistTransaction(unrelatedTopLevelCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var hookContractResult = persistHookDispatchContractResult(hookCallTimestamp, 42);
        final var hookInternalContractResult = persistContractResult(hookInternalCallTimestamp, 43);
        final var hookInternalContractLog = persistContractLog(hookInternalCallTimestamp, 43);
        final var unrelatedContractResult = persistContractResult(unrelatedTopLevelCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(hookInternalContractResult.getConsensusTimestamp(), 0);
        assertContractLogIndex(hookInternalContractLog.getConsensusTimestamp(), 0);
        assertContractResultIndex(unrelatedContractResult.getConsensusTimestamp(), 1);
    }

    @Test
    void skipsNonEvmTransactions() {
        // given
        final var block = persistBlock(0);
        persistTransaction(block.getConsensusStart() + 100, TransactionType.CRYPTOTRANSFER, 0, false, null);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(contractResultRepository.findAll()).isEmpty();
    }

    @Test
    void failedEvmRootWithoutContractResultDoesNotConsumeIndex() {
        // given
        final var block = persistBlock(0);
        final var failedEthereumTimestamp = block.getConsensusStart() + 100;
        final var successfulCallTimestamp = block.getConsensusStart() + 200;

        persistTransaction(failedEthereumTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);
        persistTransaction(successfulCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var contractCallResult = persistContractResult(successfulCallTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 0);
    }

    @Test
    void indicesResetPerBlock() {
        // given
        final var firstBlock = persistBlock(0);
        final var secondBlock = persistBlock(1);
        final var firstTimestamp = firstBlock.getConsensusStart() + 100;
        final var secondTimestamp = secondBlock.getConsensusStart() + 100;

        persistTransaction(firstTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(secondTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

        final var firstContractResult = persistContractResult(firstTimestamp, 99);
        final var secondContractResult = persistContractResult(secondTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(firstContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(secondContractResult.getConsensusTimestamp(), 0);
    }

    @Test
    void processesMultipleBatchIntervals() {
        // given
        final var earlyBlock = persistBlock(0);
        final var recentBase =
                earlyBlock.getConsensusEnd() + INTERVAL - Duration.ofSeconds(1).toNanos();
        final var recentBlock = domainBuilder
                .recordFile()
                .customize(r -> r.index(1L)
                        .consensusStart(recentBase)
                        .consensusEnd(recentBase + Duration.ofSeconds(2).toNanos()))
                .persist();

        final var earlyTimestamp = earlyBlock.getConsensusStart() + 100;
        final var recentTimestamp = recentBlock.getConsensusStart() + 100;

        persistTransaction(earlyTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(recentTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);

        final var earlyContractResult = persistContractResult(earlyTimestamp, 99);
        final var recentContractResult = persistContractResult(recentTimestamp, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(earlyContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(recentContractResult.getConsensusTimestamp(), 0);
    }

    private RecordFile persistBlock(long index) {
        final var base = domainBuilder.timestamp() + index * INTERVAL;
        return domainBuilder
                .recordFile()
                .customize(r -> r.index(index)
                        .consensusStart(base)
                        .consensusEnd(base + Duration.ofSeconds(2).toNanos()))
                .persist();
    }

    private void persistTransaction(
            long consensusTimestamp,
            TransactionType type,
            int nonce,
            boolean scheduled,
            Long parentConsensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(type.getProtoId())
                        .nonce(nonce)
                        .scheduled(scheduled)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(null))
                .persist();
    }

    private void persistHookDispatchTransaction(long consensusTimestamp, int nonce, Long parentConsensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(TransactionType.CONTRACTCALL.getProtoId())
                        .nonce(nonce)
                        .scheduled(false)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .entityId(EntityId.of(0L, 0L, RecordItem.HOOK_CONTRACT_NUM)))
                .persist();
    }

    private ContractResult persistContractResult(long consensusTimestamp, int wrongIndex) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp).transactionIndex(wrongIndex))
                .persist();
    }

    private ContractResult persistHookDispatchContractResult(long consensusTimestamp, int wrongIndex) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(0L, 0L, RecordItem.HOOK_CONTRACT_NUM)
                                .getId())
                        .transactionIndex(wrongIndex))
                .persist();
    }

    private ContractLog persistContractLog(long consensusTimestamp, int wrongIndex) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp).transactionIndex(wrongIndex))
                .persist();
    }

    private void assertContractResultIndex(long consensusTimestamp, int expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }

    private void assertContractLogIndex(long consensusTimestamp, int expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }
}
