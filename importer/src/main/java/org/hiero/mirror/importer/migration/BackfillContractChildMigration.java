// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Named
final class BackfillContractChildMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "3h";

    // IN instead of JOIN since transaction has a different citus distribution key
    private static final String BACKFILL_CONTRACT_LOG_CHILD_SQL = """
            update contract_log
            set child = true
            where child is not true
              and consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :consensusEnd
              and consensus_timestamp in (
                select consensus_timestamp from transaction
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :consensusEnd
                  and parent_consensus_timestamp is not null
              )
            """;

    private static final String BACKFILL_CONTRACT_RESULT_CHILD_SQL = """
            update contract_result
            set child = true
            where child is not true
              and consensus_timestamp >= :consensusStart
              and consensus_timestamp <= :consensusEnd
              and consensus_timestamp in (
                select consensus_timestamp from transaction
                where consensus_timestamp >= :consensusStart
                  and consensus_timestamp <= :consensusEnd
                  and parent_consensus_timestamp is not null
              )
            """;

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from backfill_contract_child_progress_temp)
            insert into backfill_contract_child_progress_temp(upper_bound) values (:upperBound)
            """;

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists backfill_contract_child_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists backfill_contract_child_progress_temp;
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

    private final long batchInterval;
    private long initialUpperBound = -1L;
    private long lowerBound = Long.MAX_VALUE;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final boolean v2;

    BackfillContractChildMigration(
            final DBProperties dbProperties,
            final Environment environment,
            final ImporterProperties importerProperties,
            final @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
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
        return "Backfill contract_result and contract_log child column from transaction.parent_consensus_timestamp";
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

        final long consensusStart = consensusEnd - batchInterval + 1;
        final var params = new MapSqlParameterSource()
                .addValue("consensusStart", consensusStart)
                .addValue("consensusEnd", consensusEnd);

        final int updatedResults = getNamedParameterJdbcOperations().update(BACKFILL_CONTRACT_RESULT_CHILD_SQL, params);
        final int updatedLogs = getNamedParameterJdbcOperations().update(BACKFILL_CONTRACT_LOG_CHILD_SQL, params);
        if (updatedResults > 0 || updatedLogs > 0) {
            log.info(
                    "Backfilled child column for {} contract_result and {} contract_log rows in range [{}, {}]",
                    updatedResults,
                    updatedLogs,
                    consensusStart,
                    consensusEnd);
        }

        final long nextUpperBound = consensusStart - 1;
        getNamedParameterJdbcOperations()
                .update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", nextUpperBound));
        return Optional.of(nextUpperBound);
    }

    @Override
    protected boolean performSynchronousSteps() {
        final var jdbcOperations = getJdbcOperations();
        final var maxConsensusTimestamp = jdbcOperations.queryForObject(SELECT_MAX_CONSENSUS_TIMESTAMP, Long.class);
        if (maxConsensusTimestamp == null) {
            log.info("No contract results or logs to process, skipping migration");
            return false;
        }

        jdbcOperations.execute(CREATE_PROGRESS_TABLE);
        final var savedProgress =
                queryForObjectOrNull(SELECT_PROGRESS_UPPER_BOUND, new MapSqlParameterSource(), Long.class);
        initialUpperBound = savedProgress != null ? savedProgress : maxConsensusTimestamp;
        lowerBound = Objects.requireNonNull(jdbcOperations.queryForObject(SELECT_MIN_CONSENSUS_TIMESTAMP, Long.class));
        log.info(
                "Starting contract child column backfill with initial timestamp: {}, lower bound: {}",
                initialUpperBound,
                lowerBound);
        return true;
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
