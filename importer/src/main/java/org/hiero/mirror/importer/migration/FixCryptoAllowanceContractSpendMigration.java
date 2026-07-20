// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.SystemEntity;
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

/**
 * Backfills the remaining amount of crypto allowances that were spent on-chain through a smart contract.
 */
@CustomLog
@Named
final class FixCryptoAllowanceContractSpendMigration extends AsyncJavaMigration<Long> {

    static final String DEFAULT_BATCH_INTERVAL = "1d";

    private static final String BATCH_INTERVAL_PROPERTIES_KEY = "batchInterval";

    private static final String CREATE_PROGRESS_TABLE_SQL =
            "create table if not exists crypto_allowance_contract_spend_progress (upper_bound bigint not null)";

    private static final String DROP_PROGRESS_TABLE_SQL =
            "drop table if exists crypto_allowance_contract_spend_progress";

    private static final String PROGRESS_TABLE_EXISTS_SQL =
            "select to_regclass('crypto_allowance_contract_spend_progress') is not null";

    private static final String SELECT_CHECKPOINT_SQL =
            "select max(upper_bound) from crypto_allowance_contract_spend_progress";

    private static final String SELECT_MAX_CONTRACT_RESULT_SQL =
            "select max(consensus_timestamp) + 1 from contract_result where contract_id = :htsContractId";

    private static final String SELECT_LOWER_BOUND_FLOOR_SQL =
            "select min(lower(timestamp_range)) from crypto_allowance where amount_granted > 0";

    private static final String CHECKPOINT_SQL = """
            with clear as (delete from crypto_allowance_contract_spend_progress)
            insert into crypto_allowance_contract_spend_progress (upper_bound) values (:upperBound)
            """;

    private static final String ENABLE_REPARTITION_JOINS_SQL = "set local citus.enable_repartition_joins to on";

    private static final String SET_INTERMEDIATE_RESULT_SIZE_SQL = "set local citus.max_intermediate_result_size to -1";

    private static final String CREATE_BATCH_TABLES_SQL = """
            create temp table crypto_allowance_contract_spend_batch (
              owner bigint not null,
              spender bigint not null,
              amount_spent bigint not null
            ) on commit drop;
            create temp table crypto_allowance_contract_spend_reversal_batch (
              owner bigint not null,
              spender bigint not null,
              amount_spent bigint not null
            ) on commit drop;
            """;

    private static final String POPULATE_BATCH_TABLES_SQL = """
            with hts_contract_call as (
              select consensus_timestamp, sender_id
              from contract_result
              where contract_id = :htsContractId
                and sender_id is not null
                and consensus_timestamp >= :lowerBound
                and consensus_timestamp < :upperBound
            ),
            approved_contract_spend as (
              select ct.entity_id as owner,
                     cr.sender_id as contract,
                     ct.payer_account_id as payer,
                     ct.amount,
                     ct.consensus_timestamp
              from crypto_transfer ct
              join hts_contract_call cr
                on cr.consensus_timestamp = ct.consensus_timestamp
              where ct.is_approval is true
                and ct.amount < 0
                and ct.payer_account_id <> cr.sender_id
                and ct.consensus_timestamp >= :lowerBound
                and ct.consensus_timestamp < :upperBound
            ),
            matched_allowance as (
              select s.owner, ca.spender, s.amount, ca.spender = s.contract as contract_spend
              from approved_contract_spend s
              join crypto_allowance ca
                on ca.owner = s.owner
                and ca.spender in (s.contract, s.payer)
                and ca.amount_granted > 0
                and s.consensus_timestamp > lower(ca.timestamp_range)
            ),
            missed as (
              insert into crypto_allowance_contract_spend_batch (owner, spender, amount_spent)
              select owner, spender, sum(amount)
              from matched_allowance
              where contract_spend
              group by owner, spender
            )
            insert into crypto_allowance_contract_spend_reversal_batch (owner, spender, amount_spent)
            select owner, spender, sum(amount)
            from matched_allowance
            where not contract_spend
            group by owner, spender
            """;

    private static final String APPLY_MISSED_SPEND_SQL = """
            update crypto_allowance ca
            set amount = greatest(ca.amount + w.amount_spent, 0)
            from crypto_allowance_contract_spend_batch w
            where ca.owner = w.owner
              and ca.spender = w.spender
              and w.amount_spent <> 0
            """;

