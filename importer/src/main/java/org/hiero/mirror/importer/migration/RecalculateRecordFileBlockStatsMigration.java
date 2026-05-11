// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recomputes {@code record_file.count}, {@code record_file.gas_used}, and {@code record_file.logs_bloom} from
 * {@code transaction} and {@code contract_result} rows in each record_file's consensus range.
 * Only processes {@code record_file} rows whose stored {@code count} is different from the number of
 * {@code transaction} rows in {@code [consensus_start, consensus_end]}.
 */
@Named
final class RecalculateRecordFileBlockStatsMigration extends AsyncJavaMigration<Long> {

    private static final long INTERVAL = Duration.ofDays(30).toNanos();

    // The earliest affected block consensus end timestamp on mainnet.
    private static final long MAINNET_MIN_CONSENSUS_END = 1769623811888273000L;

    private static final String CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE = """
            create table if not exists processed_record_file_temp(
                consensus_end bigint not null
            );
            """;

    private static final String DROP_TEMPORARY_PROGRESS_TABLE = """
            drop table if exists processed_record_file_temp;
            """;

    private static final String SELECT_RECORD_FILES_MIN_AND_MAX_TIMESTAMP = """
            select consensus_start as min_consensus_timestamp, max_consensus_timestamp
            from record_file
            join (
              select rf.consensus_end
              from record_file as rf
              where rf.consensus_end > :consensusEndLowerBound
                and rf.consensus_end <= :consensusEndUpperBound
              order by rf.consensus_end desc
              limit 1
            ) as t(max_consensus_timestamp) on true
            where consensus_end > :consensusEndLowerBound
              and consensus_end <= :consensusEndUpperBound
            order by consensus_end
            limit 1;
            """;

    /**
     * Record files in the slice whose count does not match the count of transactions in range
     * [record_file.consensus_start; record_file.consensus_end] as well as the record file at the previous
     * index as it needs to be updated as well.
     */
    private static final String SELECT_TARGETS_TO_RECALCULATE = """
            with slice_files as (
                select rf.consensus_end, rf.consensus_start, rf.count
                from record_file rf
                where rf.consensus_end > :consensusEndLowerBound
                  and rf.consensus_end <= :consensusEndUpperBound
            ),
            affected as (
                select sf.consensus_end, sf.consensus_start
                from slice_files sf
                where sf.count is distinct from (
                    select count(*)::bigint
                    from transaction t
                    where t.consensus_timestamp >= sf.consensus_start
                      and t.consensus_timestamp <= sf.consensus_end
                )
            ),
            targets as (
                select a.consensus_end, a.consensus_start
                from affected a
                union all
                select prev.consensus_end, prev.consensus_start
                from affected a
                cross join lateral (
                    select r.consensus_end, r.consensus_start
                    from record_file r
                    where r.consensus_end < a.consensus_start
                    order by r.consensus_end desc
                    limit 1
                ) prev
            )
            select distinct t.consensus_end, t.consensus_start
            from targets t
            order by t.consensus_end
            """;

    private static final String INSERT_CHECKPOINT = """
            insert into processed_record_file_temp(consensus_end)
            values(:consensusStart)
            """;

    private static final String COUNT_TRANSACTIONS = """
            select count(*) from transaction
            where consensus_timestamp >= :consensusStart and consensus_timestamp <= :consensusEnd
            """;

    /** SQL predicate aligned with {@link org.hiero.mirror.common.domain.transaction.RecordItem#isTopLevel()} */
    private static final String TX_IS_TOP_LEVEL_SQL = """
            (t.nonce = 0
                or (t.nonce > 0 and t.scheduled = true)
                or t.parent_consensus_timestamp is null
                or (t.type = %d and t.payer_account_id = :systemFileUpdatePayerId))""".formatted(TransactionType.FILEUPDATE.getProtoId());

    private static final String SELECT_CONTRACT_RESULT = """
            select cr.bloom, cr.gas_used
            from contract_result cr
            join transaction t on t.consensus_timestamp = cr.consensus_timestamp
            where cr.consensus_timestamp >= :consensusStart
              and cr.consensus_timestamp <= :consensusEnd
              and %s
            """.formatted(TX_IS_TOP_LEVEL_SQL);

    private static final String UPDATE_RECORD_FILE = """
            update record_file
            set count = :count,
                gas_used = :gasUsed,
                logs_bloom = :logsBloom
            where consensus_end = :consensusEnd
            """;

