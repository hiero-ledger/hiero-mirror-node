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

    private final boolean v2;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private static final String SELECT_MAX_NULL_TIMESTAMP = """
            select max(consensus_timestamp) from contract_log where synthetic is null
            """;

    private static final String BACKFILL_SQL = """
            update contract_log cl
            set synthetic = true
            where cl.synthetic is null
              and cl.consensus_timestamp > :lowerBound
              and cl.consensus_timestamp <= :upperBound
              and not exists (
                select 1 from contract_result cr
                where cr.contract_id = cl.contract_id
                  and cr.consensus_timestamp = cl.consensus_timestamp
              )
            """;

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
        return v2 ? MigrationVersion.fromVersion("2.29.0") : MigrationVersion.fromVersion("1.124.0");
    }

    @Override
    protected Long getInitial() {
        var maxTimestamp = getJdbcOperations().queryForObject(SELECT_MAX_NULL_TIMESTAMP, Long.class);
        if (maxTimestamp == null) {
            log.info("No contract_log rows with synthetic=null found");
            return -1L;
        }
        log.info("Starting synthetic backfill from timestamp {}", maxTimestamp);
        return maxTimestamp;
    }

    @Override
    @NonNull
    protected Optional<Long> migratePartial(Long endTimestamp) {
        if (endTimestamp < 0) {
            return Optional.empty();
        }

        var lowerBound = endTimestamp - BATCH_INTERVAL;
        var params =
                new MapSqlParameterSource().addValue("lowerBound", lowerBound).addValue("upperBound", endTimestamp);
        var updated = getNamedParameterJdbcOperations().update(BACKFILL_SQL, params);
        log.info("Backfilled {} contract_log rows in range ({}, {}]", updated, lowerBound, endTimestamp);

        return lowerBound > 0 ? Optional.of(lowerBound) : Optional.empty();
    }

    private TransactionOperations transactionOperations() {
        final var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
