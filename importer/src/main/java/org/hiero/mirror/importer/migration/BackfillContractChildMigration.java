// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.LongStream;
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
final class BackfillContractChildMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "3h";

    // 2021-11-01T00:00:00Z, parent_consensus_timestamp was introduced with HAPI v0.21 so nothing earlier is a child
    static final long PARENT_CONSENSUS_TIMESTAMP_START = 1635724800000000000L;

    private static final String BACKFILL_CHILD_SQL = """
            with child_timestamp as (
              select unnest(cast(:childTimestamps as bigint[])) as consensus_timestamp
            ), updated_contract_result as (
              update contract_result
              set child = true
              where child is not true
                and consensus_timestamp >= :consensusStart
                and consensus_timestamp <= :consensusEnd
                and consensus_timestamp in (select consensus_timestamp from child_timestamp)
              returning consensus_timestamp
            ), updated_contract_log as (
              update contract_log
              set child = true
              where child is not true
                and consensus_timestamp >= :consensusStart
                and consensus_timestamp <= :consensusEnd
                and consensus_timestamp in (select consensus_timestamp from child_timestamp)
              returning consensus_timestamp
            )
            select
              (select count(*) from updated_contract_result) as updated_results,
              (select count(*) from updated_contract_log) as updated_logs
            """;

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from backfill_contract_child_progress_temp)
            insert into backfill_contract_child_progress_temp(upper_bound) values (:upperBound)
            """;

    private static final RowMapper<ContractResultRow> CONTRACT_RESULT_ROW_MAPPER =
            new DataClassRowMapper<>(ContractResultRow.class);

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists backfill_contract_child_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists backfill_contract_child_progress_temp;
            """;

    // Scheduled executions have no parent but sit between their trigger and the trigger's children
    private static final String SELECT_CHILD_AND_SCHEDULED_TRANSACTIONS = """
            select consensus_timestamp, nonce, parent_consensus_timestamp, scheduled
            from transaction
            where consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :consensusEnd
              and (parent_consensus_timestamp is not null or scheduled)
            """;

    private static final String SELECT_CONTRACT_RESULTS = """
            select consensus_timestamp, contract_id, coalesce(gas_used, 0) as gas_used
            from contract_result
            where consensus_timestamp >= :minTimestamp
              and consensus_timestamp <= :maxTimestamp
              and consensus_timestamp = any(cast(:timestamps as bigint[]))
            """;

    private static final String SELECT_MAX_CONSENSUS_TIMESTAMP = """
            select greatest(
              (select max(consensus_timestamp) from contract_result),
              (select max(consensus_timestamp) from contract_log)
            )
            """;

    private static final String SELECT_MIN_CONSENSUS_TIMESTAMP = """
            select least(
              (select min(consensus_timestamp) from contract_result),
              (select min(consensus_timestamp) from contract_log)
            )
            """;

    private static final String SELECT_PROGRESS_UPPER_BOUND =
            "select upper_bound from backfill_contract_child_progress_temp limit 1";

    private static final String SELECT_TRANSACTIONS_BY_TIMESTAMP = """
            select consensus_timestamp, nonce, parent_consensus_timestamp, scheduled
            from transaction
            where consensus_timestamp >= :minTimestamp
              and consensus_timestamp <= :maxTimestamp
              and consensus_timestamp = any(cast(:timestamps as bigint[]))
            """;

    private static final RowMapper<TransactionRow> TRANSACTION_ROW_MAPPER =
            new DataClassRowMapper<>(TransactionRow.class);

    private static final RowMapper<UpdateCounts> UPDATE_COUNTS_ROW_MAPPER =
            new DataClassRowMapper<>(UpdateCounts.class);

    private final long batchInterval;
    private final EntityProperties entityProperties;

    @Getter(lazy = true)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    private long initialUpperBound = -1L;
    private long lowerBound = Long.MAX_VALUE;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final boolean v2;

    BackfillContractChildMigration(
            final DBProperties dbProperties,
            final EntityProperties entityProperties,
            final Environment environment,
            final ImporterProperties importerProperties,
            final @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        this.batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
    }

    @Override
    public String getDescription() {
        return "Backfill contract_result and contract_log child column for rows sharing another EVM transaction";
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.35.0") : MigrationVersion.fromVersion("1.130.0");
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(final Long consensusEnd) {
        if (consensusEnd < lowerBound) {
            log.info("Completed contract child column backfill");
            getJdbcOperations().execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        final long consensusStart = Math.max(consensusEnd - batchInterval + 1, lowerBound);
        final var childTimestamps = findChildTimestamps(consensusStart, consensusEnd);
        if (!childTimestamps.isEmpty()) {
            final var params = new MapSqlParameterSource()
                    .addValue("childTimestamps", toArray(childTimestamps))
                    .addValue("consensusStart", consensusStart)
                    .addValue("consensusEnd", consensusEnd);
            final var counts = getNamedParameterJdbcOperations()
                    .queryForObject(BACKFILL_CHILD_SQL, params, UPDATE_COUNTS_ROW_MAPPER);
            if (counts != null && (counts.updatedResults() > 0 || counts.updatedLogs() > 0)) {
                log.info(
                        "Backfilled child column for {} contract_result and {} contract_log rows in range [{}, {}]",
                        counts.updatedResults(),
                        counts.updatedLogs(),
                        consensusStart,
                        consensusEnd);
            }
        }

        final long nextUpperBound = consensusStart - 1;
        getNamedParameterJdbcOperations()
                .update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
    }

    @Override
    protected boolean performSynchronousSteps() {
        if (!entityProperties.getPersist().isContractResults()) {
            return false;
        }

        final var jdbcOperations = getJdbcOperations();
        final var maxConsensusTimestamp = jdbcOperations.queryForObject(SELECT_MAX_CONSENSUS_TIMESTAMP, Long.class);
        if (maxConsensusTimestamp == null || maxConsensusTimestamp < PARENT_CONSENSUS_TIMESTAMP_START) {
            log.info("No contract results or logs to process, skipping migration");
            return false;
        }

        jdbcOperations.execute(CREATE_PROGRESS_TABLE);
        final var savedProgress =
                queryForObjectOrNull(SELECT_PROGRESS_UPPER_BOUND, new MapSqlParameterSource(), Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusTimestamp;
        final long minConsensusTimestamp =
                Objects.requireNonNull(jdbcOperations.queryForObject(SELECT_MIN_CONSENSUS_TIMESTAMP, Long.class));
        lowerBound = Math.max(minConsensusTimestamp, PARENT_CONSENSUS_TIMESTAMP_START);
        log.info(
                "Starting contract child column backfill with initial timestamp: {}, lower bound: {}",
                initialUpperBound,
                lowerBound);
        return true;
    }

    private void findChildAndScheduledTransactions(
            final long consensusStart, final long consensusEnd, final TreeMap<Long, TransactionRow> transactions) {
        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", consensusStart)
                .addValue("consensusEnd", consensusEnd);
        for (final var transaction : getNamedParameterJdbcOperations()
                .query(SELECT_CHILD_AND_SCHEDULED_TRANSACTIONS, params, TRANSACTION_ROW_MAPPER)) {
            transactions.put(transaction.consensusTimestamp(), transaction);
        }
    }

    // Loads every row EvmTransactionIndexCalculator walks to resolve a contract related parent: children, scheduled
    // executions, earlier siblings and the parents themselves
    private List<Long> findChildTimestamps(final long consensusStart, final long consensusEnd) {
        final var transactions = new TreeMap<Long, TransactionRow>();
        findChildAndScheduledTransactions(consensusStart, consensusEnd, transactions);

        final var minParent = transactions.values().stream()
                .map(TransactionRow::parentConsensusTimestamp)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .min();
        if (minParent.isEmpty()) {
            return List.of();
        }

        final long minParentTimestamp = minParent.getAsLong();
        if (minParentTimestamp + 1 < consensusStart) {
            findChildAndScheduledTransactions(minParentTimestamp + 1, consensusStart - 1, transactions);
        }

        final var parentTimestamps = new ArrayList<Long>();
        for (final var transaction : transactions.values()) {
            final var parentTimestamp = transaction.parentConsensusTimestamp();
            if (parentTimestamp != null && !transactions.containsKey(parentTimestamp)) {
                parentTimestamps.add(parentTimestamp);
            }
        }

        if (!parentTimestamps.isEmpty()) {
            for (final var parent : getNamedParameterJdbcOperations()
                    .query(
                            SELECT_TRANSACTIONS_BY_TIMESTAMP,
                            timestampParams(parentTimestamps),
                            TRANSACTION_ROW_MAPPER)) {
                transactions.put(parent.consensusTimestamp(), parent);
            }
        }

        final var contractResults = new HashMap<Long, ContractResultRow>();
        for (final var contractResult : getNamedParameterJdbcOperations()
                .query(SELECT_CONTRACT_RESULTS, timestampParams(transactions.keySet()), CONTRACT_RESULT_ROW_MAPPER)) {
            contractResults.put(contractResult.consensusTimestamp(), contractResult);
        }

        final var rows = new ArrayList<EvmTransactionIndexCalculator.Row>(transactions.size());
        for (final var transaction : transactions.values()) {
            final var contractResult = contractResults.get(transaction.consensusTimestamp());
            rows.add(new EvmTransactionIndexCalculator.Row(
                    transaction.consensusTimestamp(),
                    transaction.parentConsensusTimestamp(),
                    contractResult != null,
                    contractResult != null ? contractResult.contractId() : 0L,
                    contractResult != null ? contractResult.gasUsed() : 0L,
                    false,
                    transaction.scheduled(),
                    transaction.nonce()));
        }

        return EvmTransactionIndexCalculator.findChildren(rows, getHookContractId()).stream()
                .filter(timestamp -> timestamp >= consensusStart)
                .toList();
    }

    // Bounding the lookup by its min and max timestamp lets the query skip the other time partitions
    private MapSqlParameterSource timestampParams(final Collection<Long> timestamps) {
        final var values = toArray(timestamps);
        final var range = LongStream.of(values).summaryStatistics();
        return new MapSqlParameterSource()
                .addValue("maxTimestamp", range.getMax())
                .addValue("minTimestamp", range.getMin())
                .addValue("timestamps", values);
    }

    private long[] toArray(final Collection<Long> timestamps) {
        return timestamps.stream().mapToLong(Long::longValue).toArray();
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record ContractResultRow(long consensusTimestamp, long contractId, long gasUsed) {}

    private record TransactionRow(
            long consensusTimestamp, int nonce, @Nullable Long parentConsensusTimestamp, boolean scheduled) {}

    private record UpdateCounts(long updatedResults, long updatedLogs) {}
}
