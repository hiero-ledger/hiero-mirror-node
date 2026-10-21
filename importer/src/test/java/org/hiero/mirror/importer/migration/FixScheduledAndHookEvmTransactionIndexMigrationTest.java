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
import org.springframework.boot.convert.DurationStyle;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixScheduledAndHookEvmTransactionIndexMigrationTest
        extends AbstractAsyncJavaMigrationTest<FixScheduledAndHookEvmTransactionIndexMigration> {

    private static final long BATCH_INTERVAL = DurationStyle.SIMPLE
            .parse(FixScheduledAndHookEvmTransactionIndexMigration.DEFAULT_BATCH_INTERVAL)
            .toNanos();
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
    void atomicBatchInnerCallAfterHookChainClaimsOwnIndex() {
        // given
        final var block = persistBlock(0);
        final var batchTimestamp = block.getConsensusStart() + 100;
        final var innerTransferTimestamp = block.getConsensusStart() + 200;
        final var hookCallTimestamp = block.getConsensusStart() + 300;
        final var hookDispatchedTimestamp = block.getConsensusStart() + 400;
        final var innerCallTimestamp = block.getConsensusStart() + 500;

        persistTransaction(batchTimestamp, TransactionType.ATOMIC_BATCH, 0, false, null);
        persistTransaction(innerTransferTimestamp, TransactionType.CRYPTOTRANSFER, 0, false, batchTimestamp);
        persistHookDispatchTransaction(hookCallTimestamp, 1, innerTransferTimestamp);
        persistTransaction(hookDispatchedTimestamp, TransactionType.CONTRACTCALL, 2, false, innerTransferTimestamp);
        // user-signed inner transaction after the hook chain, not an effect of the hook
        persistTransaction(innerCallTimestamp, TransactionType.CONTRACTCALL, 0, false, batchTimestamp);

        persistHookDispatchContractResult(hookCallTimestamp, 0);
        persistContractResult(hookDispatchedTimestamp, 2, 0);
        // simulates the buggy output: inherited the hook's index (0) instead of claiming its own (1)
        persistContractResult(innerCallTimestamp, 0, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(hookCallTimestamp, 0);
        assertContractResultIndex(hookDispatchedTimestamp, 0);
        assertContractResultIndex(innerCallTimestamp, 1);
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
    void scheduledCallEffectsInheritScheduledCallIndex() {
        // given - the effects are parented to the ScheduleSign, not to the scheduled call
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        final var scheduleSignTimestamp = block.getConsensusStart() + 200;
        final var scheduledCallTimestamp = scheduleSignTimestamp + 1;
        final var firstEffectTimestamp = scheduleSignTimestamp + 2;
        final var secondEffectTimestamp = scheduleSignTimestamp + 3;
        final var laterRootTimestamp = block.getConsensusStart() + 300;

        persistTransaction(rootTimestamp, TransactionType.ETHEREUMTRANSACTION, 0, false, null);
        persistTransaction(scheduleSignTimestamp, TransactionType.SCHEDULESIGN, 0, false, null);
        persistTransaction(scheduledCallTimestamp, TransactionType.CONTRACTCALL, 0, true, null);
        persistTransaction(firstEffectTimestamp, TransactionType.CRYPTOTRANSFER, 1, false, scheduleSignTimestamp);
        persistTransaction(secondEffectTimestamp, TransactionType.CONTRACTCALL, 2, false, scheduleSignTimestamp);
        persistTransaction(laterRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistScheduleExecution(scheduledCallTimestamp);

        persistContractResult(rootTimestamp, 0, 0);
        persistContractResult(scheduledCallTimestamp, 0, 1);
        // simulates the live importer's output: each effect claimed its own index, shifting the later root
        persistContractResult(firstEffectTimestamp, 1, 2);
        persistContractResult(secondEffectTimestamp, 2, 3);
        persistContractResult(laterRootTimestamp, 0, 4);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootTimestamp, 0);
        assertContractResultIndex(scheduledCallTimestamp, 1);
        assertContractResultIndex(firstEffectTimestamp, 1);
        assertContractResultIndex(secondEffectTimestamp, 1);
        assertContractResultIndex(laterRootTimestamp, 2);
    }

    @Test
    void effectsOfFailedCallInheritNullIndex() {
        // given - a call failing with no gas used still has precompile effects
        final var block = persistBlock(0);
        final var rootTimestamp = block.getConsensusStart() + 100;
        final var failedCallTimestamp = block.getConsensusStart() + 200;
        final var firstEffectTimestamp = failedCallTimestamp + 1;
        final var secondEffectTimestamp = failedCallTimestamp + 2;

        persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(failedCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(firstEffectTimestamp, TransactionType.CRYPTOTRANSFER, 1, false, failedCallTimestamp);
        persistTransaction(secondEffectTimestamp, TransactionType.TOKENMINT, 2, false, failedCallTimestamp);

        persistContractResult(rootTimestamp, 0, 0);
        persistContractResult(failedCallTimestamp, 0, null, 0L);
        // simulates the old migration's actual output: the unrelated root's index, and -1 when nothing precedes
        persistContractResult(firstEffectTimestamp, 1, 0);
        persistContractResult(secondEffectTimestamp, 2, -1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(rootTimestamp, 0);
        assertContractResultIndex(failedCallTimestamp, null);
        assertContractResultIndex(firstEffectTimestamp, null);
        assertContractResultIndex(secondEffectTimestamp, null);
    }

    @Test
    void dropsTargetsTable() {
        // given
        persistBlock(0);
        ownerJdbcTemplate.execute("""
                create table if not exists fix_scheduled_hook_evm_index_targets_temp(
                    consensus_start bigint not null,
                    consensus_end bigint not null primary key
                )
                """);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(tableExists("fix_scheduled_hook_evm_index_targets_temp")).isFalse();
        assertThat(tableExists("fix_scheduled_hook_evm_index_progress_temp")).isFalse();
    }

    @Test
    void effectsOfFailedCallsInSlicesSeparatedByGapAreFound() {
        // given - the gap between the blocks spans slices without record files
        final var olderBlock = persistBlockAt(domainBuilder.timestamp(), 0);
        final var newerBlock = persistBlockAt(olderBlock.getConsensusStart() + 3 * BATCH_INTERVAL, 1);
        final var olderEffect = persistFailedCallWithEffect(olderBlock.getConsensusStart() + 100);
        final var newerEffect = persistFailedCallWithEffect(newerBlock.getConsensusStart() + 100);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(olderEffect, null);
        assertContractResultIndex(newerEffect, null);
    }

    @Test
    void blockAcrossSliceBoundaryIsProcessedWhole() {
        // given - the newer slice's lower bound falls inside the older block, after its failed call
        final var olderBlock = persistBlockAt(domainBuilder.timestamp(), 0);
        final var effectTimestamp = persistFailedCallWithEffect(olderBlock.getConsensusStart() + 100);
        final var newerBlockEnd =
                olderBlock.getConsensusStart() + Duration.ofSeconds(1).toNanos() + BATCH_INTERVAL;
        persistBlockAt(newerBlockEnd - Duration.ofSeconds(2).toNanos(), 1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(effectTimestamp, null);
    }

    @Test
    void resumesFromCheckpoint() {
        // given - an earlier run was interrupted after processing the newest block
        final var olderBlock = persistBlockAt(domainBuilder.timestamp(), 0);
        final var newerBlock = persistBlockAt(olderBlock.getConsensusStart() + 3 * BATCH_INTERVAL, 1);
        final var newestBlock = persistBlockAt(newerBlock.getConsensusStart() + 3 * BATCH_INTERVAL, 2);
        final var olderEffect = persistFailedCallWithEffect(olderBlock.getConsensusStart() + 100);
        final var newerEffect = persistFailedCallWithEffect(newerBlock.getConsensusStart() + 100);
        final var newestEffect = persistFailedCallWithEffect(newestBlock.getConsensusStart() + 100);

        migration.performSynchronousSteps();
        migration
                .getJdbcOperations()
                .update(
                        "insert into fix_scheduled_hook_evm_index_progress_temp values (?)",
                        newestBlock.getConsensusStart() - 1);

        // when
        runMigration();
        waitForCompletion();

        // then - the checkpoint is honored, so the newest block isn't processed again
        assertContractResultIndex(olderEffect, null);
        assertContractResultIndex(newerEffect, null);
        assertContractResultIndex(newestEffect, -1);
        assertThat(tableExists("fix_scheduled_hook_evm_index_progress_temp")).isFalse();
    }

    @Test
    void effectsOfFailedCallAfterChildWithoutResultAreFound() {
        // given - the first child has no contract result, so the effect is found by its nonce offset instead
        final var block = persistBlock(0);
        final var failedCallTimestamp = block.getConsensusStart() + 100;
        final var accountCreateTimestamp = failedCallTimestamp + 1;
        final var effectTimestamp = failedCallTimestamp + 2;

        persistTransaction(failedCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(accountCreateTimestamp, TransactionType.CRYPTOCREATEACCOUNT, 1, false, failedCallTimestamp);
        persistTransaction(effectTimestamp, TransactionType.CRYPTOTRANSFER, 2, false, failedCallTimestamp);

        // a call whose record had no ContractFunctionResult is stored without gas used
        persistContractResult(failedCallTimestamp, 0, null, null);
        persistContractResult(effectTimestamp, 2, -1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(failedCallTimestamp, null);
        assertContractResultIndex(effectTimestamp, null);
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
    void scheduledTransferWithOnlySyntheticLogIsAlreadyCorrect() {
        // given - the block's only EVM activity is a scheduled transfer with a synthetic log and no contract result
        final var block = persistBlock(0);
        final var scheduledTransferTimestamp = block.getConsensusStart() + 100;

        persistTransaction(scheduledTransferTimestamp, TransactionType.CRYPTOTRANSFER, 54, true, null);
        persistScheduleExecution(scheduledTransferTimestamp);
        persistSyntheticContractLog(scheduledTransferTimestamp, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractLogIndex(scheduledTransferTimestamp, 0);
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
    void staleLogIsFixedWhenContractResultIsAlreadyCorrect() {
        // given
        final var block = persistBlock(0);
        final var unrelatedRootTimestamp = block.getConsensusStart() + 100;
        final var scheduledTimestamp = block.getConsensusStart() + 200;

        persistTransaction(unrelatedRootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(scheduledTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistScheduleExecution(scheduledTimestamp);

        persistContractResult(unrelatedRootTimestamp, 0, 0);
        persistContractResult(scheduledTimestamp, 53, 1);
        persistContractLog(scheduledTimestamp, 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(scheduledTimestamp, 1);
        assertContractLogIndex(scheduledTimestamp, 1);
    }

    @Test
    void scheduledZeroGasContractCallGetsNullIndex() {
        // given
        final var block = persistBlock(0);
        final var scheduledTimestamp = block.getConsensusStart() + 100;

        persistTransaction(scheduledTimestamp, TransactionType.CONTRACTCALL, 53, true, null);
        persistScheduleExecution(scheduledTimestamp);
        persistContractResult(scheduledTimestamp, 53, -1, 0L);
        persistContractLog(scheduledTimestamp, -1);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(scheduledTimestamp, null);
        assertContractLogIndex(scheduledTimestamp, null);
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

    @Test
    void affectedBlocksAcrossIterationsAreFixedIndependently() {
        // given - more affected blocks than one iteration reads, with an unaffected block and a row outside every
        // block in between
        final var blockCount = 25;
        final var unaffectedBlockIndex = 12;
        final var scheduledTimestamps = new long[blockCount];
        long unaffectedTimestamp = 0;
        long outsideBlockTimestamp = 0;

        for (int i = 0; i < blockCount; i++) {
            final var block = persistBlock(i);
            final var rootTimestamp = block.getConsensusStart() + 100;
            persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);

            if (i == unaffectedBlockIndex) {
                unaffectedTimestamp = rootTimestamp;
                persistContractResult(rootTimestamp, 0, 99);
                outsideBlockTimestamp = block.getConsensusEnd() + 1000;
                persistTransaction(outsideBlockTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
                persistContractResult(outsideBlockTimestamp, 0, 77);
                continue;
            }

            scheduledTimestamps[i] = block.getConsensusStart() + 200;
            persistTransaction(scheduledTimestamps[i], TransactionType.CONTRACTCALL, 53, true, null);
            persistScheduleExecution(scheduledTimestamps[i]);
            persistContractResult(rootTimestamp, 0, 0);
            persistContractResult(scheduledTimestamps[i], 53, 0);
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        for (int i = 0; i < blockCount; i++) {
            if (i != unaffectedBlockIndex) {
                assertContractResultIndex(scheduledTimestamps[i], 1);
            }
        }
        assertContractResultIndex(unaffectedTimestamp, 99);
        assertContractResultIndex(outsideBlockTimestamp, 77);
    }

    @Test
    void moreAffectedBlocksInSliceThanOneReadTakesAreFixed() {
        // given - all blocks fall in one slice
        final var blockCount = 45;
        final var firstBlockStart = domainBuilder.timestamp();
        final var scheduledTimestamps = new long[blockCount];
        for (int i = 0; i < blockCount; i++) {
            final var block =
                    persistBlockAt(firstBlockStart + i * Duration.ofMinutes(1).toNanos(), i);
            final var rootTimestamp = block.getConsensusStart() + 100;
            scheduledTimestamps[i] = block.getConsensusStart() + 200;
            persistTransaction(rootTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
            persistTransaction(scheduledTimestamps[i], TransactionType.CONTRACTCALL, 53, true, null);
            persistScheduleExecution(scheduledTimestamps[i]);
            persistContractResult(rootTimestamp, 0, 0);
            persistContractResult(scheduledTimestamps[i], 53, 0);
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        for (final var scheduledTimestamp : scheduledTimestamps) {
            assertContractResultIndex(scheduledTimestamp, 1);
        }
    }

    private RecordFile persistBlockAt(long consensusStart, long index) {
        return domainBuilder
                .recordFile()
                .customize(r -> r.index(index)
                        .consensusStart(consensusStart)
                        .consensusEnd(consensusStart + Duration.ofSeconds(2).toNanos()))
                .persist();
    }

    // A call failing with no gas used and one precompile effect left at -1 by the old migration; returns the effect
    private long persistFailedCallWithEffect(long failedCallTimestamp) {
        final var effectTimestamp = failedCallTimestamp + 1;
        persistTransaction(failedCallTimestamp, TransactionType.CONTRACTCALL, 0, false, null);
        persistTransaction(effectTimestamp, TransactionType.CRYPTOTRANSFER, 1, false, failedCallTimestamp);
        persistContractResult(failedCallTimestamp, 0, null, 0L);
        persistContractResult(effectTimestamp, 1, -1);
        return effectTimestamp;
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
                        .entityId(hookContractId()))
                .persist();
    }

    private void persistContractResult(long consensusTimestamp, int nonce, Integer existingIndex) {
        persistContractResult(consensusTimestamp, nonce, existingIndex, 100L);
    }

    private void persistContractResult(long consensusTimestamp, int nonce, Integer existingIndex, Long gasUsed) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .transactionIndex(existingIndex)
                        .transactionNonce(nonce)
                        .gasUsed(gasUsed))
                .persist();
    }

    private void persistHookDispatchContractResult(long consensusTimestamp, Integer existingIndex) {
        domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(hookContractId().getId())
                        .transactionIndex(existingIndex)
                        .gasUsed(100L))
                .persist();
    }

    private EntityId hookContractId() {
        return EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), RecordItem.HOOK_CONTRACT_NUM);
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
