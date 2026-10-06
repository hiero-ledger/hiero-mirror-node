// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixScheduledAndHookEvmTransactionIndexMigrationTest
        extends AbstractAsyncJavaMigrationTest<FixScheduledAndHookEvmTransactionIndexMigration> {

    private static final long INTERVAL = Duration.ofHours(3).toNanos();

    @Getter
    private final FixScheduledAndHookEvmTransactionIndexMigration migration;

    @Test
    void emptyDatabase() {
        // when
        runMigration();
        waitForCompletion();

        // then - no exception, nothing to do
    }

    @Test
    void scheduledTransactionGetsOwnIndexInsteadOfInheritingUnrelatedPrecedingRoot() {
        // given
        final var block = persistBlock(0);
        final var unrelatedRootTimestamp = block.getConsensusStart() + 100;
        final var scheduledTimestamp = block.getConsensusStart() + 200;

        persistTransaction(unrelatedRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(scheduledTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistScheduleExecution(scheduledTimestamp);

        persistContractResult(unrelatedRootTimestamp, 0, 0); // already correct
        // simulates what the old buggy migration actually computed: inherited the unrelated root's index
        persistContractResult(scheduledTimestamp, 53, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(unrelatedRootTimestamp, 0);
        assertContractResultIndex(scheduledTimestamp, 1);
    }

    @Test
    void scheduledTransactionAsOnlyCandidateInBlockGetsIndexZero() {
        // given
        final var block = persistBlock(0);
        final var scheduledTimestamp = block.getConsensusStart() + 100;

        persistTransaction(scheduledTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistScheduleExecution(scheduledTimestamp);
        // simulates the old migration's actual (negative) output
        persistContractResult(scheduledTimestamp, 53, -1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(scheduledTimestamp, 0);
    }

    @Test
    void hookChainSiblingInheritsHookIndexEvenWithUnrelatedRootInBetween() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var unrelatedRootTimestamp = block.getConsensusStart() + 250;
        final var siblingTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(unrelatedRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(siblingTimestamp, TransactionType.CONTRACTCALL, 3, false, cryptoTransferTimestamp);

        persistHookDispatchContractResult(hookCallTimestamp, 0);
        persistContractResult(unrelatedRootTimestamp, 0, 1);
        // simulates the old migration's actual output: inherited the unrelated root's index (1), not the hook's (0)
        persistContractResult(siblingTimestamp, 3, 1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookCallTimestamp, 0);
        assertContractResultIndex(unrelatedRootTimestamp, 1);
        assertContractResultIndex(siblingTimestamp, 0);
    }

    @Test
    void hookChainBreaksOnNonContractSiblingGetsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var cryptoTransferTimestamp = block.getConsensusStart() + 100;
        final var hookCallTimestamp = block.getConsensusStart() + 200;
        final var breakingSiblingTimestamp = block.getConsensusStart() + 250;
        final var afterBreakTimestamp = block.getConsensusStart() + 300;

        persistTransaction(cryptoTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, null);
        persistHookDispatchTransaction(hookCallTimestamp, 1, cryptoTransferTimestamp);
        persistTransaction(breakingSiblingTimestamp, TransactionType.CRYPTOTRANSFER, 2, false, cryptoTransferTimestamp);
        persistTransaction(afterBreakTimestamp, TransactionType.CONTRACTCALL, 3, false, cryptoTransferTimestamp);

        persistHookDispatchContractResult(hookCallTimestamp, 0);
        // simulates the old migration wrongly making this inherit the hook's index across the break
        persistContractResult(afterBreakTimestamp, 3, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookCallTimestamp, 0);
        assertContractResultIndex(afterBreakTimestamp, 1);
    }

    @Test
    void unaffectedBlockIsNotTouched() {
        // given
        final var block = persistBlock(0);
        final var timestamp = block.getConsensusStart() + 100;
        persistTransaction(timestamp, TransactionType.CONTRACTCALL, 0, false, null);
        // deliberately a value the calculator would never produce (99), to prove this block is never recomputed
        persistContractResult(timestamp, 0, 99);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(timestamp, 99);
    }

    @Test
    void scheduledSyntheticLogAfterScheduledContractCallIsShifted() {
        // given
        final var block = persistBlock(0);
        final var unrelatedRootTimestamp = block.getConsensusStart() + 100;
        final var scheduledCallTimestamp = block.getConsensusStart() + 200;
        final var scheduledTransferTimestamp = block.getConsensusStart() + 300;

        persistTransaction(unrelatedRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(scheduledCallTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistTransaction(scheduledTransferTimestamp, TransactionType.CRYPTOTRANSFER, 54, true, null);
        persistScheduleExecution(scheduledCallTimestamp);
        persistScheduleExecution(scheduledTransferTimestamp);

        persistContractResult(unrelatedRootTimestamp, 0, 0);
        persistContractResult(scheduledCallTimestamp, 53, 0);
        persistSyntheticContractLog(scheduledTransferTimestamp, 1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(unrelatedRootTimestamp, 0);
        assertContractResultIndex(scheduledCallTimestamp, 1);
        assertContractLogIndex(scheduledTransferTimestamp, 2);
    }

    @Test
    void multipleSyntheticLogsAtSameTimestamp() {
        // given
        final var block = persistBlock(0);
        final var scheduledCallTimestamp = block.getConsensusStart() + 100;
        final var scheduledTransferTimestamp = block.getConsensusStart() + 200;

        persistTransaction(scheduledCallTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistTransaction(scheduledTransferTimestamp, TransactionType.CRYPTOTRANSFER, 54, true, null);
        persistScheduleExecution(scheduledCallTimestamp);
        persistScheduleExecution(scheduledTransferTimestamp);

        persistContractResult(scheduledCallTimestamp, 53, -1);
        persistSyntheticContractLog(scheduledTransferTimestamp, 0);
        persistSyntheticContractLog(scheduledTransferTimestamp, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(scheduledCallTimestamp, 0);
        assertThat(jdbcOperations.queryForList(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        scheduledTransferTimestamp))
                .containsExactly(1, 1);
    }

    @Test
    void scheduledContractCallLogsFollowFixedIndex() {
        // given
        final var block = persistBlock(0);
        final var unrelatedRootTimestamp = block.getConsensusStart() + 100;
        final var scheduledTimestamp = block.getConsensusStart() + 200;

        persistTransaction(unrelatedRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(scheduledTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistScheduleExecution(scheduledTimestamp);

        persistContractResult(unrelatedRootTimestamp, 0, 0);
        persistContractResult(scheduledTimestamp, 53, 0);
        persistContractLog(scheduledTimestamp, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(scheduledTimestamp, 1);
        assertContractLogIndex(scheduledTimestamp, 1);
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

    private void persistScheduleExecution(long executedTimestamp) {
        domainBuilder
                .schedule()
                .customize(s -> s.executedTimestamp(executedTimestamp))
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

    private void persistContractResult(long consensusTimestamp, int nonce, Integer existingIndex) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(existingIndex)
                        .transactionNonce(nonce)
                        .gasUsed(100L))
                .persist();
    }

    private void persistHookDispatchContractResult(long consensusTimestamp, Integer existingIndex) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(EntityId.of(0L, 0L, RecordItem.HOOK_CONTRACT_NUM)
                                .getId())
                        .transactionIndex(existingIndex)
                        .gasUsed(100L))
                .persist();
    }

    private void persistContractLog(long consensusTimestamp, Integer existingIndex) {
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(existingIndex)
                        .synthetic(false))
                .persist();
    }

    private void persistSyntheticContractLog(long consensusTimestamp, Integer existingIndex) {
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(existingIndex)
                        .synthetic(true))
                .persist();
    }

    private void assertContractResultIndex(long consensusTimestamp, Integer expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }

    private void assertContractLogIndex(long consensusTimestamp, Integer expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }
}
