// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Updates {@code record_file.consensus_start} and {@code record_file.consensus_end} with the earliest and latest
 * timestamp of the transactions that belong to each block.
 */
@Named
final class RecordFileConsensusTimestampsRecalculateMigration extends AsyncJavaMigration<Long> {

    /**
     * Mainnet: only {@code record_file} rows with {@code consensus_end} strictly after this instant are processed.
     * Jan 28, 2026 00:00:00 UTC.
     */
    private static final long MAINNET_MIN_CONSENSUS_END_TIMESTAMP = 1769558400000000000L;

    /**
     * Testnet: only {@code record_file} rows with {@code consensus_end} strictly after this instant are processed.
     * (Dec 29, 2025 00:00:00 UTC).
     */
    static final long TESTNET_MIN_CONSENSUS_END_TIMESTAMP = 1766966400000000000L;

    private static final Map<String, Long> DEFAULT_MIN_CONSENSUS_END_TIMESTAMP_BY_NETWORK = Map.of(
            HederaNetwork.MAINNET, MAINNET_MIN_CONSENSUS_END_TIMESTAMP,
            HederaNetwork.TESTNET, TESTNET_MIN_CONSENSUS_END_TIMESTAMP);

    static final String MIN_CONSENSUS_END_TIMESTAMP_KEY = "minConsensusEndTimestamp";

    static final long INTERVAL = Duration.ofDays(7).toNanos();

    private static final String CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE = """
                    create table if not exists processed_record_file_temp(
                        consensus_end bigint not null
                    );
            """;

    private static final String DROP_TEMPORARY_PROCESSED_RECORD_FILE_TABLE = """
                    drop table if exists processed_record_file_temp;
            """;

    private static final String SELECT_LAST_PROCESSED_TIMESTAMP = """
                    select coalesce(
                      (select consensus_end
                       from processed_record_file_temp
                       order by consensus_end asc
                       limit 1),
                      (select max(consensus_end)
                       from record_file
                       where consensus_end > :minConsensusEndTimestamp),
                      :minConsensusEndTimestamp
                    )
            """;

    private static final String INSERT_SLICE_CHECKPOINT = """
                    insert into processed_record_file_temp(consensus_end)
                    values (:consensusEndLowerBound)
            """;

    private static final String SELECT_RECORD_FILES_IN_SLICE = """
                    with record_file_slice as (
                        select rf.consensus_start,
                            rf.consensus_end,
                            rf.count
                        from record_file rf
                        where rf.consensus_end > :consensusEndLowerBound
                                and rf.consensus_end <= :consensusEndUpperBound
                    )
                    select rf.consensus_start,
                        rf.consensus_end,
                        rf.count,
                        count(t.consensus_timestamp)::bigint as actual_transactions_count
                    from record_file_slice rf
                    left join transaction t on t.consensus_timestamp >= rf.consensus_start
                            and t.consensus_timestamp <= rf.consensus_end
                            and t.consensus_timestamp > :consensusEndLowerBound - 10_000_000_000
                            and t.consensus_timestamp <= :consensusEndUpperBound
                    group by rf.consensus_start, rf.consensus_end, rf.count
                    having rf.count != count(t.consensus_timestamp)::bigint
                    order by rf.consensus_end;
            """;

    private static final String SELECT_PREV_CONSENSUS_END = """
                    select max(consensus_end)
                    from record_file
                    where consensus_end < :consensusStart
            """;

    // Using the consensus_end from the record_file as it is the primary key instead of searching
    // by consensus_start directly.
    private static final String SELECT_NEXT_CONSENSUS_START = """
                    select consensus_start
                    from record_file
                    where consensus_end > :consensusEnd
                    order by consensus_end
                    limit 1
            """;

    private static final String SELECT_MIN_TX_BEFORE_START = """
                    select min(gap_transactions.consensus_timestamp) as consensus_timestamp,
                           count(*)::bigint as transaction_count
                    from (
                        select t.consensus_timestamp
                        from transaction t
                        where t.consensus_timestamp > :prevConsensusEnd
                          and t.consensus_timestamp < :consensusStart
                        order by t.consensus_timestamp desc
                        limit :missingTransactionsCount
                    ) gap_transactions
            """;

    private static final String SELECT_MAX_TX_AFTER_END = """
                    select max(gap_transactions.consensus_timestamp) as consensus_timestamp,
                           count(*)::bigint as transaction_count
                    from (
                        select t.consensus_timestamp
                        from transaction t
                        where t.consensus_timestamp > :consensusEnd
                          and t.consensus_timestamp < :nextConsensusStart
                        order by t.consensus_timestamp asc
                        limit :missingTransactionsCount
                    ) gap_transactions
            """;

