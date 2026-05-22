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
class RecordFileConsensusTimestampsRecalculateMigrationTest
        extends AbstractAsyncJavaMigrationTest<RecordFileConsensusTimestampsRecalculateMigration> {

    private static final String SELECT_LAST_CHECKSUM_SQL = """
            select (
              select checksum from flyway_schema_history
              where description = ?
              order by installed_rank desc
              limit 1
            )
            """;

    private static final long END_TIMESTAMP =
            RecordFileConsensusTimestampsRecalculateMigration.TESTNET_MIN_CONSENSUS_END_TIMESTAMP;

    @Getter
    private final RecordFileConsensusTimestampsRecalculateMigration migration;

    private final JdbcOperations jdbcOperations;

    @BeforeEach
    void configureMinConsensusEndTimestamp() {
        migration
                .migrationProperties
                .getParams()
                .put(
                        RecordFileConsensusTimestampsRecalculateMigration.MIN_CONSENSUS_END_TIMESTAMP_KEY,
                        String.valueOf(
                                RecordFileConsensusTimestampsRecalculateMigration.TESTNET_MIN_CONSENSUS_END_TIMESTAMP));
    }

    @AfterEach
    void cleanup() {
        migration
                .migrationProperties
                .getParams()
                .remove(RecordFileConsensusTimestampsRecalculateMigration.MIN_CONSENSUS_END_TIMESTAMP_KEY);
        jdbcOperations.execute("drop table if exists processed_record_file_temp");
    }

    @Test
    void migrationOnEmptyDb() {
        runMigration();
        waitForCompletionExtended();
    }

    @Test
    void setsStartTimestampToEarliestGapTxBeforeConsensusStart() {
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

        assertThat(startCalculated(currEnd)).isEqualTo(earlierInGap);
        assertThat(endCalculated(currEnd)).isEqualTo(currEnd);
        assertThat(startCalculated(prevEnd)).isEqualTo(prevStart);
        assertThat(endCalculated(prevEnd)).isEqualTo(prevEnd);
    }

    @Test
    void setsEndTimestampToLatestGapTxAfterConsensusEnd() {
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

        assertThat(endCalculated(latestAfterEnd)).isEqualTo(latestAfterEnd);
        assertThat(startCalculated(latestAfterEnd)).isEqualTo(currStart);
        assertThat(startCalculated(nextEnd)).isEqualTo(nextStart);
        assertThat(endCalculated(nextEnd)).isEqualTo(nextEnd);
    }

    @Test
    void setsStartAndEndTimestampForGapTransactionsAroundCurrentBlock() {
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
        insertRecordFile(currStart, currEnd, 4L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertTransaction(earliestBeforeStart);
        insertTransaction(latestBeforeStart);
        insertTransaction(earliestAfterEnd);
        insertTransaction(latestAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(startCalculated(latestAfterEnd)).isEqualTo(earliestBeforeStart);
        assertThat(endCalculated(latestAfterEnd)).isEqualTo(latestAfterEnd);
        assertThat(startCalculated(prevEnd)).isEqualTo(prevStart);
        assertThat(endCalculated(prevEnd)).isEqualTo(prevEnd);
        assertThat(startCalculated(nextEnd)).isEqualTo(nextStart);
        assertThat(endCalculated(nextEnd)).isEqualTo(nextEnd);
    }

    @Test
    void setsStartAndEndTimestampForGapTransactionsBeforeBlockStartAndAfterPrevEnd() {
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

        assertThat(startCalculated(currEnd)).isEqualTo(earliestCurrBeforeStart);
        assertThat(endCalculated(currEnd)).isEqualTo(currEnd);
        assertThat(startCalculated(latestPrevAfterEnd)).isEqualTo(prevStart);
        assertThat(endCalculated(latestPrevAfterEnd)).isEqualTo(latestPrevAfterEnd);
    }

    @Test
    void updatesSidecarFilesWhenConsensusEndIsRecalculated() {
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long nextStart = END_TIMESTAMP + 4_000L;
        long nextEnd = END_TIMESTAMP + 5_000L;
        long currAfterEnd = END_TIMESTAMP + 3_900L;

        insertRecordFile(currStart, currEnd, 1L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertSidecarFile(currEnd, 0);
        insertTransaction(currAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(endCalculated(currAfterEnd)).isEqualTo(currAfterEnd);
        assertThat(sidecarConsensusEnd(currAfterEnd, 0)).isEqualTo(currAfterEnd);
    }

    @Test
    void doesNotUpdateSidecarFilesWhenOnlyConsensusStartChanges() {
        long prevStart = END_TIMESTAMP + 100L;
        long prevEnd = END_TIMESTAMP + 1_000L;
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long earlierInGap = END_TIMESTAMP + 1_100L;

        insertRecordFile(prevStart, prevEnd, 0L);
        insertRecordFile(currStart, currEnd, 1L);
        insertSidecarFile(currEnd, 0);
        insertSidecarFile(currEnd, 1);
        insertTransaction(earlierInGap);

        runMigration();
        waitForCompletionExtended();

        assertThat(startCalculated(currEnd)).isEqualTo(earlierInGap);
        assertThat(endCalculated(currEnd)).isEqualTo(currEnd);
        assertThat(sidecarConsensusEnd(currEnd, 0)).isEqualTo(currEnd);
        assertThat(sidecarConsensusEnd(currEnd, 1)).isEqualTo(currEnd);
    }

    @Test
    void updatesAllSidecarFilesForRecordFileWhenConsensusEndIsRecalculated() {
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long nextStart = END_TIMESTAMP + 4_000L;
        long nextEnd = END_TIMESTAMP + 5_000L;
        long earliestAfterEnd = END_TIMESTAMP + 3_100L;
        long latestAfterEnd = END_TIMESTAMP + 3_900L;

        insertRecordFile(currStart, currEnd, 2L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertSidecarFile(currEnd, 0);
        insertSidecarFile(currEnd, 1);
        insertTransaction(earliestAfterEnd);
        insertTransaction(latestAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(endCalculated(latestAfterEnd)).isEqualTo(latestAfterEnd);
        assertThat(sidecarConsensusEnd(latestAfterEnd, 0)).isEqualTo(latestAfterEnd);
        assertThat(sidecarConsensusEnd(latestAfterEnd, 1)).isEqualTo(latestAfterEnd);
    }

    @Test
    void doesNotUpdateSidecarFilesForOtherRecordFiles() {
        long currStart = END_TIMESTAMP + 2_000L;
        long currEnd = END_TIMESTAMP + 3_000L;
        long nextStart = END_TIMESTAMP + 4_000L;
        long nextEnd = END_TIMESTAMP + 5_000L;
        long latestCurrAfterEnd = END_TIMESTAMP + 3_900L;

        insertRecordFile(currStart, currEnd, 1L);
        insertRecordFile(nextStart, nextEnd, 0L);
        insertSidecarFile(currEnd, 0);
        insertSidecarFile(nextEnd, 0);
        insertTransaction(latestCurrAfterEnd);

        runMigration();
        waitForCompletionExtended();

        assertThat(sidecarConsensusEnd(latestCurrAfterEnd, 0)).isEqualTo(latestCurrAfterEnd);
        assertThat(sidecarConsensusEnd(nextEnd, 0)).isEqualTo(nextEnd);
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

    private Long startCalculated(long consensusEnd) {
        return jdbcOperations.queryForObject(
                "select consensus_start from record_file where consensus_end = ?", Long.class, consensusEnd);
    }

    private Long endCalculated(long consensusEnd) {
        return jdbcOperations.queryForObject(
                "select consensus_end from record_file where consensus_end = ?", Long.class, consensusEnd);
    }

    private Long sidecarConsensusEnd(long consensusEnd, int id) {
        return jdbcOperations.queryForObject(
                "select consensus_end from sidecar_file where consensus_end = ? and id = ?",
                Long.class,
                consensusEnd,
                id);
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

    private void insertSidecarFile(long consensusEnd, int id) {
        jdbcOperations.update("""
                        insert into sidecar_file (
                          consensus_end, hash_algorithm, hash, id, name, size, types
                        )
                        values (?, ?, ?, ?, ?, ?, ?::int[])
                        """, consensusEnd, 0, new byte[48], id, consensusEnd + "_" + id + ".rcd.gz", 256, "{1}");
    }
}
