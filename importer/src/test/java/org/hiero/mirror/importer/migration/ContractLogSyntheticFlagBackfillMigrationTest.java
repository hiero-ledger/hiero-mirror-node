// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
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

    private static final byte[] TRANSFER_SIGNATURE = Bytes.fromHexString(
                    "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            .toArray();
    private static final byte[] APPROVE_SIGNATURE = Bytes.fromHexString(
                    "8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925")
            .toArray();
    private static final byte[] APPROVE_FOR_ALL_SIGNATURE = Bytes.fromHexString(
                    "17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31")
            .toArray();

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
        final var block = persistBlock(0);
        final var nftLog = persistContractLog(block.getConsensusStart() + 100, TRANSFER_SIGNATURE, false);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertSynthetic(nftLog.getConsensusTimestamp(), true);
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
    void preservesLogsWithUnrelatedSignature() {
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
        assertSynthetic(unrelatedLog.getConsensusTimestamp(), false);
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
