// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

@RequiredArgsConstructor
@Tag("migration")
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
class RecalculateRecordFileBlockStatsMigrationTest
        extends AbstractAsyncJavaMigrationTest<RecalculateRecordFileBlockStatsMigration> {

    @Getter
    private final RecalculateRecordFileBlockStatsMigration migration;

    private final JdbcOperations jdbcOperations;

    @Test
    void migrationOnEmptyDb() {
        runMigration();
        waitForCompletion();

        assertThat(tableExists("processed_record_file_temp")).isFalse();
    }

    @Test
    void recalculatesCountGasAndLogsBloom() {
        long start = domainBuilder.timestamp();
        long end = start + 1000L;
        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloom, (byte) 0x0a);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(1000L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(start + 10).validStartNs(start))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(start + 10).bloom(bloom).gasUsed(41000L))
                .persist();

        runMigration();
        waitForCompletion();

        var count =
                jdbcOperations.queryForObject("select count from record_file where consensus_end = ?", Long.class, end);
        var gasUsed = jdbcOperations.queryForObject(
                "select gas_used from record_file where consensus_end = ?", Long.class, end);
        var logsBloom = jdbcOperations.queryForObject(
                "select logs_bloom from record_file where consensus_end = ?", byte[].class, end);

        assertThat(count).isEqualTo(1L);
        assertThat(gasUsed).isEqualTo(41000L);
        assertThat(logsBloom).isEqualTo(bloom);
        assertThat(tableExists("processed_record_file_temp")).isFalse();
    }

    @Test
    void recalculatesPredecessorWhenFollowingBlockCountIsWrong() {
        long base = domainBuilder.timestamp();
        long end1 = base + 100L;
        long start2 = end1 + 1L;
        long end2 = start2 + 200L;

        var bloom2 = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloom2, (byte) 0x02);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(base)
                        .consensusEnd(end1)
                        .index(1L)
                        .count(1L)
                        .gasUsed(999L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start2)
                        .consensusEnd(end2)
                        .index(2L)
                        .count(0L)
                        .gasUsed(-1L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(base + 1).validStartNs(base))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(start2 + 1).validStartNs(start2))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(start2 + 1).bloom(bloom2).gasUsed(7L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end1))
                .isEqualTo(1L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end1))
                .isEqualTo(0L);

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end2))
                .isEqualTo(1L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end2))
                .isEqualTo(7L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end2))
                .isEqualTo(bloom2);
    }

    @Test
    void aggregatesMultipleContractResultsInOneRecordFile() {
        long start = domainBuilder.timestamp();
        long end = start + 10_000L;
        long ts1 = start + 100L;
        long ts2 = start + 200L;

        var bloom1 = new byte[LogsBloomFilter.BYTE_SIZE];
        bloom1[0] = (byte) 0x01;
        var bloom2 = new byte[LogsBloomFilter.BYTE_SIZE];
        bloom2[1] = (byte) 0x02;

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.or(bloom1);
        expectedBloom.or(bloom2);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(0L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ts1).validStartNs(start).nonce(0))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ts2).validStartNs(start).nonce(0))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(ts1).bloom(bloom1).gasUsed(10L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(ts2).bloom(bloom2).gasUsed(25L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(2L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(35L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end))
                .isEqualTo(expectedBloom.toArrayUnsafe());
    }

    @Test
    void predecessorRecalculationMovesGasAndBloomFromCurrentRecordFileToPredecessor() {
        long base = domainBuilder.timestamp();
        long end1 = base + 100L;
        long start2 = end1 + 1L;
        long end2 = start2 + 200L;

        var bloomOnFile1 = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloomOnFile1, (byte) 0x0c);

        var bloomOnFile2 = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloomOnFile2, (byte) 0x55);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(base)
                        .consensusEnd(end1)
                        .index(1L)
                        .count(1L)
                        .gasUsed(123L)
                        .logsBloom(bloomOnFile1))
                .persist();

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start2)
                        .consensusEnd(end2)
                        .index(2L)
                        .count(1L)
                        .gasUsed(234L)
                        .logsBloom(bloomOnFile2))
                .persist();

        domainBuilder
                .transaction()
                .customize(
                        t -> t.consensusTimestamp(base + 1).validStartNs(base).nonce(0))
                .persist();
        domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(base + 2).validStartNs(base + 1).nonce(0))
                .persist();

        domainBuilder
                .contractResult()
                .customize(
                        c -> c.consensusTimestamp(base + 1).bloom(bloomOnFile1).gasUsed(123L))
                .persist();

        domainBuilder
                .contractResult()
                .customize(
                        c -> c.consensusTimestamp(base + 2).bloom(bloomOnFile2).gasUsed(234L))
                .persist();

        runMigration();
        waitForCompletion();

        var combinedBloom = new LogsBloomFilter();
        combinedBloom.or(bloomOnFile1);
        combinedBloom.or(bloomOnFile2);

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end1))
                .isEqualTo(2L);
        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end2))
                .isEqualTo(0L);

        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end2))
                .isEqualTo(0L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end1))
                .isEqualTo(123L + 234L);

        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end1))
                .isEqualTo(combinedBloom.toArrayUnsafe());
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end2))
                .isEqualTo(new LogsBloomFilter().toArrayUnsafe());
    }

    @Test
    void includesContractResultForScheduledTransactionWithNonceGreaterThanZero() {
        long start = domainBuilder.timestamp();
        long end = start + 5_000L;
        long tsTop = start + 50L;
        long tsScheduled = start + 150L;

        var bloomTop = new byte[LogsBloomFilter.BYTE_SIZE];
        bloomTop[2] = (byte) 0x10;
        var bloomScheduled = new byte[LogsBloomFilter.BYTE_SIZE];
        bloomScheduled[3] = (byte) 0x20;

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.or(bloomTop);
        expectedBloom.or(bloomScheduled);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(0L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(tsTop)
                        .validStartNs(start)
                        .nonce(0)
                        .scheduled(false)
                        .parentConsensusTimestamp(tsTop - 1))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(tsScheduled)
                        .validStartNs(start)
                        .nonce(1)
                        .scheduled(true)
                        .parentConsensusTimestamp(tsTop))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tsTop).bloom(bloomTop).gasUsed(11L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c ->
                        c.consensusTimestamp(tsScheduled).bloom(bloomScheduled).gasUsed(19L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(30L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end))
                .isEqualTo(expectedBloom.toArrayUnsafe());
    }

    @Test
    void excludesContractResultForNonTopLevelChildTransaction() {
        long start = domainBuilder.timestamp();
        long end = start + 5_000L;
        long tsParent = start + 40L;
        long tsChild = start + 80L;

        var bloomParent = new byte[LogsBloomFilter.BYTE_SIZE];
        bloomParent[0] = (byte) 0x04;
        var bloomChild = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloomChild, (byte) 0x7f);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(0L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(tsParent)
                        .validStartNs(start)
                        .nonce(0)
                        .scheduled(false)
                        .parentConsensusTimestamp(tsParent - 1))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(tsChild)
                        .validStartNs(start)
                        .nonce(2)
                        .scheduled(false)
                        .parentConsensusTimestamp(tsParent)
                        .type(TransactionType.CRYPTOTRANSFER.getProtoId()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(
                        c -> c.consensusTimestamp(tsParent).bloom(bloomParent).gasUsed(5L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tsChild).bloom(bloomChild).gasUsed(9_999L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(2L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(5L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end))
                .isEqualTo(bloomParent);
    }

    @Test
    void includesContractResultWhenParentConsensusTimestampIsNullWithNonceGreaterThanZero() {
        long start = domainBuilder.timestamp();
        long end = start + 5_000L;
        long ts0 = start + 30L;
        long ts1 = start + 90L;

        var bloom0 = new byte[LogsBloomFilter.BYTE_SIZE];
        bloom0[10] = (byte) 0x01;
        var bloom1 = new byte[LogsBloomFilter.BYTE_SIZE];
        bloom1[11] = (byte) 0x02;

        var expectedBloom = new LogsBloomFilter();
        expectedBloom.or(bloom0);
        expectedBloom.or(bloom1);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(0L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ts0)
                        .validStartNs(start)
                        .nonce(0)
                        .scheduled(false)
                        .parentConsensusTimestamp(ts0 - 1))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(ts1)
                        .validStartNs(start)
                        .nonce(3)
                        .scheduled(false)
                        .parentConsensusTimestamp(null))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(ts0).bloom(bloom0).gasUsed(3L))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(ts1).bloom(bloom1).gasUsed(400L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(403L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end))
                .isEqualTo(expectedBloom.toArrayUnsafe());
    }

    @Test
    void includesContractResultForSystemFileUpdateWithNonceGreaterThanZero() {
        long start = domainBuilder.timestamp();
        long end = start + 5_000L;
        long tsFile = start + 60L;

        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        Arrays.fill(bloom, (byte) 0x33);

        var systemPayer = EntityId.of(0L, 0L, 50L);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start)
                        .consensusEnd(end)
                        .count(0L)
                        .gasUsed(0L)
                        .logsBloom(null))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(tsFile)
                        .validStartNs(start)
                        .nonce(4)
                        .scheduled(false)
                        .parentConsensusTimestamp(tsFile - 10)
                        .type(TransactionType.FILEUPDATE.getProtoId())
                        .payerAccountId(systemPayer))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tsFile).bloom(bloom).gasUsed(123L))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(jdbcOperations.queryForObject(
                        "select count from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(1L);
        assertThat(jdbcOperations.queryForObject(
                        "select gas_used from record_file where consensus_end = ?", Long.class, end))
                .isEqualTo(123L);
        assertThat(jdbcOperations.queryForObject(
                        "select logs_bloom from record_file where consensus_end = ?", byte[].class, end))
                .isEqualTo(bloom);
    }
}
