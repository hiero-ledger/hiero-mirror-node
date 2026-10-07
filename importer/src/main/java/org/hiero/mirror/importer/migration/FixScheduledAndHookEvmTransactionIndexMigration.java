// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
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

    private static final String SELECT_BLOCK_ROWS_SQL = """
            select
                consensus_timestamp,
                max(parent_consensus_timestamp) as parent_consensus_timestamp,
                bool_or(has_contract_result) as has_contract_result,
                coalesce(max(contract_id), 0) as contract_id,
                coalesce(max(gas_used), 0) as gas_used,
                bool_or(has_synthetic_log) and not bool_or(has_contract_result) as synthetic_log_only,
                case when bool_or(has_contract_result) then max(contract_result_index)
                     else max(contract_log_index) end as transaction_index
            from (
                select consensus_timestamp, parent_consensus_timestamp, false as has_contract_result,
                    null::bigint as contract_id, null::bigint as gas_used, null::int as contract_result_index,
                    false as has_synthetic_log, null::int as contract_log_index
                from transaction
                where consensus_timestamp between :consensusStart and :consensusEnd
                union all
                select consensus_timestamp, null::bigint, true, contract_id, gas_used, transaction_index, false,
                    null::int
                from contract_result
                where consensus_timestamp between :consensusStart and :consensusEnd
                union all
                select distinct consensus_timestamp, null::bigint, false, null::bigint, null::bigint, null::int, true,
                    transaction_index
                from contract_log
                where synthetic = true and consensus_timestamp between :consensusStart and :consensusEnd
            ) block_rows
            group by consensus_timestamp
            order by consensus_timestamp
            """;

    private static final String UPDATE_CONTRACT_RESULT_INDEX_SQL = """
            update contract_result set transaction_index = :transactionIndex
            where consensus_timestamp = :consensusTimestamp
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEX_SQL = """
            update contract_log set transaction_index = :transactionIndex
            where consensus_timestamp = :consensusTimestamp
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

    // Rpeatable migrations run in description order; this must sort after RecomputeEvmTransactionIndexMigration's
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
                    jdbcOperations.execute("set citus.max_intermediate_result_size = -1");
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

        final var updates = new ArrayList<EvmTransactionIndexCalculator.Update>();
        for (final var block : blocks) {
            final var blockParams = new MapSqlParameterSource()
                    .addValue("consensusStart", block.consensusStart())
                    .addValue("consensusEnd", block.consensusEnd());
            final var rows = getNamedParameterJdbcOperations().query(SELECT_BLOCK_ROWS_SQL, blockParams, ROW_MAPPER);
            updates.addAll(EvmTransactionIndexCalculator.compute(rows, getHookContractId()));
        }

        if (!updates.isEmpty()) {
            final var batch =
                    updates.stream().map(SimplePropertySqlParameterSource::new).toArray(SqlParameterSource[]::new);
            getNamedParameterJdbcOperations().batchUpdate(UPDATE_CONTRACT_RESULT_INDEX_SQL, batch);
            getNamedParameterJdbcOperations().batchUpdate(UPDATE_CONTRACT_LOG_INDEX_SQL, batch);
            log.info("Fixed {} evm_transaction_index values across {} blocks", updates.size(), blocks.size());
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
