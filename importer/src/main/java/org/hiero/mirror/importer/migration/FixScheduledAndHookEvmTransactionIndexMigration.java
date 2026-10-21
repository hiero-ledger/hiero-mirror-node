// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
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
final class FixScheduledAndHookEvmTransactionIndexMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "1d";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";
    private static final int BLOCKS_PER_ITERATION = 20;

    private static final String DROP_TARGETS_TABLE = """
            drop table if exists fix_scheduled_hook_evm_index_targets_temp;
            """;

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists fix_scheduled_hook_evm_index_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists fix_scheduled_hook_evm_index_progress_temp;
            """;

    private static final String SELECT_MAX_CONSENSUS_END = "select max(consensus_end) from record_file";
    private static final String SELECT_MIN_CONSENSUS_END = "select min(consensus_end) from record_file";

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select (select upper_bound from fix_scheduled_hook_evm_index_progress_temp limit 1)";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from fix_scheduled_hook_evm_index_progress_temp)
            insert into fix_scheduled_hook_evm_index_progress_temp(upper_bound)
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

    private static final String FIND_AFFECTED_BLOCKS_SQL = """
            with slice_results as materialized (
                select consensus_timestamp, contract_id, transaction_nonce, gas_used
                from contract_result
                where consensus_timestamp between :consensusStart and :lastConsensusEnd
            ), no_index_parents as (
                select consensus_timestamp
                from slice_results
                where transaction_nonce = 0 and coalesce(gas_used, 0) = 0
            ), affected as (
                select consensus_timestamp
                from slice_results
                where contract_id = :hookContractId
                union
                select consensus_timestamp
                from slice_results
                where consensus_timestamp in
                      (select s.executed_timestamp from schedule s
                       where s.executed_timestamp between :consensusStart and :lastConsensusEnd)
                union
                -- Effects of a call without an index, which follow it by timestamp and nonce
                select consensus_timestamp
                from slice_results
                where transaction_nonce <> 0
                  and (consensus_timestamp - 1 in (select consensus_timestamp from no_index_parents)
                    or consensus_timestamp - transaction_nonce in (select consensus_timestamp from no_index_parents))
            )
            select distinct rf.consensus_start, rf.consensus_end
            from affected a
            join lateral (
                select consensus_start, consensus_end
                from record_file
                where consensus_end >= a.consensus_timestamp
                order by consensus_end
                limit 1
            ) rf on rf.consensus_start <= a.consensus_timestamp
            order by rf.consensus_end desc
            """;

    // %s is an OR of the batch's block ranges; one read per batch since each query fans out to every Citus shard
    private static final String SELECT_BLOCK_ROWS_SQL = """
            with t as (
                select consensus_timestamp, nonce, parent_consensus_timestamp, scheduled
                from transaction
                where consensus_timestamp between :minTimestamp and :maxTimestamp and (%1$s)
            ), cr as (
                select consensus_timestamp, contract_id, gas_used
                from contract_result
                where consensus_timestamp between :minTimestamp and :maxTimestamp and (%1$s)
            ), cl as (
                select distinct consensus_timestamp
                from contract_log
                where synthetic = true and consensus_timestamp between :minTimestamp and :maxTimestamp and (%1$s)
            )
            select
                t.consensus_timestamp,
                t.nonce,
                t.parent_consensus_timestamp,
                t.scheduled,
                cr.consensus_timestamp is not null as has_contract_result,
                coalesce(cr.contract_id, 0) as contract_id,
                coalesce(cr.gas_used, 0) as gas_used,
                cl.consensus_timestamp is not null and cr.consensus_timestamp is null as synthetic_log_only
            from t
            left join cr on cr.consensus_timestamp = t.consensus_timestamp
            left join cl on cl.consensus_timestamp = t.consensus_timestamp
            order by t.consensus_timestamp
            """;

    private static final String UPDATE_CONTRACT_RESULT_INDEX_SQL = """
            update contract_result cr set transaction_index = v.transaction_index
            from unnest(:consensusTimestamps::bigint[], :transactionIndexes::int[])
                as v(consensus_timestamp, transaction_index)
            where cr.consensus_timestamp = v.consensus_timestamp
              and cr.consensus_timestamp between :minTimestamp and :maxTimestamp
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEX_SQL = """
            update contract_log cl set transaction_index = v.transaction_index
            from unnest(:consensusTimestamps::bigint[], :transactionIndexes::int[])
                as v(consensus_timestamp, transaction_index)
            where cl.consensus_timestamp = v.consensus_timestamp
              and cl.consensus_timestamp between :minTimestamp and :maxTimestamp
            """;

    private static final RowMapper<Block> BLOCK_ROW_MAPPER = new DataClassRowMapper<>(Block.class);
    private static final RowMapper<EvmTransactionIndexCalculator.Row> ROW_MAPPER =
            new DataClassRowMapper<>(EvmTransactionIndexCalculator.Row.class);
    private static final RowMapper<RecordFileSlice> SLICE_ROW_MAPPER = new DataClassRowMapper<>(RecordFileSlice.class);

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
    private final boolean v2;
    private long initialUpperBound = -1L;
    private long lowerBound = Long.MAX_VALUE;

    FixScheduledAndHookEvmTransactionIndexMigration(
            Environment environment,
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            EntityProperties entityProperties) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
    }

    // Repeatable migrations run in description order; this must sort after RecomputeEvmTransactionIndexMigration's
    @Override
    public String getDescription() {
        return "Repair EVM transaction index for scheduled transactions, hook chains and effects of failed calls";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.128.0");
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

        jdbcOperations.execute(DROP_TARGETS_TABLE);
        jdbcOperations.execute(CREATE_PROGRESS_TABLE);

        final var savedProgress = jdbcOperations.queryForObject(SELECT_PROGRESS_UPPER_BOUND, Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusEnd;
        lowerBound = Objects.requireNonNull(jdbcOperations.queryForObject(SELECT_MIN_CONSENSUS_END, Long.class));
        log.info(
                "Starting EVM transaction index repair with initial timestamp: {}, lower bound: {}",
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

        if (v2) {
            getJdbcOperations().execute("set local citus.max_intermediate_result_size = -1");
        }

        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", slice.minConsensusTimestamp())
                .addValue("lastConsensusEnd", slice.maxConsensusTimestamp())
                .addValue("hookContractId", getHookContractId());
        final var blocks = getNamedParameterJdbcOperations().query(FIND_AFFECTED_BLOCKS_SQL, params, BLOCK_ROW_MAPPER);
        final long nextUpperBound = slice.minConsensusTimestamp() - 1;
        getNamedParameterJdbcOperations()
                .update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));

        for (int i = 0; i < blocks.size(); i += BLOCKS_PER_ITERATION) {
            repair(blocks.subList(i, Math.min(i + BLOCKS_PER_ITERATION, blocks.size())));
        }

        return Optional.of(nextUpperBound);
    }

    private RecordFileSlice getNextSlice(long sliceUpperBound) {
        while (sliceUpperBound >= lowerBound) {
            final var sliceLowerBound = sliceUpperBound - batchInterval;
            final var sliceParams = new MapSqlParameterSource()
                    .addValue("consensusEndUpperBound", sliceUpperBound)
                    .addValue("consensusEndLowerBound", sliceLowerBound);
            final var slice = queryForObjectOrNull(SELECT_RECORD_FILES_RANGE, sliceParams, SLICE_ROW_MAPPER);
            if (slice != null && slice.minConsensusTimestamp() != null && slice.maxConsensusTimestamp() != null) {
                return slice;
            }

            sliceUpperBound = sliceLowerBound;
        }

        return null;
    }

    private void repair(List<Block> blocks) {
        final var minTimestamp = blocks.getLast().consensusStart();
        final var maxTimestamp = blocks.getFirst().consensusEnd();
        final var readParams = new MapSqlParameterSource()
                .addValue("minTimestamp", minTimestamp)
                .addValue("maxTimestamp", maxTimestamp);
        final var blockRanges = new StringJoiner(" or ");
        for (int i = 0; i < blocks.size(); i++) {
            blockRanges.add("consensus_timestamp between :start%d and :end%d".formatted(i, i));
            readParams
                    .addValue("start" + i, blocks.get(i).consensusStart())
                    .addValue("end" + i, blocks.get(i).consensusEnd());
        }
        final var rows = getNamedParameterJdbcOperations()
                .query(SELECT_BLOCK_ROWS_SQL.formatted(blockRanges), readParams, ROW_MAPPER);

        // Rows are ordered by timestamp and blocks don't overlap, so each block's rows are a contiguous slice
        final var updates = new ArrayList<EvmTransactionIndexCalculator.Update>();
        int from = 0;
        for (final var block : blocks.reversed()) {
            int to = from;
            while (to < rows.size() && rows.get(to).consensusTimestamp() <= block.consensusEnd()) {
                to++;
            }
            updates.addAll(EvmTransactionIndexCalculator.compute(rows.subList(from, to), getHookContractId()));
            from = to;
        }

        if (!updates.isEmpty()) {
            final var updateParams = new MapSqlParameterSource()
                    .addValue(
                            "consensusTimestamps",
                            updates.stream()
                                    .map(EvmTransactionIndexCalculator.Update::consensusTimestamp)
                                    .toArray(Long[]::new))
                    .addValue(
                            "transactionIndexes",
                            updates.stream()
                                    .map(EvmTransactionIndexCalculator.Update::transactionIndex)
                                    .toArray(Long[]::new))
                    .addValue("minTimestamp", minTimestamp)
                    .addValue("maxTimestamp", maxTimestamp);
            getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_RESULT_INDEX_SQL, updateParams);
            getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_LOG_INDEX_SQL, updateParams);
            log.info("Recomputed {} evm_transaction_index values across {} blocks", updates.size(), blocks.size());
        }
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record Block(long consensusStart, long consensusEnd) {}

    private record RecordFileSlice(Long minConsensusTimestamp, Long maxConsensusTimestamp) {}
}