    private static final String APPLY_WRONG_SPEND_SQL = """
            update crypto_allowance ca
            set amount = least(ca.amount - w.amount_spent, ca.amount_granted)
            from crypto_allowance_contract_spend_reversal_batch w
            where ca.owner = w.owner
              and ca.spender = w.spender
              and w.amount_spent <> 0
            """;

    private final long batchInterval;
    private final EntityProperties entityProperties;
    private final long htsContractId;
    private final boolean v2;

    private long lowerBoundFloor = 0L;
    private long initialUpperBound = 0L;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    public FixCryptoAllowanceContractSpendMigration(
            Environment environment,
            ImporterProperties importerProperties,
            DBProperties dbProperties,
            EntityProperties entityProperties,
            SystemEntity systemEntity,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.entityProperties = entityProperties;
        this.htsContractId = systemEntity.hederaTokenServiceContract().getId();
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
        return "Fix crypto allowance contract spend";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return v2 ? MigrationVersion.fromVersion("2.30.1") : MigrationVersion.fromVersion("1.125.1");
    }

    @Override
    protected boolean performSynchronousSteps() {
        var floor = getJdbcOperations().queryForObject(SELECT_LOWER_BOUND_FLOOR_SQL, Long.class);
        if (floor == null) {
            log.info("No crypto allowances found, skipping contract-spend backfill");
            getJdbcOperations().execute(DROP_PROGRESS_TABLE_SQL);
            return false;
        }

        var upperBound = getUpperBound();
        if (upperBound == null || upperBound <= floor) {
            log.info(
                    "No crypto allowance contract-spend history to backfill, upperBound {} floor {}",
                    upperBound,
                    floor);
            getJdbcOperations().execute(DROP_PROGRESS_TABLE_SQL);
            return false;
        }

        lowerBoundFloor = floor;
        initialUpperBound = upperBound;
        getJdbcOperations().execute(CREATE_PROGRESS_TABLE_SQL);
        log.info("Starting crypto allowance contract-spend backfill from {} down to {}", upperBound, floor);
        return true;
    }

    /**
     * Resumes from the last checkpoint if a previous run was interrupted, otherwise starts from the latest HTS
     * system contract call. The progress table may not exist yet, so guard the read.
     */
    private Long getUpperBound() {

        var exists = Boolean.TRUE.equals(getJdbcOperations().queryForObject(PROGRESS_TABLE_EXISTS_SQL, Boolean.class));
        if (exists) {
            var checkpoint = getJdbcOperations().queryForObject(SELECT_CHECKPOINT_SQL, Long.class);
            if (checkpoint != null) {
                return checkpoint;
            }
        }

        return getNamedParameterJdbcOperations()
                .queryForObject(
                        SELECT_MAX_CONTRACT_RESULT_SQL,
                        new MapSqlParameterSource("htsContractId", htsContractId),
                        Long.class);
    }

    @Override
    protected Long getInitial() {
        return initialUpperBound;
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long upperBound) {
        long lowerBound = upperBound - batchInterval;

        var parameters = new MapSqlParameterSource()
                .addValue("lowerBound", lowerBound)
                .addValue("upperBound", upperBound)
                .addValue("htsContractId", htsContractId);
        getJdbcOperations().execute(CREATE_BATCH_TABLES_SQL);
        getNamedParameterJdbcOperations().update(POPULATE_BATCH_TABLES_SQL, parameters);

        int updated = getJdbcOperations().update(APPLY_MISSED_SPEND_SQL);
        if (updated > 0) {
            log.info("Backfilled {} crypto allowances in range [{}, {})", updated, lowerBound, upperBound);
        }

        int reversed = getJdbcOperations().update(APPLY_WRONG_SPEND_SQL);
        if (reversed > 0) {
            log.info(
                    "Reversed {} wrongly debited crypto allowances in range [{}, {})",
                    reversed,
                    lowerBound,
                    upperBound);
        }

        if (lowerBound <= lowerBoundFloor) {
            getJdbcOperations().execute(DROP_PROGRESS_TABLE_SQL);
            return Optional.empty();
        }

        getNamedParameterJdbcOperations().update(CHECKPOINT_SQL, new MapSqlParameterSource("upperBound", lowerBound));

        return Optional.of(lowerBound);
    }

    @Override
    protected boolean skipMigration(Configuration configuration) {
        if (super.skipMigration(configuration)) {
            return true;
        }

        if (!entityProperties.getPersist().isTrackAllowance()) {
            log.info("Skipping migration since track allowance is disabled");
            return true;
        }

        return false;
    }

    private TransactionOperations transactionOperations() {
        var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
