// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import org.apache.commons.lang3.BooleanUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.jspecify.annotations.NullMarked;
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
@NullMarked
public class ExpandBalancePartitionsMigration extends AsyncJavaMigration<TimePartition> {
    private static final String NEW_ACCOUNT_BALANCE_TABLE = "account_balance";
    private static final String DROP_OLD_TABLES_SQL =
            """
            drop table if exists account_balance_old;
            drop table if exists token_balance_old;
            """;

    private static final Map<Boolean, MigrationVersion> MINIMUM_VERSION = Map.of(
            Boolean.FALSE, MigrationVersion.fromVersion("1.115.0"),
            Boolean.TRUE, MigrationVersion.fromVersion("2.20.0"));

    private static final String LAST_BALANCE_FILE =
            "select coalesce(max(consensus_timestamp), 0) from account_balance_file";

    private static final String EXISTS_ACCOUNT_BALANCE_V2_RANGE_SQL =
            """
            select exists(
              select 1
              from account_balance
              where consensus_timestamp >= :from
                and consensus_timestamp <  :to
              limit 1
            )
            """;

    private static final String EXISTS_TOKEN_BALANCE_V2_RANGE_SQL =
            """
            select exists(
              select 1
              from token_balance
              where consensus_timestamp >= :from
                and consensus_timestamp <  :to
              limit 1
            )
            """;

    private static final String INSERT_ACCOUNT_BALANCE_WINDOW_SQL =
            """
            insert into account_balance (account_id, balance, consensus_timestamp)
            select account_id, balance, consensus_timestamp
            from (
                select
                    ab.account_id,
                    ab.balance,
                    ab.consensus_timestamp,
                    lag(ab.balance) over (
                        partition by ab.account_id
                        order by ab.consensus_timestamp
                    ) as prev_balance
                from account_balance_old ab
                where ab.consensus_timestamp >= :from
                  and ab.consensus_timestamp <  :to
            ) t
            where t.account_id = :sentinel
               or t.prev_balance is null
               or t.prev_balance <> t.balance;
            """;

    private static final String INSERT_TOKEN_BALANCE_WINDOW_SQL =
            """
            insert into token_balance (account_id, balance, consensus_timestamp, token_id)
            select account_id, balance, consensus_timestamp, token_id
            from (
                select
                    tb.account_id,
                    tb.balance,
                    tb.consensus_timestamp,
                    tb.token_id,
                    lag(tb.balance) over (
                        partition by tb.account_id, tb.token_id
                        order by tb.consensus_timestamp
                    ) as prev_balance
                from token_balance_old tb
                where tb.consensus_timestamp >= :from
                  and tb.consensus_timestamp <  :to
            ) t
            where t.prev_balance is null
               or t.prev_balance <> t.balance;
            """;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private List<TimePartition> accountBalancePartitions = new ArrayList<>();
    private final ObjectProvider<TimePartitionService> timePartitionServiceProvider;
    private final long treasuryAccountId;
    private final boolean v2;

    private final AtomicInteger partitionIndex = new AtomicInteger(0);

    public ExpandBalancePartitionsMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            SystemEntity systemEntity,
            ObjectProvider<TimePartitionService> timePartitionServiceProvider,
            Environment environment) {

        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.treasuryAccountId = systemEntity.treasuryAccount().getId();
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        this.timePartitionServiceProvider = timePartitionServiceProvider;
    }

    @Override
    protected boolean performSynchronousSteps() {
        this.partitionIndex.set(0);
        this.accountBalancePartitions =
                timePartitionServiceProvider.getObject().getTimePartitions(NEW_ACCOUNT_BALANCE_TABLE).stream()
                        .sorted(Comparator.comparing((TimePartition p) ->
                                        p.getTimestampRange().upperEndpoint())
                                .reversed())
                        .toList();
        log.info("Performing synchronous steps for balance migration");

        Boolean performAsync = getTransactionOperations().execute(status -> {
            final var maxBalanceFileTimestamp =
                    Objects.requireNonNull(getJdbcOperations().queryForObject(LAST_BALANCE_FILE, Long.class));

            if (maxBalanceFileTimestamp == 0L) {
                log.info("No balance files found, skipping asynchronous migration steps");
                finalizeMigration();
                return Boolean.FALSE;
            }

            final var sixHoursNs = 6L * 60 * 60 * 1_000_000_000L;
            final var minTimestamp = Math.max(0L, maxBalanceFileTimestamp - sixHoursNs);

            log.info(
                    "Synchronously backfilling partitions overlapping [{}, {}]", minTimestamp, maxBalanceFileTimestamp);

            while (partitionIndex.get() < accountBalancePartitions.size()) {
                final var idx = partitionIndex.get();
                final var partition = accountBalancePartitions.get(idx);
                final var range = partition.getTimestampRange();
                final var from = range.lowerEndpoint();
                final var to = range.upperEndpoint();

                if (to <= minTimestamp) {
                    log.info("Reached partition {} no more synchronous partitions to process", range);
                    break;
                }

                final var intersects = (from <= maxBalanceFileTimestamp);

                if (intersects) {
                    log.info("Synchronously processing partition {}", range);
                    processPartition(partition);
                }

                partitionIndex.incrementAndGet();
            }

            return Boolean.TRUE;
        });

        return BooleanUtils.isTrue(performAsync);
    }

    @Override
    protected TimePartition getInitial() {
        return accountBalancePartitions.get(partitionIndex.getAndIncrement());
    }

    @Override
    protected Optional<TimePartition> migratePartial(TimePartition partition) {
        processPartition(partition);

        if (partitionIndex.get() >= accountBalancePartitions.size()) {
            finalizeMigration();
            return Optional.empty();
        }

        return Optional.of(accountBalancePartitions.get(partitionIndex.getAndIncrement()));
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return Objects.requireNonNull(MINIMUM_VERSION.get(v2)); // The version balance table partitions are expanded
    }

    private void finalizeMigration() {
        log.info("Finalizing balance migration and dropping old tables");
        getJdbcOperations().execute(DROP_OLD_TABLES_SQL);
    }

    private void processPartition(TimePartition partition) {
        var stopwatch = Stopwatch.createStarted();
        getJdbcOperations().execute("set local work_mem = '2048MB'"); // Use higher work_mem to avoid temp files

        final var params = new MapSqlParameterSource()
                .addValue("from", partition.getTimestampRange().lowerEndpoint())
                .addValue("to", partition.getTimestampRange().upperEndpoint());

        final var accountV2HasData = getNamedParameterJdbcOperations()
                .queryForObject(EXISTS_ACCOUNT_BALANCE_V2_RANGE_SQL, params, Boolean.class);
        final var tokenV2HasData = getNamedParameterJdbcOperations()
                .queryForObject(EXISTS_TOKEN_BALANCE_V2_RANGE_SQL, params, Boolean.class);

        if (Boolean.TRUE.equals(accountV2HasData) || Boolean.TRUE.equals(tokenV2HasData)) {
            log.info(
                    "Skipping partition {} because v2 tables already have data in this range",
                    partition.getTimestampRange());
        } else {
            final var tokenBalanceCount =
                    getNamedParameterJdbcOperations().update(INSERT_TOKEN_BALANCE_WINDOW_SQL, params);

            params.addValue("sentinel", treasuryAccountId);
            final var accountBalanceCount =
                    getNamedParameterJdbcOperations().update(INSERT_ACCOUNT_BALANCE_WINDOW_SQL, params);

            log.info(
                    "Migrated {} account balances and {} token balances for {} in {}",
                    accountBalanceCount,
                    tokenBalanceCount,
                    partition,
                    stopwatch);
        }
    }

    private TransactionOperations transactionOperations() {
        var jdbcTemplate = (JdbcTemplate) getJdbcOperations();
        var transactionManager = new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public String getDescription() {
        return "backfill balance tables";
    }
}