    private static final String UPDATE_SIDECAR_FILES_WITH_CALCULATED_TIMESTAMPS = """
                    update sidecar_file
                    set consensus_end = coalesce(:consensusEndCalculated, consensus_end)
                    where consensus_end = :consensusEnd
            """;

    private static final String UPDATE_RECORD_FILE_CALCULATED_TIMESTAMPS = """
                    update record_file
                    set consensus_start = coalesce(:consensusStartCalculated, consensus_start),
                        consensus_end = coalesce(:consensusEndCalculated, consensus_end)
                    where consensus_end = :consensusEnd
            """;

    private static final DataClassRowMapper<RecordFileSlice> RECORD_FILE_SLICE_ROW_MAPPER =
            new DataClassRowMapper<>(RecordFileSlice.class);
    private static final DataClassRowMapper<GapTransactionsResult> GAP_TRANSACTIONS_RESULT_ROW_MAPPER =
            new DataClassRowMapper<>(GapTransactionsResult.class);

    private final ImporterProperties importerProperties;
    private final boolean v2;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private long latestProcessedEndTimestamp;

    protected RecordFileConsensusTimestampsRecalculateMigration(
            final Environment environment,
            final DBProperties dbProperties,
            final ImporterProperties importerProperties,
            final @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.importerProperties = importerProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    private long getMinConsensusEndTimestamp() {
        var override = migrationProperties.getParams().get(MIN_CONSENSUS_END_TIMESTAMP_KEY);
        if (override != null) {
            return Long.parseLong(override);
        }
        return getDefaultMinConsensusEndTimestampForNetwork();
    }

    private long getDefaultMinConsensusEndTimestampForNetwork() {
        var network = importerProperties.getNetwork();
        var minTimestamp = DEFAULT_MIN_CONSENSUS_END_TIMESTAMP_BY_NETWORK.get(network);
        if (minTimestamp != null) {
            return minTimestamp;
        }

        log.info("No minimum consensus_end configured for network {}; processing all record_file rows", network);
        return 0L;
    }

    @Override
    public String getDescription() {
        return "Recalculate record_file's consensus_start and consensus_end timestamps.";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.28.0") : MigrationVersion.fromVersion("1.123.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        log.info("Create table processed_record_file_temp if not exists.");
        getJdbcOperations().execute(CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE);
        var minEnd = getMinConsensusEndTimestamp();
        latestProcessedEndTimestamp = Objects.requireNonNull(getNamedParameterJdbcOperations()
                .queryForObject(
                        SELECT_LAST_PROCESSED_TIMESTAMP,
                        new MapSqlParameterSource("minConsensusEndTimestamp", minEnd),
                        Long.class));
        return true;
    }

    @Override
    protected Long getInitial() {
        log.info(
                "Starting record_file timestamp recalculation migration with initial consensus_end upper bound: {}.",
                latestProcessedEndTimestamp);
        return latestProcessedEndTimestamp;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        final long minEnd = getMinConsensusEndTimestamp();
        if (consensusEndTimestamp <= minEnd) {
            log.info(
                    "Record_file consensus offset migration passed floor consensus_end {}; dropping temporary table.",
                    minEnd);
            getJdbcOperations().execute(DROP_TEMPORARY_PROCESSED_RECORD_FILE_TABLE);
            return Optional.empty();
        }

        final long consensusEndLowerBound = Math.max(consensusEndTimestamp - INTERVAL, minEnd);
        final var sliceParams = new MapSqlParameterSource()
                .addValue("consensusEndLowerBound", consensusEndLowerBound)
                .addValue("consensusEndUpperBound", consensusEndTimestamp);

        long updated = 0;
        var jdbc = getNamedParameterJdbcOperations();
        for (var block : jdbc.query(SELECT_RECORD_FILES_IN_SLICE, sliceParams, RECORD_FILE_SLICE_ROW_MAPPER)) {
            final long missingTransactionsCount = block.count() - block.actualTransactionsCount();
            final var beforeStartGapResult = computeStartTimestamp(block);
            final var consensusStartCalculated = getTimestampOrNull(beforeStartGapResult);
            final long transactionsCountBeforeStart = getTransactionsCountOrZero(beforeStartGapResult);
            Long consensusEndCalculated = null;
            if (missingTransactionsCount > transactionsCountBeforeStart) {
                // There are some transactions after the block's consensus_end.
                // Updating the consensus_end means we need to update the block's sidecars as well.
                final var afterEndGapResult = computeEndTimestamp(block);
                consensusEndCalculated = getTimestampOrNull(afterEndGapResult);
                final long transactionsCountAfterEnd = getTransactionsCountOrZero(afterEndGapResult);

                updateSidecarFiles(block, consensusEndCalculated);
                logFailedRecalculationIfNeeded(block, transactionsCountBeforeStart, transactionsCountAfterEnd);
            }

            var updateParams = new MapSqlParameterSource()
                    .addValue("consensusEnd", block.consensusEnd())
                    .addValue("consensusStartCalculated", consensusStartCalculated)
                    .addValue("consensusEndCalculated", consensusEndCalculated);
            jdbc.update(UPDATE_RECORD_FILE_CALCULATED_TIMESTAMPS, updateParams);
            updated++;
        }

        jdbc.update(
                INSERT_SLICE_CHECKPOINT, new MapSqlParameterSource("consensusEndLowerBound", consensusEndLowerBound));

        log.info(
                "Updated {} record_file row(s) for consensus_end in ({}, {}].",
                updated,
                consensusEndLowerBound,
                consensusEndTimestamp);

        if (consensusEndLowerBound <= minEnd) {
            log.info(
                    "Record_file consensus timestamp recalculation migration complete; dropping temporary table processed_record_file_temp.");
            getJdbcOperations().execute(DROP_TEMPORARY_PROCESSED_RECORD_FILE_TABLE);
            return Optional.empty();
        }
        return Optional.of(consensusEndLowerBound);
    }

    private void updateSidecarFiles(RecordFileSlice block, Long consensusEndCalculated) {
        var jdbc = getNamedParameterJdbcOperations();
        var sidecarUpdateParams = new MapSqlParameterSource()
                .addValue("consensusEnd", block.consensusEnd())
                .addValue("consensusEndCalculated", consensusEndCalculated);
        int updatedSidecars = jdbc.update(UPDATE_SIDECAR_FILES_WITH_CALCULATED_TIMESTAMPS, sidecarUpdateParams);
        log.info(
                "Updated {} sidecar_file row(s) for consensus_end {} with calculated timestamp {}.",
                updatedSidecars,
                block.consensusEnd(),
                consensusEndCalculated);
    }

    @Nullable
    private Long lookupPrevConsensusEnd(long consensusStart) {
        return queryForObjectOrNull(
                SELECT_PREV_CONSENSUS_END, new MapSqlParameterSource("consensusStart", consensusStart), Long.class);
    }

    @Nullable
    private Long lookupNextConsensusStart(long consensusEnd) {
        return queryForObjectOrNull(
                SELECT_NEXT_CONSENSUS_START, new MapSqlParameterSource("consensusEnd", consensusEnd), Long.class);
    }

    @Nullable
    private GapTransactionsResult computeStartTimestamp(RecordFileSlice block) {
        var prevConsensusEnd = lookupPrevConsensusEnd(block.consensusStart());
        var params = new MapSqlParameterSource()
                .addValue("consensusStart", block.consensusStart())
                .addValue("prevConsensusEnd", prevConsensusEnd != null ? prevConsensusEnd : 0L)
                .addValue("missingTransactionsCount", block.count() - block.actualTransactionsCount());
        return queryForObjectOrNull(SELECT_MIN_TX_BEFORE_START, params, GAP_TRANSACTIONS_RESULT_ROW_MAPPER);
    }

    @Nullable
    private GapTransactionsResult computeEndTimestamp(RecordFileSlice block) {
        var nextConsensusStart = lookupNextConsensusStart(block.consensusEnd());
        var params = new MapSqlParameterSource()
                .addValue("consensusEnd", block.consensusEnd())
                .addValue("nextConsensusStart", nextConsensusStart != null ? nextConsensusStart : Long.MAX_VALUE)
                .addValue("missingTransactionsCount", block.count() - block.actualTransactionsCount());
        return queryForObjectOrNull(SELECT_MAX_TX_AFTER_END, params, GAP_TRANSACTIONS_RESULT_ROW_MAPPER);
    }

    private TransactionOperations transactionOperations() {
        var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private void logFailedRecalculationIfNeeded(
            RecordFileSlice block, long transactionsCountBeforeStart, long transactionsCountAfterEnd) {
        if (transactionsCountBeforeStart + transactionsCountAfterEnd
                != block.count() - block.actualTransactionsCount()) {
            log.warn(
                    "The transactions count before record_file start {} and after end {} does not match the missing"
                            + "transactions count {} for record_file with consensus_end {}.",
                    transactionsCountBeforeStart,
                    transactionsCountAfterEnd,
                    (block.count() - block.actualTransactionsCount()),
                    block.consensusEnd());
        }
    }

    private @Nullable Long getTimestampOrNull(GapTransactionsResult gapTransactionsResult) {
        return gapTransactionsResult != null ? gapTransactionsResult.consensusTimestamp() : null;
    }

    private long getTransactionsCountOrZero(GapTransactionsResult gapTransactionsResult) {
        return gapTransactionsResult != null ? gapTransactionsResult.transactionCount() : 0L;
    }

    private record RecordFileSlice(long consensusStart, long consensusEnd, long count, long actualTransactionsCount) {}

    private record GapTransactionsResult(Long consensusTimestamp, long transactionCount) {}
}
