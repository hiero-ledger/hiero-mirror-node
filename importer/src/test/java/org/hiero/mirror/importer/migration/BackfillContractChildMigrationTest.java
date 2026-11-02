// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.RecordItem.HOOK_CONTRACT_NUM;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.ContractResultRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class BackfillContractChildMigrationTest extends AbstractAsyncJavaMigrationTest<BackfillContractChildMigration> {

    private static final long BATCH_INTERVAL = Duration.ofDays(1).toNanos();

    @Getter
    private final BackfillContractChildMigration migration;

    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityProperties entityProperties;

    @Test
    void backfillsByParentFromFirstHookContractResult() {
        // given
        final var topLevel = transaction(0, null, false);
        final var topLevelResult = contractResult(topLevel);
        final var topLevelLog = contractLog(topLevel.getConsensusTimestamp(), 0);
        final var topLevelSyntheticLog = syntheticContractLog(topLevel.getConsensusTimestamp(), 1);
        final var evmChild = transaction(1, topLevel.getConsensusTimestamp(), false);
        final var evmChildResult = contractResult(evmChild);
        final var evmChildLogs = new ContractLog[] {
            contractLog(evmChild.getConsensusTimestamp(), 0),
            contractLog(evmChild.getConsensusTimestamp(), 1),
            syntheticContractLog(evmChild.getConsensusTimestamp(), 2)
        };
        final var alreadyChild = transaction(2, topLevel.getConsensusTimestamp(), false);
        final var alreadyChildResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.child(true)
                        .consensusTimestamp(alreadyChild.getConsensusTimestamp())
                        .transactionNonce(alreadyChild.getNonce()))
                .persist();

        // the top level hook call is the root of its own EVM execution, calls it dispatches are its children
        final var hookTrigger = transaction(0, null, false);
        final var hookExecution = transaction(1, hookTrigger.getConsensusTimestamp(), false);
        final var hookResult = hookContractResult(hookExecution);
        final var hookLogs = new ContractLog[] {
            contractLog(hookExecution.getConsensusTimestamp(), 0),
            syntheticContractLog(hookExecution.getConsensusTimestamp(), 1)
        };
        final var hookDispatched = transaction(2, hookTrigger.getConsensusTimestamp(), false);
        final var hookDispatchedResult = contractResult(hookDispatched);
        final var hookDispatchedLog = contractLog(hookDispatched.getConsensusTimestamp(), 0);

        // a sibling without a contract result breaks the hook chain
        final var brokenHookTrigger = transaction(0, null, false);
        final var brokenHookExecution = transaction(1, brokenHookTrigger.getConsensusTimestamp(), false);
        hookContractResult(brokenHookExecution);
        transaction(2, brokenHookTrigger.getConsensusTimestamp(), false);
        final var afterBrokenChain = transaction(3, brokenHookTrigger.getConsensusTimestamp(), false);
        final var afterBrokenChainResult = contractResult(afterBrokenChain);

        // effects of a scheduled call are parented to the ScheduleSign that triggered it
        final var scheduleSign = transaction(0, null, false);
        final var scheduledCall = transaction(0, null, true);
        final var scheduledCallResult = contractResult(scheduledCall);
        final var scheduledCallEffect = transaction(1, scheduleSign.getConsensusTimestamp(), false);
        final var scheduledCallEffectResult = contractResult(scheduledCallEffect);

        final var batch = transaction(0, null, false);
        final var batchInner = transaction(0, batch.getConsensusTimestamp(), false);
        final var batchInnerResult = contractResult(batchInner);
        final var batchInnerLog = contractLog(batchInner.getConsensusTimestamp(), 0);

        // a user-signed atomic batch inner call after a hook chain isn't an effect of the hook
        final var hookBatch = transaction(0, null, false);
        final var hookBatchTransfer = transaction(0, hookBatch.getConsensusTimestamp(), false);
        final var hookBatchExecution = transaction(1, hookBatchTransfer.getConsensusTimestamp(), false);
        hookContractResult(hookBatchExecution);
        final var hookBatchDispatched = transaction(2, hookBatchTransfer.getConsensusTimestamp(), false);
        final var hookBatchDispatchedResult = contractResult(hookBatchDispatched);
        final var hookBatchInnerCall = transaction(0, hookBatch.getConsensusTimestamp(), false);
        final var hookBatchInnerCallResult = contractResult(hookBatchInnerCall);
        final var hookBatchInnerCallLog = contractLog(hookBatchInnerCall.getConsensusTimestamp(), 0);

        final var scheduledExecution = transaction(0, null, true);
        final var scheduledResult = contractResult(scheduledExecution);
        final var recurringScheduledExecution = transaction(1, null, true);
        final var recurringScheduledResult = contractResult(recurringScheduledExecution);
        final var orphanResult = contractResult(domainBuilder.timestamp(), 0);
        final var orphanLog = contractLog(orphanResult.getConsensusTimestamp(), 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(topLevelResult, false);
        assertChild(topLevelLog, false);
        assertChild(topLevelSyntheticLog, false);
        assertChild(evmChildResult, true);
        for (final var log : evmChildLogs) {
            assertChild(log, true);
        }
        assertChild(alreadyChildResult, true);
        assertChild(hookResult, false);
        for (final var log : hookLogs) {
            assertChild(log, false);
        }
        assertChild(hookDispatchedResult, true);
        assertChild(hookDispatchedLog, true);
        assertChild(afterBrokenChainResult, false);
        assertChild(scheduledCallResult, false);
        assertChild(scheduledCallEffectResult, true);
        assertChild(batchInnerResult, false);
        assertChild(batchInnerLog, false);
        assertChild(hookBatchDispatchedResult, true);
        assertChild(hookBatchInnerCallResult, false);
        assertChild(hookBatchInnerCallLog, false);
        assertChild(scheduledResult, false);
        assertChild(recurringScheduledResult, false);
        assertChild(orphanResult, false);
        assertChild(orphanLog, false);
    }

    @Test
    void backfillsByNonceBeforeFirstHookOrScheduleContractResult() {
        // given
        final var topLevel = transaction(0, null, false);
        final var topLevelResult = contractResult(topLevel);
        final var topLevelLog = contractLog(topLevel.getConsensusTimestamp(), 0);
        final var evmChild = transaction(1, topLevel.getConsensusTimestamp(), false);
        final var evmChildResult = contractResult(evmChild);
        final var evmChildLog = contractLog(evmChild.getConsensusTimestamp(), 0);
        final var scheduleSign = transaction(0, null, false);
        final var scheduledCall = transaction(0, null, true);
        final var scheduledCallResult = contractResult(scheduledCall);
        final var scheduledCallEffect = transaction(1, scheduleSign.getConsensusTimestamp(), false);
        final var scheduledCallEffectResult = contractResult(scheduledCallEffect);
        final var batch = transaction(0, null, false);
        final var batchInner = transaction(0, batch.getConsensusTimestamp(), false);
        final var batchInnerResult = contractResult(batchInner);
        final var batchInnerLog = contractLog(batchInner.getConsensusTimestamp(), 0);

        // a later window with a hook call is walked by parent, earlier windows only use the nonce
        final long hookTriggerTimestamp = domainBuilder.timestamp() + BATCH_INTERVAL * 3;
        transaction(hookTriggerTimestamp, 0, null);
        final var hookExecution = transaction(hookTriggerTimestamp + 1, 1, hookTriggerTimestamp);
        final var hookResult = hookContractResult(hookExecution);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(topLevelResult, false);
        assertChild(topLevelLog, false);
        assertChild(evmChildResult, true);
        assertChild(evmChildLog, true);
        assertChild(scheduledCallResult, false);
        assertChild(scheduledCallEffectResult, true);
        assertChild(batchInnerResult, false);
        assertChild(batchInnerLog, false);
        assertChild(hookResult, false);
    }

    @Test
    void backfillsScheduledCallOfContractByParent() {
        // given: a contract schedules a call through the schedule service system contract, which executes later with
        // the nonce of the ScheduleCreate child record and no parent
        final var contractCall = transaction(0, null, false);
        contractResult(contractCall);
        final var scheduleCreate = transaction(53, contractCall.getConsensusTimestamp(), false);
        final var scheduleCreateResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.child(false)
                        .consensusTimestamp(scheduleCreate.getConsensusTimestamp())
                        .contractId(domainBuilder.entityNum(363).getId())
                        .transactionNonce(scheduleCreate.getNonce()))
                .persist();
        final var scheduledExecution = transaction(53, null, true);
        final var scheduledExecutionResult = contractResult(scheduledExecution);
        final var scheduledExecutionChild = transaction(106, scheduledExecution.getConsensusTimestamp(), false);
        final var scheduledExecutionChildResult = contractResult(scheduledExecutionChild);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(scheduleCreateResult, true);
        assertChild(scheduledExecutionResult, false);
        assertChild(scheduledExecutionChildResult, true);
    }

    @Test
    void batchBoundariesAreInclusive() {
        // given: children at the max and min timestamps and at both sides of the first batch boundary, where the child
        // right after the boundary has its parent in the previous batch
        final long anchor = domainBuilder.timestamp() + BATCH_INTERVAL * 10;
        final long firstBatchStart = anchor - BATCH_INTERVAL + 1;
        final long secondBatchEnd = anchor - BATCH_INTERVAL;
        final var maxChild = childOfContractCall(anchor);
        final var secondBatchEndChild = childOfContractCall(secondBatchEnd);
        final var firstBatchStartChild = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(firstBatchStart).nonce(2).parentConsensusTimestamp(secondBatchEnd))
                .persist();
        final var minChild = childOfContractCall(anchor - BATCH_INTERVAL * 5 - 7);
        final var results = new ContractResult[] {
            contractResult(maxChild),
            contractResult(secondBatchEndChild),
            contractResult(firstBatchStartChild),
            contractResult(minChild)
        };

        // when
        runMigration();
        waitForCompletion();

        // then
        for (final var result : results) {
            assertChild(result, true);
        }
    }

    @Test
    void childBackfilledWhenParentHasContractResult() {
        // given
        final var child = childOfContractCall(domainBuilder.timestamp());
        final var contractResult = contractResult(child);
        final var contractLog = contractLog(child.getConsensusTimestamp(), 0);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(contractResult, true);
        assertChild(contractLog, true);
    }

    @Test
    void childNotSetWhenTransactionHasNoParent() {
        // given
        final var transaction = transaction(0, null, false);
        final var contractResult = contractResult(transaction);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(contractResult, false);
    }

    @Test
    void dropsProgressTable() {
        // given
        final var child = childOfContractCall(domainBuilder.timestamp());
        contractResult(child);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(tableExists("backfill_contract_child_progress_temp")).isFalse();
    }

    @Test
    void emptyDatabase() {
        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(contractResultRepository.findAll()).isEmpty();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void processesMultipleBatchIntervals() {
        // given: two children more than one batch interval apart
        final var recentChild = childOfContractCall(domainBuilder.timestamp());
        final var earlyChild = childOfContractCall(recentChild.getConsensusTimestamp() - BATCH_INTERVAL * 3);
        final var recentContractResult = contractResult(recentChild);
        final var earlyContractResult = contractResult(earlyChild);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(recentContractResult, true);
        assertChild(earlyContractResult, true);
    }

    @Test
    void resumesFromSavedProgress() {
        // given: a previous run checkpointed at savedUpperBound
        final var below = childOfContractCall(domainBuilder.timestamp());
        final long savedUpperBound = below.getConsensusTimestamp() + 10;
        final var above = childOfContractCall(savedUpperBound + 2);
        final var belowResult = contractResult(below);
        final var aboveResult = contractResult(above);
        ownerJdbcTemplate.execute("create table backfill_contract_child_progress_temp(upper_bound bigint not null)");
        ownerJdbcTemplate.update("insert into backfill_contract_child_progress_temp values (?)", savedUpperBound);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(belowResult, true);
        assertChild(aboveResult, false);
        assertThat(tableExists("backfill_contract_child_progress_temp")).isFalse();
    }

    @Test
    void skipsWhenContractResultsNotPersisted() {
        // given
        final var contractResult = contractResult(childOfContractCall(domainBuilder.timestamp()));
        entityProperties.getPersist().setContractResults(false);

        try {
            // when
            runMigration();
            waitForCompletion();
        } finally {
            entityProperties.getPersist().setContractResults(true);
        }

        // then
        assertChild(contractResult, false);
        assertThat(tableExists("backfill_contract_child_progress_temp")).isFalse();
    }

    @Test
    void skipsWhenNoContractResults() {
        // given
        domainBuilder.transaction().persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(contractResultRepository.findAll()).isEmpty();
        assertThat(tableExists("backfill_contract_child_progress_temp")).isFalse();
    }

    private void assertChild(final ContractLog contractLog, final boolean expected) {
        assertThat(contractLogRepository.findById(
                        new ContractLog.Id(contractLog.getConsensusTimestamp(), contractLog.getIndex())))
                .get()
                .extracting(ContractLog::isChild)
                .isEqualTo(expected);
    }

    private void assertChild(final ContractResult contractResult, final boolean expected) {
        assertThat(contractResultRepository.findById(contractResult.getConsensusTimestamp()))
                .get()
                .extracting(ContractResult::isChild)
                .isEqualTo(expected);
    }

    private Transaction childOfContractCall(final long consensusTimestamp) {
        final long parentConsensusTimestamp = consensusTimestamp - 1;
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(parentConsensusTimestamp).parentConsensusTimestamp(null))
                .persist();
        contractResult(parentConsensusTimestamp, 0);
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .nonce(1)
                        .parentConsensusTimestamp(parentConsensusTimestamp))
                .persist();
    }

    private ContractLog contractLog(final long consensusTimestamp, final int index) {
        return domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.child(false).consensusTimestamp(consensusTimestamp).index(index))
                .persist();
    }

    private ContractResult contractResult(final long consensusTimestamp, final int nonce) {
        return domainBuilder
                .contractResult()
                .customize(cr ->
                        cr.child(false).consensusTimestamp(consensusTimestamp).transactionNonce(nonce))
                .persist();
    }

    private ContractResult contractResult(final Transaction transaction) {
        return contractResult(transaction.getConsensusTimestamp(), transaction.getNonce());
    }

    private ContractResult hookContractResult(final Transaction transaction) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.child(false)
                        .consensusTimestamp(transaction.getConsensusTimestamp())
                        .contractId(domainBuilder.entityNum(HOOK_CONTRACT_NUM).getId())
                        .transactionNonce(transaction.getNonce()))
                .persist();
    }

    private ContractLog syntheticContractLog(final long consensusTimestamp, final int index) {
        return domainBuilder
                .contractLog()
                .customize(cl -> cl.child(false)
                        .consensusTimestamp(consensusTimestamp)
                        .index(index)
                        .synthetic(true))
                .persist();
    }

    private Transaction transaction(final int nonce, final Long parentConsensusTimestamp, final boolean scheduled) {
        return domainBuilder
                .transaction()
                .customize(t -> t.nonce(nonce)
                        .parentConsensusTimestamp(parentConsensusTimestamp)
                        .scheduled(scheduled))
                .persist();
    }

    private Transaction transaction(
            final long consensusTimestamp, final int nonce, final Long parentConsensusTimestamp) {
        return domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .nonce(nonce)
                        .parentConsensusTimestamp(parentConsensusTimestamp))
                .persist();
    }
}
