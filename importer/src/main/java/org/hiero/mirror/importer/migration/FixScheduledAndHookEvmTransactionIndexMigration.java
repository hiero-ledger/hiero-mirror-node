// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final String DELETE_PROCESSED_TARGET_BLOCKS = """
            delete from fix_scheduled_hook_evm_index_targets_temp where consensus_end = :consensusEnd
            """;

    private static final String SELECT_TRANSACTIONS_SQL = """
            select consensus_timestamp, parent_consensus_timestamp
            from transaction
            where consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String SELECT_CONTRACT_RESULTS_SQL = """
            select consensus_timestamp, contract_id, gas_used, transaction_index
            from contract_result
            where consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String SELECT_SYNTHETIC_CONTRACT_LOGS_SQL = """
            select consensus_timestamp, coalesce(root_contract_id, contract_id) as contract_id, transaction_index
            from contract_log
            where synthetic = true and consensus_timestamp between :consensusStart and :consensusEnd
            """;

    private static final String UPDATE_CONTRACT_RESULT_INDEX_SQL = """
            update contract_result set transaction_index = :evmIndex where consensus_timestamp = :consensusTimestamp
            """;

    private static final String UPDATE_CONTRACT_LOG_INDEX_SQL = """
            update contract_log set transaction_index = :evmIndex where consensus_timestamp = :consensusTimestamp
            """;

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

    @Override
    public String getDescription() {
        return "Fix EVM transaction index for scheduled transactions and hook chains";
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
        final var blocks = getNamedParameterJdbcOperations()
                .query(
                        SELECT_NEXT_TARGET_BLOCKS,
                        params,
                        (rs, rowNum) -> new Block(rs.getLong("consensus_start"), rs.getLong("consensus_end")));

        if (blocks.isEmpty()) {
            getJdbcOperations().execute(DROP_TARGETS_TABLE);
            log.info("No more affected blocks remaining to process");
            return Optional.empty();
        }

        long updatedCount = 0;
        for (final var block : blocks) {
            updatedCount += fixBlock(block);
            getNamedParameterJdbcOperations()
                    .update(
                            DELETE_PROCESSED_TARGET_BLOCKS,
                            new MapSqlParameterSource("consensusEnd", block.consensusEnd()));
        }

        if (updatedCount > 0) {
            log.info("Fixed {} evm_transaction_index values across {} blocks", updatedCount, blocks.size());
        }

        final var nextCursor = blocks.get(blocks.size() - 1).consensusEnd();
        return Optional.of(nextCursor);
    }

    private static @Nullable Long asLong(@Nullable Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }

    private @Nullable Long currentIndex(DbRow dbRow) {
        return dbRow.hasContractResult ? dbRow.crTransactionIndex : dbRow.clTransactionIndex;
    }

    private long fixBlock(Block block) {
        final var rowParams = new MapSqlParameterSource()
                .addValue("consensusStart", block.consensusStart())
                .addValue("consensusEnd", block.consensusEnd());

        final var transactions = getNamedParameterJdbcOperations()
                .query(
                        SELECT_TRANSACTIONS_SQL,
                        rowParams,
                        (rs, rowNum) -> new TransactionRow(
                                rs.getLong("consensus_timestamp"), asLong(rs.getObject("parent_consensus_timestamp"))));
        final var contractResultsByTimestamp = getNamedParameterJdbcOperations()
                .query(
                        SELECT_CONTRACT_RESULTS_SQL,
                        rowParams,
                        (rs, rowNum) -> new ContractResultRow(
                                rs.getLong("consensus_timestamp"),
                                rs.getLong("contract_id"),
                                asLong(rs.getObject("gas_used")),
                                asLong(rs.getObject("transaction_index"))))
                .stream()
                .collect(Collectors.toMap(ContractResultRow::consensusTimestamp, r -> r));
        final var syntheticLogsByTimestamp = getNamedParameterJdbcOperations()
                .query(
                        SELECT_SYNTHETIC_CONTRACT_LOGS_SQL,
                        rowParams,
                        (rs, rowNum) -> new SyntheticLogRow(
                                rs.getLong("consensus_timestamp"),
                                rs.getLong("contract_id"),
                                asLong(rs.getObject("transaction_index"))))
                .stream()
                .collect(Collectors.toMap(SyntheticLogRow::consensusTimestamp, r -> r, (first, second) -> first));

        final var dbRows = transactions.stream()
                .map(transaction -> toDbRow(
                        transaction,
                        contractResultsByTimestamp.get(transaction.consensusTimestamp()),
                        syntheticLogsByTimestamp.get(transaction.consensusTimestamp())))
                .sorted(Comparator.comparingLong(DbRow::consensusTimestamp))
                .toList();

        final var calculatorRows = dbRows.stream().map(DbRow::toCalculatorRow).toList();
        final var results = EvmTransactionIndexCalculator.compute(calculatorRows, getHookContractId());

        final var dbRowsByTimestamp = dbRows.stream().collect(Collectors.toMap(DbRow::consensusTimestamp, r -> r));

        long updated = 0;
        for (final var result : results) {
            final var dbRow = dbRowsByTimestamp.get(result.consensusTimestamp());
            if (!Objects.equals(result.evmIndex(), currentIndex(dbRow))) {
                updateIndex(dbRow, result.evmIndex());
                updated++;
            }
        }
        return updated;
    }

    private static DbRow toDbRow(
            TransactionRow transaction,
            @Nullable ContractResultRow contractResult,
            @Nullable SyntheticLogRow syntheticLog) {
        return new DbRow(
                transaction.consensusTimestamp(),
                transaction.parentConsensusTimestamp(),
                contractResult != null,
                contractResult != null ? contractResult.contractId() : null,
                contractResult != null ? contractResult.gasUsed() : null,
                contractResult != null ? contractResult.transactionIndex() : null,
                syntheticLog != null,
                syntheticLog != null ? syntheticLog.contractId() : null,
                syntheticLog != null ? syntheticLog.transactionIndex() : null);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private void updateIndex(DbRow dbRow, @Nullable Long evmIndex) {
        final var params = new MapSqlParameterSource()
                .addValue("evmIndex", evmIndex)
                .addValue("consensusTimestamp", dbRow.consensusTimestamp);
        if (dbRow.hasContractResult) {
            getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_RESULT_INDEX_SQL, params);
        }
        getNamedParameterJdbcOperations().update(UPDATE_CONTRACT_LOG_INDEX_SQL, params);
    }

    private record Block(long consensusStart, long consensusEnd) {}

    private record TransactionRow(
            long consensusTimestamp, @Nullable Long parentConsensusTimestamp) {}

    private record ContractResultRow(
            long consensusTimestamp,
            long contractId,
            @Nullable Long gasUsed,
            @Nullable Long transactionIndex) {}

    private record SyntheticLogRow(
            long consensusTimestamp,
            long contractId,
            @Nullable Long transactionIndex) {}

    private record DbRow(
            long consensusTimestamp,
            @Nullable Long parentConsensusTimestamp,
            boolean hasContractResult,
            @Nullable Long crContractId,
            @Nullable Long gasUsed,
            @Nullable Long crTransactionIndex,
            boolean hasSyntheticLog,
            @Nullable Long clContractId,
            @Nullable Long clTransactionIndex) {

        EvmTransactionIndexCalculator.Row toCalculatorRow() {
            final var syntheticLogOnly = hasSyntheticLog && !hasContractResult;
            final long contractId = hasContractResult ? crContractId : syntheticLogOnly ? clContractId : 0L;
            return new EvmTransactionIndexCalculator.Row(
                    consensusTimestamp,
                    parentConsensusTimestamp,
                    hasContractResult,
                    contractId,
                    hasContractResult && gasUsed != null ? gasUsed : 0L,
                    syntheticLogOnly);
        }
    }
}
