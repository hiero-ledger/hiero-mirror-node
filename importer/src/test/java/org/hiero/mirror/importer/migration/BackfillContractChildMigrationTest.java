// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final long BATCH_INTERVAL = Duration.ofHours(3).toNanos();

    @Getter
    private final BackfillContractChildMigration migration;

    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final EntityProperties entityProperties;

    @Test
    void backfillsOnlyRowsOfChildTransactions() {
        // given
        final var topLevel = transaction(0, null, false);
        final var evmChild = transaction(1, topLevel.getConsensusTimestamp(), false);
        final var batch = transaction(0, null, false);
        final var batchInner = transaction(0, batch.getConsensusTimestamp(), false);
        final var scheduledExecution = transaction(0, null, true);
        final var recurringScheduledExecution = transaction(1, null, true);

        final var topLevelResult = contractResult(topLevel.getConsensusTimestamp());
        final var topLevelLog = contractLog(topLevel.getConsensusTimestamp(), 0);
        final var topLevelSyntheticLog = syntheticContractLog(topLevel.getConsensusTimestamp(), 1);
        final var evmChildResult = contractResult(evmChild.getConsensusTimestamp());
        final var evmChildLogs = new ContractLog[] {
            contractLog(evmChild.getConsensusTimestamp(), 0),
            contractLog(evmChild.getConsensusTimestamp(), 1),
            syntheticContractLog(evmChild.getConsensusTimestamp(), 2)
        };
        final var batchInnerResult = contractResult(batchInner.getConsensusTimestamp());
        final var batchInnerLog = contractLog(batchInner.getConsensusTimestamp(), 0);
        final var scheduledResult = contractResult(scheduledExecution.getConsensusTimestamp());
        final var recurringScheduledResult = contractResult(recurringScheduledExecution.getConsensusTimestamp());
        final var recurringScheduledLog = contractLog(recurringScheduledExecution.getConsensusTimestamp(), 0);
        final var orphanResult = contractResult(domainBuilder.timestamp());
        final var orphanLog = contractLog(orphanResult.getConsensusTimestamp(), 0);
        final var alreadyChild = transaction(1, topLevel.getConsensusTimestamp(), false);
        final var alreadyChildResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.child(true).consensusTimestamp(alreadyChild.getConsensusTimestamp()))
                .persist();

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
        assertChild(batchInnerResult, true);
        assertChild(batchInnerLog, true);
        assertChild(scheduledResult, false);
        assertChild(recurringScheduledResult, false);
        assertChild(recurringScheduledLog, false);
        assertChild(orphanResult, false);
        assertChild(orphanLog, false);
        assertChild(alreadyChildResult, true);
    }

    @Test
    void batchBoundariesAreInclusive() {
        // given: child rows at max, min and both sides of the first batch boundary
        final var anchor = domainBuilder.timestamp() + BATCH_INTERVAL * 10;
        final var firstBatchStart = anchor - BATCH_INTERVAL + 1;
        final var secondBatchEnd = anchor - BATCH_INTERVAL;
        final var min = anchor - BATCH_INTERVAL * 5 - 7;

        final var results = new ContractResult[4];
        final long[] timestamps = {anchor, firstBatchStart, secondBatchEnd, min};
        for (int i = 0; i < timestamps.length; i++) {
            final long timestamp = timestamps[i];
            domainBuilder
                    .transaction()
                    .customize(t -> t.consensusTimestamp(timestamp).parentConsensusTimestamp(timestamp - 1))
                    .persist();
            results[i] = contractResult(timestamp);
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        for (final var result : results) {
            assertChild(result, true);
        }
    }

    @Test
    void childBackfilledWhenTransactionHasParent() {
        // given
        final var transaction = domainBuilder.transaction().persist();
        final var contractResult = contractResult(transaction.getConsensusTimestamp());
        final var contractLog = contractLog(transaction.getConsensusTimestamp(), 0);

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
        final var transaction = domainBuilder
                .transaction()
                .customize(t -> t.parentConsensusTimestamp(null))
                .persist();
        final var contractResult = contractResult(transaction.getConsensusTimestamp());

        // when
        runMigration();
        waitForCompletion();

        // then
        assertChild(contractResult, false);
    }

    @Test
    void dropsProgressTable() {
        // given
        domainBuilder.transaction().persist();

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
        // given: two child transactions more than one batch interval apart
        final var recentTransaction = domainBuilder.transaction().persist();
        final var earlyTransaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(recentTransaction.getConsensusTimestamp() - BATCH_INTERVAL * 3)
                        .parentConsensusTimestamp(recentTransaction.getConsensusTimestamp() - BATCH_INTERVAL * 3 - 1))
                .persist();
        final var recentContractResult = contractResult(recentTransaction.getConsensusTimestamp());
        final var earlyContractResult = contractResult(earlyTransaction.getConsensusTimestamp());

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
        final var below = domainBuilder.transaction().persist();
        final var savedUpperBound = below.getConsensusTimestamp() + 10;
        final var above = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(savedUpperBound + 1))
                .persist();
        final var belowResult = contractResult(below.getConsensusTimestamp());
        final var aboveResult = contractResult(above.getConsensusTimestamp());
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
        final var transaction = domainBuilder.transaction().persist();
        final var contractResult = contractResult(transaction.getConsensusTimestamp());
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

    private ContractLog contractLog(final long consensusTimestamp, final int index) {
        return domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.child(false).consensusTimestamp(consensusTimestamp).index(index))
                .persist();
    }

    private ContractResult contractResult(final long consensusTimestamp) {
        return domainBuilder
                .contractResult()
                .customize(cr -> cr.child(false).consensusTimestamp(consensusTimestamp))
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
}
