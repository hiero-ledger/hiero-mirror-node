// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.jspecify.annotations.NonNull;
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
final class ContractLogSyntheticBackfillMigration extends AsyncJavaMigration<Long> {

    static final long BATCH_INTERVAL = Duration.ofDays(7).toNanos();

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists contract_log_synthetic_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists contract_log_synthetic_progress_temp;
            """;

    // Uses partial index on synthetic=true for the initial upper bound; falls back to max(null)+1 if none exist yet.
    private static final String SELECT_UPPER_BOUND = """
            select coalesce(
                (select upper_bound from contract_log_synthetic_progress_temp limit 1),
                (select min(consensus_timestamp) from contract_log where synthetic is true),
                (select max(consensus_timestamp) + 1 from contract_log where synthetic is null)
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
            update contract_log cl
            set synthetic = true
            where cl.synthetic is null
              and cl.consensus_timestamp >= :lowerBound
              and cl.consensus_timestamp < :upperBound
              and not exists (
                select 1 from contract_result cr
                where cr.consensus_timestamp = cl.consensus_timestamp
              )
            """;

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
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Backfill synthetic flag for HAPI transfer contract log rows";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.28.1") : MigrationVersion.fromVersion("1.123.1");
    }

    @Override
    protected boolean performSynchronousSteps() {
        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);

        var upperBound = getJdbcOperations().queryForObject(SELECT_UPPER_BOUND, Long.class);
        if (upperBound == null) {
            log.info("No contract_log rows to backfill");
            return true;
        }

        var floor = getJdbcOperations().queryForObject(SELECT_LOWER_BOUND_FLOOR, Long.class);
        if (floor == null) {
            log.info("contract_log is empty, skipping backfill");
            return true;
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
        if (upperBound < 0) {
            return Optional.empty();
        }

        var lowerBound = upperBound - BATCH_INTERVAL;
        var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", upperBound);
        var updated = getNamedParameterJdbcOperations().update(BACKFILL_SQL, params);
        log.info("Backfilled {} contract_log rows in range [{}, {})", updated, lowerBound, upperBound);

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
