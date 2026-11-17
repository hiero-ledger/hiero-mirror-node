// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.collect.Lists;
import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
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
    static final String DEFAULT_BATCH_SIZE = "50000";

    // 2021-11-01T00:00:00Z, parent_consensus_timestamp was introduced with HAPI v0.21 so nothing earlier is a child
    static final long PARENT_CONSENSUS_TIMESTAMP_START = 1635724800000000000L;

    // Before hooks and contract scheduled calls, a contract result is a child exactly when its nonce is above 0
    private static final String BACKFILL_CHILD_BY_NONCE_SQL = """
            update contract_result
            set child = true
            where consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :consensusEnd
              and transaction_nonce > 0
              and not child
            """;

    private static final String BACKFILL_CHILD_BY_PARENT_SQL = """
            update contract_result
            set child = true
            where consensus_timestamp >= :minTimestamp
              and consensus_timestamp <= :maxTimestamp
              and not child
              and consensus_timestamp = any(cast(:timestamps as bigint[]))
            """;

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String BATCH_SIZE_PROPERTIES_KEY = "batchSize";

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

    // The schedule service system contract, 0x16b
    private static final long SCHEDULE_CONTRACT_NUM = 363L;

    // Scheduled executions have no parent but sit between their trigger and the trigger's children
    private static final String SELECT_CHILD_AND_SCHEDULED_TRANSACTIONS = """
            select consensus_timestamp, nonce, parent_consensus_timestamp, scheduled
            from transaction
            where consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :consensusEnd
              and (parent_consensus_timestamp is not null or scheduled)
            order by consensus_timestamp desc
            limit :limit
            """;

    private static final String SELECT_CONTRACT_RESULTS = """
            select consensus_timestamp, contract_id, coalesce(gas_used, 0) as gas_used
            from contract_result
            where consensus_timestamp >= :minTimestamp
              and consensus_timestamp <= :maxTimestamp
              and consensus_timestamp = any(cast(:timestamps as bigint[]))
            """;

    // Hook executions and calls scheduled by a contract are child records that aren't a child of an EVM transaction
    private static final String SELECT_FIRST_HOOK_OR_SCHEDULE_CONTRACT_RESULT = """
            select least(
              (select min(consensus_timestamp) from contract_result where contract_id = :hookContractId),
              (select min(consensus_timestamp) from contract_result where contract_id = :scheduleContractId)
            )
            """;

    private static final String SELECT_MAX_CONSENSUS_TIMESTAMP = "select max(consensus_timestamp) from contract_result";

    private static final String SELECT_MIN_CONSENSUS_TIMESTAMP = "select min(consensus_timestamp) from contract_result";

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

    private final long batchInterval;
    private int batchSize;
    private final EntityProperties entityProperties;
    private long firstHookOrScheduleTimestamp = Long.MAX_VALUE;

    @Getter(lazy = true)
    private final long hookContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    RecordItem.HOOK_CONTRACT_NUM)
            .getId();

    @Getter(lazy = true)
    private final long scheduleContractId = EntityId.of(
                    CommonProperties.getInstance().getShard(),
                    CommonProperties.getInstance().getRealm(),
                    SCHEDULE_CONTRACT_NUM)
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
        final var jdbcOperations = getNamedParameterJdbcOperations();
        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", consensusStart)
                .addValue("consensusEnd", consensusEnd);
        final int updated;
        if (consensusEnd < firstHookOrScheduleTimestamp) {
            updated = jdbcOperations.update(BACKFILL_CHILD_BY_NONCE_SQL, params);
        } else {
            updated = backfillChildByParent(consensusStart, consensusEnd);
        }

        if (updated > 0) {
            log.info(
                    "Backfilled child column for {} contract_result rows in range [{}, {}]",
                    updated,
                    consensusStart,
                    consensusEnd);
        }

        final long nextUpperBound = consensusStart - 1;
        jdbcOperations.update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
    }

    @Override
    protected boolean performSynchronousSteps() {
        if (!entityProperties.getPersist().isContractResults()) {
            return false;
        }

        batchSize = Integer.parseInt(
                migrationProperties.getParams().getOrDefault(BATCH_SIZE_PROPERTIES_KEY, DEFAULT_BATCH_SIZE));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive batch size " + batchSize);
        }

        final var jdbcOperations = getJdbcOperations();
        final var maxConsensusTimestamp = jdbcOperations.queryForObject(SELECT_MAX_CONSENSUS_TIMESTAMP, Long.class);
        if (maxConsensusTimestamp == null || maxConsensusTimestamp < PARENT_CONSENSUS_TIMESTAMP_START) {
            log.info("No contract results to process, skipping migration");
            return false;
        }

        jdbcOperations.execute(CREATE_PROGRESS_TABLE);
        final var savedProgress =
                queryForObjectOrNull(SELECT_PROGRESS_UPPER_BOUND, new MapSqlParameterSource(), Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusTimestamp;
        final long minConsensusTimestamp =
                Objects.requireNonNull(jdbcOperations.queryForObject(SELECT_MIN_CONSENSUS_TIMESTAMP, Long.class));
        lowerBound = Math.max(minConsensusTimestamp, PARENT_CONSENSUS_TIMESTAMP_START);
        final var firstHookOrSchedule = queryForObjectOrNull(
                SELECT_FIRST_HOOK_OR_SCHEDULE_CONTRACT_RESULT,
                new MapSqlParameterSource()
                        .addValue("hookContractId", getHookContractId())
                        .addValue("scheduleContractId", getScheduleContractId()),
                Long.class);
        firstHookOrScheduleTimestamp = firstHookOrSchedule != null ? firstHookOrSchedule : Long.MAX_VALUE;
        log.info(
                "Starting contract child backfill with initial timestamp: {}, lower bound: {}, walking parents from {}",
                initialUpperBound,
                lowerBound,
                firstHookOrScheduleTimestamp);
        return true;
    }

    // Walks the window one page of child and scheduled transactions at a time, so each page's rows are released before
    // the next one loads
    private int backfillChildByParent(final long consensusStart, final long consensusEnd) {
        int updatedResults = 0;
        long pageEnd = consensusEnd;
        while (pageEnd >= consensusStart) {
            final var transactions = new TreeMap<Long, TransactionRow>();
            final long pageStart = findChildAndScheduledTransactions(consensusStart, pageEnd, transactions);
            for (final var chunk : Lists.partition(findChildTimestamps(pageStart, transactions), batchSize)) {
                updatedResults +=
                        getNamedParameterJdbcOperations().update(BACKFILL_CHILD_BY_PARENT_SQL, timestampParams(chunk));
            }

            pageEnd = pageStart - 1;
        }

        return updatedResults;
    }

    // Loads at most batchSize rows from the top of the range and returns the lowest timestamp the loaded rows cover
    private long findChildAndScheduledTransactions(
            final long consensusStart, final long consensusEnd, final TreeMap<Long, TransactionRow> transactions) {
        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", consensusStart)
                .addValue("consensusEnd", consensusEnd)
                .addValue("limit", batchSize);
        final var page = getNamedParameterJdbcOperations()
                .query(SELECT_CHILD_AND_SCHEDULED_TRANSACTIONS, params, TRANSACTION_ROW_MAPPER);
        for (final var transaction : page) {
            transactions.put(transaction.consensusTimestamp(), transaction);
        }

        return page.size() < batchSize ? consensusStart : page.getLast().consensusTimestamp();
    }

    // Loads every row EvmTransactionIndexCalculator walks to resolve a contract related parent: children, scheduled
    // executions, earlier siblings and the parents themselves
    private List<Long> findChildTimestamps(
            final long consensusStart, final TreeMap<Long, TransactionRow> transactions) {
        final var minParent = transactions.values().stream()
                .map(TransactionRow::parentConsensusTimestamp)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .min();
        if (minParent.isEmpty()) {
            return List.of();
        }

        final long extensionStart = minParent.getAsLong() + 1;
        long loadedFrom = consensusStart;
        while (loadedFrom > extensionStart) {
            loadedFrom = findChildAndScheduledTransactions(extensionStart, loadedFrom - 1, transactions);
        }

        final var parentTimestamps = new TreeSet<Long>();
        for (final var transaction : transactions.values()) {
            final var parentTimestamp = transaction.parentConsensusTimestamp();
            if (parentTimestamp != null && !transactions.containsKey(parentTimestamp)) {
                parentTimestamps.add(parentTimestamp);
            }
        }

        queryByTimestamps(
                SELECT_TRANSACTIONS_BY_TIMESTAMP,
                new ArrayList<>(parentTimestamps),
                TRANSACTION_ROW_MAPPER,
                parent -> transactions.put(parent.consensusTimestamp(), parent));

        final var contractResults = new HashMap<Long, ContractResultRow>();
        queryByTimestamps(
                SELECT_CONTRACT_RESULTS,
                new ArrayList<>(transactions.keySet()),
                CONTRACT_RESULT_ROW_MAPPER,
                contractResult -> contractResults.put(contractResult.consensusTimestamp(), contractResult));

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

    // Splits the sorted timestamps to chunks capped at batchSize elements
    private <R> void queryByTimestamps(
            final String sql, final List<Long> timestamps, final RowMapper<R> rowMapper, final Consumer<R> consumer) {
        for (final var chunk : Lists.partition(timestamps, batchSize)) {
            getNamedParameterJdbcOperations()
                    .query(sql, timestampParams(chunk), rowMapper)
                    .forEach(consumer);
        }
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
}
