// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class ContractLogSyntheticFlagBackfillMigrationTest
        extends AbstractAsyncJavaMigrationTest<ContractLogSyntheticFlagBackfillMigration> {

    private static final long INTERVAL = Duration.ofHours(3).toNanos();
    private static final byte[] TRANSFER_SIGNATURE =
            Hex.decode("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    private static final byte[] APPROVE_SIGNATURE =
            Hex.decode("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
    private static final byte[] APPROVE_FOR_ALL_SIGNATURE =
            Hex.decode("17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31");

    @Getter
    private final ContractLogSyntheticFlagBackfillMigration migration;

    private final ContractLogRepository contractLogRepository;
    private final RecordFileRepository recordFileRepository;

    @Test
    void emptyDatabase() {
        runMigration();
        waitForCompletion();

        assertThat(recordFileRepository.findAll()).isEmpty();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void backfillsNftTransferLogsWithoutContractResult() {
        // given
        final var timestamp = persistBlock(0).getConsensusStart() + 100;
        final var nftLog = persistContractLog(timestamp, TRANSFER_SIGNATURE, false);
        final var siblingNftLog = persistContractLog(timestamp, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(timestamp, nftLog.getIndex(), true);
        assertSynthetic(timestamp, siblingNftLog.getIndex(), true);
    }

    @Test
    void backfillsFungibleTransferLogsWithoutContractResult() {
        // given
        final var block = persistBlock(0);
        final var fungibleLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(block.getConsensusStart() + 100)
                        .topic0(TRANSFER_SIGNATURE)
                        .topic3(null)
                        .synthetic(false))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(fungibleLog.getConsensusTimestamp(), true);
    }

    @Test
    void backfillsApproveAllowanceLogsWithoutContractResult() {
        // given
        final var block = persistBlock(0);
        final var approveLog = persistContractLog(block.getConsensusStart() + 100, APPROVE_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(approveLog.getConsensusTimestamp(), true);
    }

    @Test
    void backfillsApproveForAllAllowanceLogsWithoutContractResult() {
        // given
        final var block = persistBlock(0);
        final var approveForAllLog =
                persistContractLog(block.getConsensusStart() + 100, APPROVE_FOR_ALL_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(approveForAllLog.getConsensusTimestamp(), true);
    }

    @Test
    void backfillsLogsRegardlessOfSignature() {
        // given
        final var block = persistBlock(0);
        final var unrelatedLog = domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.consensusTimestamp(block.getConsensusStart() + 100).synthetic(false))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(unrelatedLog.getConsensusTimestamp(), true);
    }

    @Test
    void preservesRowsWithMatchingContractResult() {
        // given
        final var block = persistBlock(0);
        final var nftLog = persistContractLog(block.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);
        domainBuilder
                .contractResult()
                .customize(cr -> cr.contractId(nftLog.getContractId().getId())
                        .consensusTimestamp(nftLog.getConsensusTimestamp()))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(nftLog.getConsensusTimestamp(), false);
    }

    @Test
    void preservesAlreadyFlaggedRows() {
        // given
        final var block = persistBlock(0);
        final var syntheticTrue = persistContractLog(block.getConsensusStart() + 100, TRANSFER_SIGNATURE, true);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(syntheticTrue.getConsensusTimestamp(), true);
    }

    @Test
    void recomputesEvmIndexForBlockContainingBackfilledNftLog() {
        // given
        final var block = persistBlock(0);
        final var nftMintTimestamp = block.getConsensusStart() + 100;
        final var contractCallTimestamp = block.getConsensusStart() + 200;

        final var nftLog = persistContractLog(nftMintTimestamp, TRANSFER_SIGNATURE, false);
        final var contractCallResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(contractCallTimestamp)
                        .transactionIndex(0)
                        .transactionNonce(0))
                .persist();
        final var contractCallLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(contractCallTimestamp)
                        .transactionIndex(0)
                        .synthetic(false))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(nftLog.getConsensusTimestamp(), true);
        assertTransactionIndex(nftLog.getConsensusTimestamp(), 0);
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 1);
        assertTransactionIndex(contractCallLog.getConsensusTimestamp(), 1);
    }

    @Test
    void doesNotDoubleCountEvmIndexWhenSyntheticLogSharesTimestampWithContractResult() {
        // given
        final var block = persistBlock(0);
        final var sharedTimestamp = block.getConsensusStart() + 100;
        final var laterTimestamp = block.getConsensusStart() + 200;
        final var backfillTimestamp = block.getConsensusStart() + 300;

        final var sharedContractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(sharedTimestamp)
                        .transactionIndex(99)
                        .transactionNonce(0))
                .persist();
        persistContractLog(sharedTimestamp, TRANSFER_SIGNATURE, true);
        final var laterContractResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(laterTimestamp)
                        .transactionIndex(99)
                        .transactionNonce(0))
                .persist();
        final var needsBackfillLog = persistContractLog(backfillTimestamp, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(sharedContractResult.getConsensusTimestamp(), 0);
        assertContractResultIndex(laterContractResult.getConsensusTimestamp(), 1);
        assertSynthetic(needsBackfillLog.getConsensusTimestamp(), true);
        assertTransactionIndex(needsBackfillLog.getConsensusTimestamp(), 2);
    }

    @Test
    void doesNotRecomputeIndexForBlocksWithoutABackfilledRow() {
        // given
        final var block = persistBlock(0);
        final var contractCallResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(block.getConsensusStart() + 100)
                        .transactionIndex(99)
                        .transactionNonce(0))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(contractCallResult.getConsensusTimestamp(), 99);
    }

    @Test
    void recomputeIsNarrowedToRecordFileContainingBackfilledRow() {
        // given
        final var firstBlock = persistBlock(0);
        final var secondBlock = domainBuilder
                .recordFile()
                .customize(r -> r.index(1L)
                        .consensusStart(firstBlock.getConsensusEnd() + 1)
                        .consensusEnd(firstBlock.getConsensusEnd()
                                + Duration.ofSeconds(2).toNanos()))
                .persist();

        final var untouchedResult = domainBuilder
                .contractResult()
                .customize(cr -> cr.consensusTimestamp(firstBlock.getConsensusStart() + 100)
                        .transactionIndex(99)
                        .transactionNonce(0))
                .persist();
        final var needsBackfillLog =
                persistContractLog(secondBlock.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertContractResultIndex(untouchedResult.getConsensusTimestamp(), 99);
        assertSynthetic(needsBackfillLog.getConsensusTimestamp(), true);
        assertTransactionIndex(needsBackfillLog.getConsensusTimestamp(), 0);
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

        final var earlyLog = persistContractLog(earlyBlock.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);
        final var recentLog = persistContractLog(recentBlock.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(earlyLog.getConsensusTimestamp(), true);
        assertSynthetic(recentLog.getConsensusTimestamp(), true);
    }

    @Test
    void missedTransactions() {
        // given
        final var block1 = persistBlock(1);
        final var timestamp = block1.getConsensusStart() - INTERVAL * 2;
        final var block0 = domainBuilder
                .recordFile()
                .customize(r -> r.index(0L)
                        .consensusStart(timestamp)
                        .consensusEnd(timestamp + Duration.ofSeconds(2).toNanos()))
                .persist();

        final var earlyLog = persistContractLog(block0.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(earlyLog.getConsensusTimestamp(), true);
    }

    @Test
    void stopsAtLowerBoundFloorFromSyntheticColumnCreation() {
        // given
        final var installedOn = Instant.now();
        jdbcOperations.update(
                "update flyway_schema_history set installed_on = ? where version in ('1.121.0', '2.26.0')",
                Timestamp.from(installedOn));

        final var oldBase = domainBuilder.timestamp();
        final var oldBlock = domainBuilder
                .recordFile()
                .customize(r -> r.index(0L)
                        .consensusStart(oldBase)
                        .consensusEnd(oldBase + Duration.ofSeconds(2).toNanos())
                        .loadStart(installedOn.minus(Duration.ofDays(5)).toEpochMilli()))
                .persist();
        final var oldLog = persistContractLog(oldBlock.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        final var newBase = oldBlock.getConsensusEnd() + 5 * INTERVAL;
        final var newBlock = domainBuilder
                .recordFile()
                .customize(r -> r.index(1L)
                        .consensusStart(newBase)
                        .consensusEnd(newBase + Duration.ofSeconds(2).toNanos())
                        .loadStart(installedOn.plus(Duration.ofDays(5)).toEpochMilli()))
                .persist();
        final var newLog = persistContractLog(newBlock.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(newLog.getConsensusTimestamp(), true);
        assertSynthetic(oldLog.getConsensusTimestamp(), false);
    }

    // Regression test for the infinite loop fixed by #14010.
    @Test
    void singleTransactionInIntervalNotInfiniteLoop() {
        // given
        final long timestamp1 = domainBuilder.timestamp();
        domainBuilder
                .recordFile()
                .customize(r -> r.index(1L).consensusStart(timestamp1).consensusEnd(timestamp1))
                .persist();
        final long timestamp0 = timestamp1 - INTERVAL + 1;
        domainBuilder
                .recordFile()
                .customize(r -> r.index(0L).consensusStart(timestamp0).consensusEnd(timestamp0))
                .persist();

        // when
        runMigration();
        waitForCompletion();
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

    private ContractLog persistContractLog(long consensusTimestamp, byte[] topic0, boolean synthetic) {
        return domainBuilder
                .contractLog()
                .customize(cl ->
                        cl.consensusTimestamp(consensusTimestamp).topic0(topic0).synthetic(synthetic))
                .persist();
    }

    private void assertSynthetic(long consensusTimestamp, boolean expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select synthetic from contract_log where consensus_timestamp = ?",
                        Boolean.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }

    private void assertSynthetic(long consensusTimestamp, int index, boolean expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select synthetic from contract_log where consensus_timestamp = ? and index = ?",
                        Boolean.class,
                        consensusTimestamp,
                        index))
                .isEqualTo(expected);
    }

    private void assertTransactionIndex(long consensusTimestamp, int expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_log where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }

    private void assertContractResultIndex(long consensusTimestamp, int expected) {
        assertThat(jdbcOperations.queryForObject(
                        "select transaction_index from contract_result where consensus_timestamp = ?",
                        Integer.class,
                        consensusTimestamp))
                .isEqualTo(expected);
    }
}
