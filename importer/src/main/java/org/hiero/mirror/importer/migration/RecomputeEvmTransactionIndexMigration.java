// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
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
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Named
final class RecomputeEvmTransactionIndexMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "3h";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String DROP_OLD_PROGRESS_TABLES = """
            drop table if exists fix_evm_transaction_index_progress_temp;
            drop table if exists contract_log_synthetic_progress_temp;
            """;

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists recompute_evm_transaction_index_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists recompute_evm_transaction_index_progress_temp;
            """;

    private static final String SELECT_MAX_CONSENSUS_END = "select max(consensus_end) from record_file";
    private static final String SELECT_MIN_CONSENSUS_END = "select min(consensus_end) from record_file";

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select (select upper_bound from recompute_evm_transaction_index_progress_temp limit 1)";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from recompute_evm_transaction_index_progress_temp)
            insert into recompute_evm_transaction_index_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String BACKFILL_SYNTHETIC_FLAG_SQL = """
            update contract_log
            set synthetic = true
            where synthetic is not true
              and consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :lastConsensusEnd
              and consensus_timestamp not in (
                select consensus_timestamp from contract_result
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :lastConsensusEnd
              )
            """;

    private static final String RECOMPUTE_EVM_TRANSACTION_INDEX_SQL = """
            with evm_candidates as (
                select
                    cr.consensus_timestamp,
                    (cr.transaction_nonce = 0 or cr.contract_id = :hookContractId) as is_root,
                    (
                        (cr.transaction_nonce <> 0 and cr.contract_id <> :hookContractId)
                        or cr.gas_used > 0
                    ) as qualifies
                from contract_result cr
                where cr.consensus_timestamp >= :consensusStart
                  and cr.consensus_timestamp <= :lastConsensusEnd
                union all
                -- avoids double-counting an already-covered timestamp
                select distinct
                    cl.consensus_timestamp,
                    true as is_root,
                    true as qualifies
                from contract_log cl
                where cl.synthetic = true
                  and cl.consensus_timestamp >= :consensusStart
                  and cl.consensus_timestamp <= :lastConsensusEnd
                  and cl.consensus_timestamp not in (
                    select consensus_timestamp from contract_result
                    where consensus_timestamp >= :consensusStart
                      and consensus_timestamp <= :lastConsensusEnd
                  )
            ),
            evm_index as (
                select
                    ec.consensus_timestamp,
                    case when ec.qualifies then
                        sum(case when ec.is_root and ec.qualifies then 1 else 0 end) over (
                            partition by rf.consensus_end
                            order by ec.consensus_timestamp
                        ) - 1
                    end as evm_index
                from evm_candidates ec
                join record_file rf
                    on ec.consensus_timestamp between rf.consensus_start and rf.consensus_end
                where rf.consensus_end between :consensusStart and :lastConsensusEnd
            ),
            updated_contract_result as (
                update contract_result cr
                set transaction_index = ei.evm_index
                from evm_index ei
                where cr.consensus_timestamp = ei.consensus_timestamp
                  and cr.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cr.transaction_index is distinct from ei.evm_index
                returning ei.evm_index
            ),
            updated_contract_log as (
                update contract_log cl
                set transaction_index = ei.evm_index
                from evm_index ei
                where cl.consensus_timestamp = ei.consensus_timestamp
                  and cl.consensus_timestamp between :consensusStart and :lastConsensusEnd
                  and cl.transaction_index is distinct from ei.evm_index
                returning ei.evm_index
            )
            select
                (select count(*) from updated_contract_result where evm_index is not null) as updated_results,
                (select count(*) from updated_contract_result where evm_index is null) as nulled_results,
                (select count(*) from updated_contract_log where evm_index is not null) as updated_logs,
                (select count(*) from updated_contract_log where evm_index is null) as nulled_logs
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

    private static final RowMapper<RecordFileSlice> ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);
    private static final RowMapper<UpdateCounts> UPDATE_COUNTS_ROW_MAPPER =
            new DataClassRowMapper<>(UpdateCounts.class);

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Getter(lazy = true)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    private final long batchInterval;
    private final EntityProperties entityProperties;
    private long initialUpperBound = -1L;
    private long lowerBound = Long.MAX_VALUE;

    RecomputeEvmTransactionIndexMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            EntityProperties entityProperties) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
    }

    @Override
    public String getDescription() {
        return "Recompute EVM transaction index using gasUsed and backfill contract_log synthetic flag";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.127.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var persistProperties = entityProperties.getPersist();
        if (!persistProperties.isContracts() || !persistProperties.isContractResults()) {
            return false;
        }

        final var jdbcOperations = getJdbcOperations();
        final var maxConsensusEnd = jdbcOperations.queryForObject(SELECT_MAX_CONSENSUS_END, Long.class);
        if (maxConsensusEnd == null) {
            log.info("No record files to process, skipping migration");
            return false;
        }

        jdbcOperations.execute(DROP_OLD_PROGRESS_TABLES);
        jdbcOperations.execute(CREATE_PROGRESS_TABLE);

        final var savedProgress = jdbcOperations.queryForObject(SELECT_PROGRESS_UPPER_BOUND, Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusEnd;
        lowerBound = Objects.requireNonNull(jdbcOperations.queryForObject(SELECT_MIN_CONSENSUS_END, Long.class));
        log.info(
                "Starting EVM transaction index recompute with initial timestamp: {}, lower bound: {}",
                initialUpperBound,
                lowerBound);
        return true;
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long consensusEndTimestamp) {
        final var slice = getNextSlice(consensusEndTimestamp);
        if (slice == null) {
            log.info(
                    "No more record files remaining to process. Last consensus end timestamp: {}.",
                    consensusEndTimestamp);
            getJdbcOperations().execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", slice.minConsensusTimestamp())
                .addValue("lastConsensusEnd", slice.maxConsensusTimestamp())
                .addValue("hookContractId", getHookContractId());

        final var backfilledSyntheticLogs =
                getNamedParameterJdbcOperations().update(BACKFILL_SYNTHETIC_FLAG_SQL, params);
        if (backfilledSyntheticLogs > 0) {
            log.info(
                    "Backfilled synthetic flag for {} contract_log rows in range [{}, {}]",
                    backfilledSyntheticLogs,
                    slice.minConsensusTimestamp(),
                    slice.maxConsensusTimestamp());
        }

        final var counts = getNamedParameterJdbcOperations()
                .queryForObject(RECOMPUTE_EVM_TRANSACTION_INDEX_SQL, params, UPDATE_COUNTS_ROW_MAPPER);

        if (counts.updatedResults() > 0
                || counts.nulledResults() > 0
                || counts.updatedLogs() > 0
                || counts.nulledLogs() > 0) {
            log.info(
                    "Recomputed EVM transaction index for {} contract_result and {} contract_log rows, nulled {}"
                            + " contract_result and {} contract_log rows in range [{}, {}]",
                    counts.updatedResults(),
                    counts.updatedLogs(),
                    counts.nulledResults(),
                    counts.nulledLogs(),
                    slice.minConsensusTimestamp(),
                    slice.maxConsensusTimestamp());
        }

        final long nextUpperBound = slice.minConsensusTimestamp() - 1;
        getNamedParameterJdbcOperations()
                .update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
    }

    private RecordFileSlice getNextSlice(long sliceUpperBound) {
        while (sliceUpperBound >= lowerBound) {
            final var sliceLowerBound = sliceUpperBound - batchInterval;
            final var sliceParams = new MapSqlParameterSource()
                    .addValue("consensusEndUpperBound", sliceUpperBound)
                    .addValue("consensusEndLowerBound", sliceLowerBound);
            final var slice = queryForObjectOrNull(SELECT_RECORD_FILES_RANGE, sliceParams, ROW_MAPPER);
            if (slice != null && slice.minConsensusTimestamp() != null && slice.maxConsensusTimestamp() != null) {
                return slice;
            }

            sliceUpperBound = sliceLowerBound;
        }

        return null;
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record RecordFileSlice(Long minConsensusTimestamp, Long maxConsensusTimestamp) {}

    private record UpdateCounts(long updatedResults, long nulledResults, long updatedLogs, long nulledLogs) {}
}
