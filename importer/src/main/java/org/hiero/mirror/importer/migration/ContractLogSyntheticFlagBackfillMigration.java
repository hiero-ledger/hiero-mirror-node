// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
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

@Named
final class ContractLogSyntheticFlagBackfillMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "3h";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String SET_CITUS_LIMIT = "set citus.max_intermediate_result_size = -1";

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists contract_log_synthetic_flag_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists contract_log_synthetic_flag_progress_temp;
            """;

    private static final String SELECT_MAX_CONSENSUS_END = "select max(consensus_end) from record_file";

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select upper_bound from contract_log_synthetic_flag_progress_temp limit 1";

    // contract_log.synthetic was added in V1.121.0/V2.26.0.
    private static final String SELECT_MIGRATION_INSTALLED_ON = """
            select installed_on from flyway_schema_history
            where version in ('1.121.0', '2.26.0')
            order by installed_rank desc limit 1
            """;

    private static final String SELECT_RECORD_FILE_INDEX_BOUNDS =
            "select min(index) as min_index, max(index) as max_index from record_file";

    // No index on load_start; index is sequential with load order and is indexed.
    private static final String SELECT_RECORD_FILE_AT_OR_AFTER_INDEX = """
            select consensus_start, load_start, index from record_file
            where index >= :index and index <= :maxIndex
            order by index limit 1
            """;

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from contract_log_synthetic_flag_progress_temp)
            insert into contract_log_synthetic_flag_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String SELECT_RECORD_FILES_RANGE = """
            select
                (select consensus_start from record_file
                    where consensus_end between :consensusEndLowerBound and :consensusEndUpperBound
                    order by consensus_end limit 1) as min_consensus_timestamp,
                (select consensus_end from record_file
                    where consensus_end between :consensusEndLowerBound and :consensusEndUpperBound
                    order by consensus_end desc limit 1) as max_consensus_timestamp
            """;

    private static final String BACKFILL_SYNTHETIC_SQL = """
            with backfilled as (
                update contract_log
                set synthetic = true
                where synthetic is not true
                  and consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and consensus_timestamp not in (
                    select consensus_timestamp from contract_result
                    where consensus_timestamp between :consensusStart and :lastConsensusEnd
                  )
                returning consensus_timestamp
            ),
            inserted as (
                insert into contract_log_synthetic_backfill_updated_temp (consensus_timestamp)
                select distinct consensus_timestamp from backfilled
            )
            select count(*) from backfilled
            """;

    private static final String CREATE_UPDATED_TIMESTAMPS_TEMP_TABLE = """
            create temporary table contract_log_synthetic_backfill_updated_temp (
                consensus_timestamp bigint not null
            ) on commit drop
            """;

    private static final String CREATE_IMPACTED_RECORD_FILES_TEMP_TABLE = """
            create temporary table contract_log_synthetic_backfill_impacted_files_temp (
                consensus_start bigint not null,
                consensus_end bigint primary key
            ) on commit drop
            """;

    private static final String POPULATE_IMPACTED_RECORD_FILES_SQL = """
            insert into contract_log_synthetic_backfill_impacted_files_temp (consensus_start, consensus_end)
            select distinct rf.consensus_start, rf.consensus_end
            from contract_log_synthetic_backfill_updated_temp t
            join lateral (
                select consensus_start, consensus_end from record_file
                where consensus_end >= t.consensus_timestamp
                  and consensus_end between :consensusStart and :lastConsensusEnd
                order by consensus_end limit 1
            ) rf on true
            """;

    // Newly-flagged rows were missing from FixEvmTransactionIndexMigration's EVM slot count.
    private static final String RECOMPUTE_EVM_TRANSACTION_INDEX_SQL = """
            with evm_candidates as (
                select
                    cr.consensus_timestamp,
                    (cr.transaction_nonce = 0 or cr.contract_id = :hookContractId) as is_root
                from contract_result cr
                join contract_log_synthetic_backfill_impacted_files_temp irf
                    on cr.consensus_timestamp between irf.consensus_start and irf.consensus_end
                where cr.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cr.transaction_result <> 312
                union all
                -- Excludes rows whose parent contract call already has a contract_result at the same timestamp,
                -- otherwise that timestamp is counted twice, inflating the index for every later transaction.
                select distinct
                    cl.consensus_timestamp,
                    true as is_root
                from contract_log cl
                join contract_log_synthetic_backfill_impacted_files_temp irf
                    on cl.consensus_timestamp between irf.consensus_start and irf.consensus_end
                where cl.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cl.synthetic = true
                  and cl.consensus_timestamp not in (
                    select cr2.consensus_timestamp from contract_result cr2
                    join contract_log_synthetic_backfill_impacted_files_temp irf2
                        on cr2.consensus_timestamp between irf2.consensus_start and irf2.consensus_end
                    where cr2.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  )
            ),
            evm_index as (
                select
                    ec.consensus_timestamp,
                    sum(case when ec.is_root then 1 else 0 end) over (
                        partition by irf.consensus_end
                        order by ec.consensus_timestamp
                    ) - 1 as evm_index
                from evm_candidates ec
                join contract_log_synthetic_backfill_impacted_files_temp irf
                    on ec.consensus_timestamp between irf.consensus_start and irf.consensus_end
            ),
            updated_contract_result as (
                update contract_result cr
                set transaction_index = ei.evm_index
                from evm_index ei
                where cr.consensus_timestamp = ei.consensus_timestamp
                  and cr.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cr.transaction_index is distinct from ei.evm_index
                returning cr.consensus_timestamp
            ),
            updated_contract_log as (
                update contract_log cl
                set transaction_index = ei.evm_index
                from evm_index ei
                where cl.consensus_timestamp = ei.consensus_timestamp
                  and cl.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cl.transaction_index is distinct from ei.evm_index
                returning cl.consensus_timestamp
            )
            select
                (select count(*) from updated_contract_result) as updated_results,
                (select count(*) from updated_contract_log) as updated_logs
            """;

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);
    private static final RowMapper<UpdateCounts> UPDATE_COUNTS_ROW_MAPPER =
            new DataClassRowMapper<>(UpdateCounts.class);
    private static final RowMapper<RecordFileIndexBounds> INDEX_BOUNDS_ROW_MAPPER =
            new DataClassRowMapper<>(RecordFileIndexBounds.class);
    private static final RowMapper<RecordFileAtIndex> RECORD_FILE_AT_INDEX_ROW_MAPPER =
            new DataClassRowMapper<>(RecordFileAtIndex.class);

    @Getter(lazy = true, value = AccessLevel.PROTECTED)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    private final long batchInterval;
    private final EntityProperties entityProperties;
    private final boolean v2;

    private long initialUpperBound = -1L;
    private long lowerBoundFloor = 0L;

    ContractLogSyntheticFlagBackfillMigration(
            EntityProperties entityProperties,
            Environment environment,
            ImporterProperties importerProperties,
            DBProperties dbProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
        v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        // The description ensures the migration runs after its two dependencies:
        // - ContractLogSyntheticBackfillMigration
        // - FixEvmTransactionIndexMigration
        return "Re-backfill synthetic flag and EVM transaction index for HAPI-origin contract log rows";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.29.0") : MigrationVersion.fromVersion("1.124.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var persistProperties = entityProperties.getPersist();
        if (!persistProperties.isContracts() || !persistProperties.isContractResults()) {
            return false;
        }

        final var maxConsensusEnd = getJdbcOperations().queryForObject(SELECT_MAX_CONSENSUS_END, Long.class);
        if (maxConsensusEnd == null) {
            log.info("No record files to process, skipping migration");
            return false;
        }

        lowerBoundFloor = calculateLowerBoundFloor();

        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);

        final var savedProgress = queryForObjectOrNull(SELECT_PROGRESS_UPPER_BOUND, Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusEnd;
        log.info(
                "Starting synthetic flag and EVM index backfill with initial timestamp: {}, lower bound floor: {}",
                initialUpperBound,
                lowerBoundFloor);
        return true;
    }

    private long calculateLowerBoundFloor() {
        final var installedOn = queryForObjectOrNull(SELECT_MIGRATION_INSTALLED_ON, Timestamp.class);
        if (installedOn == null) {
            return 0L;
        }

        final var thresholdMillis = installedOn.getTime();
        final var indexBounds =
                getJdbcOperations().queryForObject(SELECT_RECORD_FILE_INDEX_BOUNDS, INDEX_BOUNDS_ROW_MAPPER);
        if (indexBounds == null || indexBounds.minIndex() == null || indexBounds.maxIndex() == null) {
            return 0L;
        }

        long low = indexBounds.minIndex();
        long high = indexBounds.maxIndex();
        long floor = 0L;
        while (low <= high) {
            final long mid = low + (high - low) / 2;
            final var candidate = queryForObjectOrNull(
                    SELECT_RECORD_FILE_AT_OR_AFTER_INDEX,
                    new MapSqlParameterSource("index", mid).addValue("maxIndex", high),
                    RECORD_FILE_AT_INDEX_ROW_MAPPER);
            if (candidate == null) {
                // nothing in [mid, high], so narrow to [low, mid - 1]
                high = mid - 1;
            } else if (candidate.loadStart() >= thresholdMillis) {
                floor = candidate.consensusStart();
                high = candidate.index() - 1;
            } else {
                low = candidate.index() + 1;
            }
        }
        return floor;
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(@NonNull Long consensusEndTimestamp) {
        final var jdbcOperations = getJdbcOperations();
        final var namedParameterJdbcOperations = getNamedParameterJdbcOperations();

        final var consensusStartTimestamp = Math.max(consensusEndTimestamp - batchInterval, lowerBoundFloor);
        final var sliceParams = new MapSqlParameterSource()
                .addValue("consensusEndUpperBound", consensusEndTimestamp)
                .addValue("consensusEndLowerBound", consensusStartTimestamp);
        final var slice = queryForObjectOrNull(SELECT_RECORD_FILES_RANGE, sliceParams, ROW_MAPPER);

        if (slice == null || slice.minConsensusTimestamp() == null || slice.maxConsensusTimestamp() == null) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}.",
                    consensusEndTimestamp);
            jdbcOperations.execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", slice.minConsensusTimestamp())
                .addValue("lastConsensusEnd", slice.maxConsensusTimestamp());

        if (v2) {
            jdbcOperations.execute(SET_CITUS_LIMIT);
        }
        jdbcOperations.execute(CREATE_UPDATED_TIMESTAMPS_TEMP_TABLE);
        jdbcOperations.execute(CREATE_IMPACTED_RECORD_FILES_TEMP_TABLE);
        final var backfilledCount = Objects.requireNonNull(
                namedParameterJdbcOperations.queryForObject(BACKFILL_SYNTHETIC_SQL, params, Long.class));

        if (backfilledCount > 0) {
            namedParameterJdbcOperations.update(POPULATE_IMPACTED_RECORD_FILES_SQL, params);
            params.addValue("hookContractId", getHookContractId());
            final var counts = namedParameterJdbcOperations.queryForObject(
                    RECOMPUTE_EVM_TRANSACTION_INDEX_SQL, params, UPDATE_COUNTS_ROW_MAPPER);
            log.info(
                    """
                            Backfilled {} contract_log rows and fixed EVM transaction index for {} contract_result and
                            {} contract_log rows in range [{}, {}]
                            """,
                    backfilledCount,
                    counts.updatedResults(),
                    counts.updatedLogs(),
                    slice.minConsensusTimestamp(),
                    slice.maxConsensusTimestamp());
        }

        if (consensusStartTimestamp <= lowerBoundFloor) {
            log.info("Reached lower bound floor {}, stopping", lowerBoundFloor);
            jdbcOperations.execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        namedParameterJdbcOperations.update(
                CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", consensusStartTimestamp));
        return Optional.of(consensusStartTimestamp);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record RecordFileSlice(Long minConsensusTimestamp, Long maxConsensusTimestamp) {}

    private record UpdateCounts(long updatedResults, long updatedLogs) {}

    private record RecordFileIndexBounds(Long minIndex, Long maxIndex) {}

    private record RecordFileAtIndex(long consensusStart, long index, long loadStart) {}
}
