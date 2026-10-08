// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
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

    private static final int BLOCKS_PER_ITERATION = 20;

    private static final String CREATE_TARGETS_TABLE = """
            create table if not exists fix_scheduled_hook_evm_index_targets_temp(
                consensus_start bigint not null,
                consensus_end bigint not null primary key
            );
            """;

    private static final String DROP_TARGETS_TABLE = """
            drop table if exists fix_scheduled_hook_evm_index_targets_temp;
            """;

    private static final String TARGETS_TABLE_IS_EMPTY =
            "select not exists(select 1 from fix_scheduled_hook_evm_index_targets_temp)";

    private static final String SELECT_MAX_TARGET_CONSENSUS_END =
            "select max(consensus_end) from fix_scheduled_hook_evm_index_targets_temp";

    private static final String FIND_AFFECTED_BLOCKS_SQL = """
            insert into fix_scheduled_hook_evm_index_targets_temp(consensus_start, consensus_end)
            with affected as (
                select cr.consensus_timestamp
                from contract_result cr
                where cr.contract_id = :hookContractId
                union
                select cr.consensus_timestamp
                from contract_result cr
                where cr.consensus_timestamp in
                      (select s.executed_timestamp from schedule s where s.executed_timestamp is not null)
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
            on conflict (consensus_end) do nothing
            """;

    private static final String SELECT_NEXT_TARGET_BLOCKS = """
            select consensus_start, consensus_end
            from fix_scheduled_hook_evm_index_targets_temp
            where consensus_end < :cursor
            order by consensus_end desc
            limit :limit
            """;

    // %s is an OR of the batch's block ranges; one read per batch since each query fans out to every Citus shard
    private static final String SELECT_BLOCK_ROWS_SQL = """
            with t as (
                select consensus_timestamp, parent_consensus_timestamp
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
                t.parent_consensus_timestamp,
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

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    @Getter(lazy = true)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    private final EntityProperties entityProperties;
    private final boolean v2;

    FixScheduledAndHookEvmTransactionIndexMigration(
            Environment environment,
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            EntityProperties entityProperties) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    // Repeatable migrations run in description order; this must sort after RecomputeEvmTransactionIndexMigration's
    @Override
    public String getDescription() {
        return "Repair EVM transaction index for scheduled transactions and hook chains";
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
        jdbcOperations.execute(CREATE_TARGETS_TABLE);

        final var isEmpty = Boolean.TRUE.equals(jdbcOperations.queryForObject(TARGETS_TABLE_IS_EMPTY, Boolean.class));
        if (isEmpty) {
            final var found = getTransactionOperations().execute(status -> {
                if (v2) {
                    jdbcOperations.execute("set local citus.max_intermediate_result_size = -1");
                }
                final var params = new MapSqlParameterSource("hookContractId", getHookContractId());
                return getNamedParameterJdbcOperations().update(FIND_AFFECTED_BLOCKS_SQL, params);
            });
            log.info("Found {} blocks affected by scheduled transactions or hook chains", found);
            if (found == null || found == 0) {
                jdbcOperations.execute(DROP_TARGETS_TABLE);
                return false;
            }
        }

        return true;
    }

    @NonNull
    @Override
    protected Long getInitial() {
        final var max = getJdbcOperations().queryForObject(SELECT_MAX_TARGET_CONSENSUS_END, Long.class);
        return max != null ? max + 1 : 0L;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long cursor) {
        final var params =
                new MapSqlParameterSource().addValue("cursor", cursor).addValue("limit", BLOCKS_PER_ITERATION);
        final var blocks = getNamedParameterJdbcOperations().query(SELECT_NEXT_TARGET_BLOCKS, params, BLOCK_ROW_MAPPER);

        if (blocks.isEmpty()) {
            getJdbcOperations().execute(DROP_TARGETS_TABLE);
            log.info("No more affected blocks remaining to process");
            return Optional.empty();
        }

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

        return Optional.of(blocks.getLast().consensusEnd());
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record Block(long consensusStart, long consensusEnd) {}
}
