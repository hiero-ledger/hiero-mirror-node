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
final class ContractLogSyntheticBackfillMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "6h";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists contract_log_synthetic_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists contract_log_synthetic_progress_temp;
            """;

    // Uses partial index on synthetic=true for the initial upper bound; falls back to max+1 over all rows.
    private static final String SELECT_UPPER_BOUND = """
            select coalesce(
                (select upper_bound from contract_log_synthetic_progress_temp limit 1),
                (select min(consensus_timestamp) from contract_log where synthetic is true),
                (select max(consensus_timestamp) + 1 from contract_log)
            )
            """;

    // Avoids running empty iterations below the oldest row.
    private static final String SELECT_LOWER_BOUND_FLOOR = """
            select min(consensus_timestamp) from contract_log
            """;

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from contract_log_synthetic_progress_temp)
            insert into contract_log_synthetic_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String BACKFILL_SQL = """
            update contract_log
            set synthetic = true
            where synthetic is not true
              and consensus_timestamp >= :lowerBound
              and consensus_timestamp < :upperBound
              and consensus_timestamp not in (
                select consensus_timestamp from contract_result
                where consensus_timestamp >= :lowerBound
                  and consensus_timestamp < :upperBound
              )
            """;

    private final long batchInterval;
    private final boolean v2;

    private long lowerBoundFloor = 0L;
    private long initialUpperBound = -1L;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    ContractLogSyntheticBackfillMigration(
            Environment environment,
            ImporterProperties importerProperties,
            DBProperties dbProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());

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
        return "Backfill synthetic flag for HAPI transfer contract log rows";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.29.0") : MigrationVersion.fromVersion("1.124.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);

        var upperBound = getJdbcOperations().queryForObject(SELECT_UPPER_BOUND, Long.class);
        if (upperBound == null) {
            log.info("No contract_log rows to backfill");
            return false;
        }

        var floor = getJdbcOperations().queryForObject(SELECT_LOWER_BOUND_FLOOR, Long.class);
        if (floor == null) {
            log.info("contract_log is empty, skipping backfill");
            return false;
        }

        lowerBoundFloor = floor;
        initialUpperBound = upperBound;
        log.info("Starting synthetic backfill from {} down to {}", upperBound, lowerBoundFloor);
        return true;
    }

    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long upperBound) {
        var lowerBound = upperBound - batchInterval;
        var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", upperBound);
        var updated = getNamedParameterJdbcOperations().update(BACKFILL_SQL, params);
        if (updated > 0) {
            log.info("Backfilled {} contract_log rows in range [{}, {})", updated, lowerBound, upperBound);
        }

        if (lowerBound <= lowerBoundFloor) {
            getJdbcOperations().execute(DROP_PROGRESS_TABLE);
            return Optional.empty();
        }

        getNamedParameterJdbcOperations().update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", lowerBound));
        return Optional.of(lowerBound);
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
