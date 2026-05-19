// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

@RequiredArgsConstructor
@Tag("migration")
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
class RecordFileConsensusTimestampsOffsetMigrationTest
        extends AbstractAsyncJavaMigrationTest<RecordFileConsensusTimestampsOffsetMigration> {

    private static final String SELECT_LAST_CHECKSUM_SQL = """
            select (
              select checksum from flyway_schema_history
              where description = ?
              order by installed_rank desc
              limit 1
            )
            """;

    private static final long END_TIMESTAMP =
            RecordFileConsensusTimestampsOffsetMigration.TESTNET_MIN_CONSENSUS_END_TIMESTAMP;

    @Getter
    private final RecordFileConsensusTimestampsOffsetMigration migration;

    private final JdbcOperations jdbcOperations;

    @BeforeEach
    void configureMinConsensusEndTimestamp() {
        migration
                .migrationProperties
                .getParams()
                .put(
                        RecordFileConsensusTimestampsOffsetMigration.MIN_CONSENSUS_END_TIMESTAMP_KEY,
                        String.valueOf(
                                RecordFileConsensusTimestampsOffsetMigration.TESTNET_MIN_CONSENSUS_END_TIMESTAMP));
    }

    @AfterEach
    void cleanup() {
        migration
                .migrationProperties
                .getParams()
                .remove(RecordFileConsensusTimestampsOffsetMigration.MIN_CONSENSUS_END_TIMESTAMP_KEY);
        jdbcOperations.execute("drop table if exists processed_record_file_temp");
    }

    @Test
    void migrationOnEmptyDb() {
        runMigration();
        waitForCompletionExtended();

        assertThat(columnExists("record_file", "consensus_start_offset")).isTrue();
        assertThat(columnExists("record_file", "consensus_end_offset")).isTrue();
    }

    @Test
    void setsStartOffsetToEarliestGapTxBeforeConsensusStart() {
        long prevStart = END_TIMESTAMP + 100L;
        long prevEnd = END_TIMESTAMP + 1_000L;
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long earlierInGap = END_TIMESTAMP + 1_100L;
        long latestInGap = END_TIMESTAMP + 1_900L;

        insertRecordFile(prevStart, prevEnd, 0L);
        insertRecordFile(currStart, currEnd, 2L);
        insertTransaction(earlierInGap);
        insertTransaction(latestInGap);

        runMigration();
        waitForCompletionExtended();

        assertThat(startOffset(currEnd)).isEqualTo(earlierInGap - currStart);
        assertThat(endOffset(currEnd)).isZero();
        assertThat(startOffset(prevEnd)).isZero();
        assertThat(endOffset(prevEnd)).isZero();
    }

    @Test
    void setsEndOffsetToLatestGapTxAfterConsensusEnd() {
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long nextStart = END_TIMESTAMP + 4_000L;
        long nextEnd = END_TIMESTAMP + 5_000L;
        long earliestAfterEnd = END_TIMESTAMP + 3_100L;
        long latestAfterEnd = END_TIMESTAMP + 3_900L;

        insertRecordFile(currStart, currEnd, 2L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertTransaction(earliestAfterEnd);
        insertTransaction(latestAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(startOffset(currEnd)).isZero();
        assertThat(endOffset(currEnd)).isEqualTo(latestAfterEnd - currEnd);
        assertThat(startOffset(nextEnd)).isZero();
        assertThat(endOffset(nextEnd)).isZero();
    }

    @Test
    void setsStartAndEndOffsetForGapTransactionsAroundCurrentBlock() {
        long prevStart = END_TIMESTAMP + 100L;
        long prevEnd = END_TIMESTAMP + 1_000L;
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long nextStart = END_TIMESTAMP + 4_000L;
        long nextEnd = END_TIMESTAMP + 5_000L;
        long earliestBeforeStart = END_TIMESTAMP + 1_100L;
        long latestBeforeStart = END_TIMESTAMP + 1_900L;
        long earliestAfterEnd = END_TIMESTAMP + 3_100L;
        long latestAfterEnd = END_TIMESTAMP + 3_900L;

        insertRecordFile(prevStart, prevEnd, 0L);
        insertRecordFile(currStart, currEnd, 2L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertTransaction(earliestBeforeStart);
        insertTransaction(latestBeforeStart);
        insertTransaction(earliestAfterEnd);
        insertTransaction(latestAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(startOffset(currEnd)).isEqualTo(earliestBeforeStart - currStart);
        assertThat(endOffset(currEnd)).isEqualTo(latestAfterEnd - currEnd);
        assertThat(startOffset(prevEnd)).isZero();
        assertThat(endOffset(prevEnd)).isZero();
        assertThat(startOffset(nextEnd)).isZero();
        assertThat(endOffset(nextEnd)).isZero();
    }

    @Test
    void setsStartAndEndOffsetForGapTransactionsBeforeBlockStartAndAfterPrevEnd() {
        long prevStart = END_TIMESTAMP + 100L;
        long prevEnd = END_TIMESTAMP + 1_000L;
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long earliestPrevAfterEnd = END_TIMESTAMP + 1_100L;
        long latestPrevAfterEnd = END_TIMESTAMP + 1_200L;
        long earliestCurrBeforeStart = END_TIMESTAMP + 1_500L;
        long latestCurrBeforeStart = END_TIMESTAMP + 1_900L;

        insertRecordFile(prevStart, prevEnd, 2L);
        insertRecordFile(currStart, currEnd, 2L);
        insertTransaction(earliestPrevAfterEnd);
        insertTransaction(latestPrevAfterEnd);
        insertTransaction(earliestCurrBeforeStart);
        insertTransaction(latestCurrBeforeStart);

        runMigration();
        waitForCompletionExtended();

        assertThat(startOffset(currEnd)).isEqualTo(earliestCurrBeforeStart - currStart);
        assertThat(endOffset(currEnd)).isZero();
        assertThat(startOffset(prevEnd)).isZero();
        assertThat(endOffset(prevEnd)).isEqualTo(latestPrevAfterEnd - prevEnd);
    }

    private void waitForCompletionExtended() {
        await().atMost(Duration.ofMinutes(2))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcOperations.queryForObject(SELECT_LAST_CHECKSUM_SQL, Integer.class, migration.getDescription());
        return Objects.equals(actual, migration.getSuccessChecksum());
    }

    private boolean columnExists(String tableName, String columnName) {
        return Boolean.TRUE.equals(jdbcOperations.queryForObject("""
                        select exists(
                          select 1
                          from information_schema.columns
                          where table_name = ? and column_name = ?
                        )
                        """, Boolean.class, tableName, columnName));
    }

    private long startOffset(long consensusEnd) {
        return jdbcOperations.queryForObject(
                "select coalesce(consensus_start_offset, 0) from record_file where consensus_end = ?",
                Long.class,
                consensusEnd);
    }

    private long endOffset(long consensusEnd) {
        return jdbcOperations.queryForObject(
                "select coalesce(consensus_end_offset, 0) from record_file where consensus_end = ?",
                Long.class,
                consensusEnd);
    }

    private void insertRecordFile(long consensusStart, long consensusEnd, long count) {
        jdbcOperations.update(
                """
                        insert into record_file (
                          consensus_start, consensus_end, count, digest_algorithm, file_hash, hash, index, load_start,
                          load_end, name, prev_hash, version
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                consensusStart,
                consensusEnd,
                count,
                0,
                Long.toHexString(consensusEnd),
                Long.toHexString(consensusEnd),
                consensusEnd,
                consensusEnd,
                consensusEnd,
                consensusEnd + ".rcd",
                Long.toHexString(consensusStart),
                6);
    }

    private void insertTransaction(long consensusTimestamp) {
        jdbcOperations.update("""
                        insert into transaction (
                          consensus_timestamp, nonce, payer_account_id, result, scheduled, type, valid_start_ns
                        )
                        values (?, ?, ?, ?, ?, ?, ?)
                        """, consensusTimestamp, 0, 100, 22, false, 14, consensusTimestamp);
    }
}
