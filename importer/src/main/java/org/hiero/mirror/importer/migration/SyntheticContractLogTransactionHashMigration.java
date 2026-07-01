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
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
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
final class SyntheticContractLogTransactionHashMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "12h";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String SET_CITUS_LIMIT = "set citus.max_intermediate_result_size = -1";

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists synthetic_contract_log_transaction_hash_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists synthetic_contract_log_transaction_hash_progress_temp;
            """;

    private static final String SELECT_UPPER_BOUND = """
            select coalesce(
                (select upper_bound from synthetic_contract_log_transaction_hash_progress_temp limit 1),
                (select max(consensus_timestamp) + 1 from contract_log
                    where synthetic is true and transaction_hash is not null)
            )
            """;

    private static final String SELECT_LOWER_BOUND_FLOOR = """
            select min(consensus_timestamp) from contract_log
                where synthetic is true and transaction_hash is not null
            """;

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from synthetic_contract_log_transaction_hash_progress_temp)
            insert into synthetic_contract_log_transaction_hash_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String BACKFILL_SQL = """
            with candidates as (
              select distinct on (cl.transaction_hash)
                cl.consensus_timestamp,
                substring(cl.transaction_hash from 1 for 32) as hash,
                case
                  when octet_length(cl.transaction_hash) > 32
                    then substring(cl.transaction_hash from 33)
                  else null::bytea
                end as hash_suffix,
                cl.payer_account_id
              from contract_log cl
              where cl.synthetic is true
                and cl.transaction_hash is not null
                and cl.consensus_timestamp >= :lowerBound
                and cl.consensus_timestamp < :upperBound
              order by cl.transaction_hash, cl.consensus_timestamp
            ),
            existing as (
              select hash from transaction_hash
              where hash in (select hash from candidates)
            )
            insert into transaction_hash (consensus_timestamp, hash, hash_suffix, payer_account_id)
            select consensus_timestamp, hash, hash_suffix, payer_account_id
            from candidates
            where hash not in (select hash from existing)
            """;

    @Getter
    private final EntityProperties entityProperties;

    private final long batchInterval;
    private final boolean v2;

    private long lowerBoundFloor = 0L;
    private long initialUpperBound = -1L;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    SyntheticContractLogTransactionHashMigration(
            EntityProperties entityProperties,
            Environment environment,
            ImporterProperties importerProperties,
            DBProperties dbProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.batchInterval = DurationStyle.SIMPLE
                .parse(
                        migrationProperties
                                .getParams()
                                .getOrDefault(BATCH_INTERVAL_PROPERTIES_KEY, DEFAULT_BATCH_INTERVAL),
                        ChronoUnit.HOURS)
                .toNanos();
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Backfill transaction_hash for HAPI-origin synthetic contract log rows";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.29.0") : MigrationVersion.fromVersion("1.124.0");
    }

    @Override
    protected boolean performSynchronousSteps() {
        if (!entityProperties.getPersist().isTransactionHash()) {
            return false;
        }

        getJdbcOperations().execute(CREATE_PROGRESS_TABLE);

        final var upperBound = getJdbcOperations().queryForObject(SELECT_UPPER_BOUND, Long.class);
        if (upperBound == null) {
            log.info("No synthetic contract_log rows with transaction_hash to backfill");
            return false;
        }

        final var floor = getJdbcOperations().queryForObject(SELECT_LOWER_BOUND_FLOOR, Long.class);
        if (floor == null) {
            log.info("No synthetic contract_log rows with transaction_hash to backfill");
            return false;
        }

        lowerBoundFloor = floor;
        initialUpperBound = upperBound;
        log.info("Starting synthetic transaction_hash backfill from {} down to {}", upperBound, lowerBoundFloor);
        return true;
    }

    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long upperBound) {
        final var lowerBound = upperBound - batchInterval;
        final var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", upperBound);

        if (v2) {
            getJdbcOperations().execute(SET_CITUS_LIMIT);
        }
        final var count = getNamedParameterJdbcOperations().update(BACKFILL_SQL, params);

        if (count > 0) {
            log.info(
                    "Backfilled {} transaction_hash rows from synthetic contract_log in range [{}, {})",
                    count,
                    lowerBound,
                    upperBound);
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