    private static final String V2_PROPERTY_MAX_INTERMEDIATE_RESULTS = "set citus.max_intermediate_result_size = -1;";

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);

    private final ImporterProperties importerProperties;
    private final long systemFileUpdatePayerEntityId;
    private final boolean v2;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    RecalculateRecordFileBlockStatsMigration(
            Environment environment,
            DBProperties dbProperties,
            CommonProperties commonProperties,
            ImporterProperties importerProperties,
            final @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.importerProperties = importerProperties;
        this.systemFileUpdatePayerEntityId = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), 50L)
                .getId();
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Recalculate record_file count, gas_used, and logs_bloom where count is different from transaction rows.";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.27.0") : MigrationVersion.fromVersion("1.122.0");
    }

    @Override
    protected Long getInitial() {
        log.info("Create table processed_record_file_temp if not exists.");
        getJdbcOperations().execute(CREATE_TEMPORARY_PROCESSED_RECORD_FILE_TABLE);

        var resumeParams = new MapSqlParameterSource();
        Long resumeUpperBound = queryForObjectOrNull("""
                        select consensus_end
                        from processed_record_file_temp
                        order by consensus_end asc
                        limit 1
                        """, resumeParams, Long.class);
        if (resumeUpperBound != null) {
            log.info(
                    "Resuming record_file block stats migration from checkpoint consensus_end upper bound: {}.",
                    resumeUpperBound);
            return resumeUpperBound;
        }

        Long maxConsensusEnd =
                getJdbcOperations().queryForObject("select max(consensus_end) from record_file", Long.class);
        if (maxConsensusEnd == null) {
            maxConsensusEnd = 0L;
        }
        log.info(
                "Starting record_file block stats migration with initial consensus_end upper bound: {}.",
                maxConsensusEnd);
        return maxConsensusEnd;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        if (isMainnet() && consensusEndTimestamp < MAINNET_MIN_CONSENSUS_END) {
            log.info(
                    "Mainnet record_file block stats migration passed floor consensus_end {}; dropping temporary table.",
                    MAINNET_MIN_CONSENSUS_END);
            getJdbcOperations().execute(DROP_TEMPORARY_PROGRESS_TABLE);
            return Optional.empty();
        }

        var consensusEndLowerBound = consensusEndTimestamp - INTERVAL;
        if (isMainnet() && consensusEndLowerBound < MAINNET_MIN_CONSENSUS_END) {
            consensusEndLowerBound = MAINNET_MIN_CONSENSUS_END;
        }

        var recordFileSliceParams = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusEndLowerBound);
        final var recordFileSlice =
                queryForObjectOrNull(SELECT_RECORD_FILES_MIN_AND_MAX_TIMESTAMP, recordFileSliceParams, ROW_MAPPER);
        if (recordFileSlice == null) {
            log.info(
                    "No more record files remaining to process for record_file block stats migration. Last upper bound: {}. "
                            + "Dropping temporary table processed_record_file_temp.",
                    consensusEndTimestamp);
            getJdbcOperations().execute(DROP_TEMPORARY_PROGRESS_TABLE);
            return Optional.empty();
        }

        final long sliceStartTimestamp = recordFileSlice.minConsensusTimestamp();
        final long sliceEndTimestamp = recordFileSlice.maxConsensusTimestamp();
        log.info(
                "Scanning slice consensus_start {} .. consensus_end {} for mismatched record_file.count.",
                sliceStartTimestamp,
                sliceEndTimestamp);

        final var sliceBounds = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusEndLowerBound);

        final var checkpointParams = new MapSqlParameterSource("consensusStart", sliceStartTimestamp);

        var targets = new AtomicInteger(0);
        var updated = new AtomicInteger(0);
        getTransactionOperations().executeWithoutResult(_ -> {
            if (v2) {
                getJdbcOperations().execute(V2_PROPERTY_MAX_INTERMEDIATE_RESULTS);
            }

            var jdbc = getNamedParameterJdbcOperations();
            jdbc.query(SELECT_TARGETS_TO_RECALCULATE, sliceBounds, rs -> {
                targets.incrementAndGet();
                long consensusEnd = rs.getLong("consensus_end");
                long consensusStart = rs.getLong("consensus_start");
                var rangeParams = new MapSqlParameterSource()
                        .addValue("consensusStart", consensusStart)
                        .addValue("consensusEnd", consensusEnd);
                Long txCount = jdbc.queryForObject(COUNT_TRANSACTIONS, rangeParams, Long.class);
                if (txCount == null) {
                    txCount = 0L;
                }

                var logsBloomFilter = new LogsBloomFilter();
                var gasUsedTotal = new AtomicLong(0);
                var contractResultParams = new MapSqlParameterSource()
                        .addValue("consensusStart", consensusStart)
                        .addValue("consensusEnd", consensusEnd)
                        .addValue("systemFileUpdatePayerId", systemFileUpdatePayerEntityId);
                jdbc.query(SELECT_CONTRACT_RESULT, contractResultParams, crs -> {
                    long gas = crs.getLong("gas_used");
                    if (!crs.wasNull()) {
                        gasUsedTotal.addAndGet(gas);
                    }
                    var bloom = crs.getBytes("bloom");
                    if (bloom != null && bloom.length == LogsBloomFilter.BYTE_SIZE) {
                        logsBloomFilter.or(bloom);
                    }
                });

                var updateParams = new MapSqlParameterSource()
                        .addValue("consensusEnd", consensusEnd)
                        .addValue("count", txCount)
                        .addValue("gasUsed", gasUsedTotal.get())
                        .addValue("logsBloom", logsBloomFilter.toArrayUnsafe());

                updated.addAndGet(jdbc.update(UPDATE_RECORD_FILE, updateParams));
            });

            jdbc.update(INSERT_CHECKPOINT, checkpointParams);
        });

        log.info(
                "Slice {}..{}: recalculated {} record_file row(s).",
                sliceStartTimestamp,
                sliceEndTimestamp,
                updated.get());

        return Optional.of(consensusEndTimestamp - INTERVAL);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private boolean isMainnet() {
        return ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork());
    }

    private record RecordFileSlice(long minConsensusTimestamp, long maxConsensusTimestamp) {}
}
