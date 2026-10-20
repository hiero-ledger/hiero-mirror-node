// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
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

/**
 * Sets the zero EVM address as the recipient of precompile actions that call the zero address. Consensus nodes report
 * such a recipient as contract 0, which was stored as null, and every other precompile action has a non-null recipient.
 */
@CustomLog
@Named
final class FixContractActionZeroAddressRecipientMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "1d";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String CHECKPOINT_SQL = """
            with clear_table as (delete from fix_contract_action_zero_address_progress_temp)
            insert into fix_contract_action_zero_address_progress_temp(upper_bound)
            values (:upperBound)
            """;

    private static final String CREATE_PROGRESS_TABLE = """
            create table if not exists fix_contract_action_zero_address_progress_temp(
                upper_bound bigint not null
            );
            """;

    private static final String DROP_PROGRESS_TABLE = """
            drop table if exists fix_contract_action_zero_address_progress_temp;
            """;

    private static final String SELECT_LOWER_BOUND_FLOOR = "select min(consensus_timestamp) from contract_action";

    private static final String SELECT_UPPER_BOUND = """
            select coalesce(
                (select upper_bound from fix_contract_action_zero_address_progress_temp limit 1),
                (select max(consensus_timestamp) + 1 from contract_action)
            )
            """;

    private static final String UPDATE_SQL = """
            update contract_action
            set recipient_address = '\\x0000000000000000000000000000000000000000'::bytea
            where consensus_timestamp >= :lowerBound
              and consensus_timestamp < :upperBound
              and call_type = 3
              and recipient_account is null
              and recipient_address is null
              and recipient_contract is null
            """;

    private final long batchInterval;
    private long initialUpperBound = -1L;
    private long lowerBoundFloor = 0L;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private final boolean v2;

    FixContractActionZeroAddressRecipientMigration(
            DBProperties dbProperties,
            Environment environment,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
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
        return "Set the zero address as the recipient of contract actions that call it";
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.34.0") : MigrationVersion.fromVersion("1.129.0");
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long upperBound) {
        final var lowerBound = upperBound - batchInterval;
        final var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", upperBound);
        final var count = getNamedParameterJdbcOperations().update(UPDATE_SQL, params);

        if (count > 0) {
            log.info(
                    "Fixed {} zero address contract action recipients in range [{}, {})",
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

    @Override
    protected boolean performSynchronousSteps() {
        final var jdbcOperations = getJdbcOperations();
        jdbcOperations.execute(CREATE_PROGRESS_TABLE);
        final var upperBound = jdbcOperations.queryForObject(SELECT_UPPER_BOUND, Long.class);
        final var floor = jdbcOperations.queryForObject(SELECT_LOWER_BOUND_FLOOR, Long.class);

        if (upperBound == null || floor == null) {
            log.info("No contract actions to fix");
            jdbcOperations.execute(DROP_PROGRESS_TABLE);
            return false;
        }

        initialUpperBound = upperBound;
        lowerBoundFloor = floor;
        log.info("Fixing zero address contract action recipients from {} down to {}", upperBound, floor);
        return true;
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
