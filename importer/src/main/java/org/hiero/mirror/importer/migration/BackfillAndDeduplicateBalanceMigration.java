// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.annotation.Nonnull;
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
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Named
@Profile("!v2")
public class BackfillAndDeduplicateBalanceMigration extends AsyncJavaMigration<Long> {

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";
    private static final String DEFAULT_MIN_FREQUENCY = "4h";
    private static final long EPOCH = 0L;
    private static final String MIN_FREQUENCY_KEY = "minFrequency";
    private static final long NO_BALANCE = -1;

    // Can't use spring data repositories because the migration runs with a different role, so it can drop the resources
    // at the end
    private static final String CLEANUP_SQL =
            """
            drop function if exists create_full_account_balance_snapshot(bigint, bigint);
            drop function if exists create_deduped_account_balance_snapshot(bigint, bigint);
            drop function if exists create_full_token_balance_snapshot(bigint, bigint);
            drop function if exists create_deduped_token_balance_snapshot(bigint, bigint);

            truncate account_balance_old;
            truncate token_balance_old;
            drop table account_balance_old;
            drop table token_balance_old;
            """;

    private static final String CREATE_FULL_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            "select create_full_account_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_DEDUPED_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            "select create_deduped_account_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_FULL_TOKEN_BALANCE_SNAPSHOT_SQL =
            "select create_full_token_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String CREATE_DEDUPED_TOKEN_BALANCE_SNAPSHOT_SQL =
            "select create_deduped_token_balance_snapshot(:balanceTimestamp, :prevBalanceTimestamp)";

    private static final String IS_ACCOUNT_BALANCE_PARTITION_EMPTY_SQL =
            """
            select not exists(
              select * from account_balance
              where consensus_timestamp >= :lowerBound and consensus_timestamp < :upperBound
            )
            """;

    private static final String PATCH_ORIGINAL_FIRST_ACCOUNT_BALANCE_SNAPSHOT_SQL =
            """
            with previous as (
              select *
              from account_balance_old
              where consensus_timestamp = :prevBalanceTimestamp
            ), current as (
              select *
              from account_balance_old
              where consensus_timestamp = :balanceTimestamp
            )
            insert into account_balance (account_id, balance, consensus_timestamp)
            select p.account_id, 0, :balanceTimestamp
            from previous as p
            left join current as c using (account_id)
            where c.account_id is null
            """;

    private static final String PATCH_ORIGINAL_FIRST_TOKEN_BALANCE_SNAPSHOT_SQL =
            """
            with previous as (
              select *
              from token_balance_old
              where consensus_timestamp = :prevBalanceTimestamp
            ), current as (
              select *
              from token_balance_old
              where consensus_timestamp = :balanceTimestamp
            )
            insert into token_balance (account_id, balance, consensus_timestamp, token_id)
            select p.account_id, 0, :balanceTimestamp, p.token_id
            from previous as p
            left join current as c using (account_id, token_id)
            where c.account_id is null
            """;

    private static final String SELECT_NEXT_CONSENSUS_TIMESTAMP_SQL =
            """
            select consensus_timestamp
            from account_balance_file
            where consensus_timestamp >= :lowerBound and consensus_timestamp < :upperBound
            order by consensus_timestamp
            limit 1
            """;

    private static final String SELECT_INITIAL_CONSENSUS_TIMESTAMP_SQL =
            """
            select coalesce(max(consensus_timestamp), 0)
            from account_balance
            where account_id = 2 and consensus_timestamp < ?
            """;

    private static final String SELECT_LAST_CONSENSUS_TIMESTAMP_SQL =
            "select balance from account_balance_old where consensus_timestamp = -1 and account_id = -1";

    private final ObjectProvider<TimePartitionService> timePartitionServiceProvider;

    @Getter(lazy = true)
    private final TransactionOperations transactionOperations = transactionOperations();

    private Long lastConsensusTimestamp;
    private long minFrequency;

    public BackfillAndDeduplicateBalanceMigration(
            DBProperties dbProperties,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<TimePartitionService> timePartitionServiceProvider) {
        super(importerProperties.getMigration(), jdbcTemplateProvider, dbProperties.getSchema());
        this.timePartitionServiceProvider = timePartitionServiceProvider;
    }

    @Override
    public String getDescription() {
        return "Backfill and deduplicate old balance information";
    }

    @Override
    protected Long getInitial() {
        minFrequency = DurationStyle.SIMPLE
                .parse(
                        migrationProperties.getParams().getOrDefault(MIN_FREQUENCY_KEY, DEFAULT_MIN_FREQUENCY),
                        ChronoUnit.MINUTES)
                .toNanos();

        lastConsensusTimestamp =
                queryForObjectOrNull(SELECT_LAST_CONSENSUS_TIMESTAMP_SQL, EmptySqlParameterSource.INSTANCE, Long.class);
        if (lastConsensusTimestamp == null) {
            return NO_BALANCE;
        }

        return getJdbcTemplate()
                .queryForObject(SELECT_INITIAL_CONSENSUS_TIMESTAMP_SQL, Long.class, lastConsensusTimestamp);
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.89.2"); // The version balance table partitions are created
    }

    @Nonnull
    @Override
    protected Optional<Long> migratePartial(Long last) {
        var stopwatch = Stopwatch.createStarted();
        getJdbcTemplate().execute("set local work_mem = '512MB'"); // Use higher work_mem to avoid temp files

        var timestamp = getBalanceTimestamp(last);
        if (timestamp == null) {
            patchSnapshotAtLastConsensusTimestamp(last);
            getJdbcTemplate().execute(CLEANUP_SQL);
            return Optional.empty();
        }

        var partitions = timePartitionServiceProvider
                .getObject()
                .getOverlappingTimePartitions(ACCOUNT_BALANCE_TABLE_NAME, timestamp, timestamp);
        if (partitions.isEmpty()) {
            throw new InvalidDatasetException(
                    String.format("No account_balance table partition found for timestamp %d", timestamp));
        }

        var partitionRange = partitions.get(0).getTimestampRange();
        var params = new MapSqlParameterSource()
                .addValue("lowerBound", partitionRange.lowerEndpoint())
                .addValue("upperBound", Math.min(partitionRange.upperEndpoint(), lastConsensusTimestamp));
        var isPartitionEmpty = getNamedParameterJdbcTemplate()
                .queryForObject(IS_ACCOUNT_BALANCE_PARTITION_EMPTY_SQL, params, Boolean.class);
        var createAccountBalanceSnapshotSql = Boolean.TRUE.equals(isPartitionEmpty)
                ? CREATE_FULL_ACCOUNT_BALANCE_SNAPSHOT_SQL
                : CREATE_DEDUPED_ACCOUNT_BALANCE_SNAPSHOT_SQL;
        var createTokenBalanceSnapshotSql = Boolean.TRUE.equals(isPartitionEmpty)
                ? CREATE_FULL_TOKEN_BALANCE_SNAPSHOT_SQL
                : CREATE_DEDUPED_TOKEN_BALANCE_SNAPSHOT_SQL;

        params = new MapSqlParameterSource()
                .addValue("balanceTimestamp", timestamp)
                .addValue("prevBalanceTimestamp", last);
        var accountBalanceCount =
                getNamedParameterJdbcTemplate().queryForObject(createAccountBalanceSnapshotSql, params, Integer.class);
        var tokenBalanceCount =
                getNamedParameterJdbcTemplate().queryForObject(createTokenBalanceSnapshotSql, params, Integer.class);

        log.info(
                "Created a new {} snapshot with {} account balances and {} token balances at timestamp {} in {}",
                Boolean.TRUE.equals(isPartitionEmpty) ? "full" : "deduped",
                accountBalanceCount,
                tokenBalanceCount,
                timestamp,
                stopwatch);

        return Optional.of(timestamp);
    }

    private Long getBalanceTimestamp(Long last) {
        if (last == NO_BALANCE) {
            return null;
        }

        long lowerBound = last + minFrequency;
        var params = new MapSqlParameterSource()
                .addValue("lowerBound", lowerBound)
                .addValue("upperBound", lastConsensusTimestamp);
        return queryForObjectOrNull(SELECT_NEXT_CONSENSUS_TIMESTAMP_SQL, params, Long.class);
    }

    private void patchSnapshotAtLastConsensusTimestamp(Long lastProcessedTimestamp) {
        if (lastConsensusTimestamp == null || lastProcessedTimestamp == EPOCH) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var params = new MapSqlParameterSource()
                .addValue("balanceTimestamp", lastConsensusTimestamp)
                .addValue("prevBalanceTimestamp", lastProcessedTimestamp);
        int accountBalanceCount =
                getNamedParameterJdbcTemplate().update(PATCH_ORIGINAL_FIRST_ACCOUNT_BALANCE_SNAPSHOT_SQL, params);
        int tokenBalanceCount =
                getNamedParameterJdbcTemplate().update(PATCH_ORIGINAL_FIRST_TOKEN_BALANCE_SNAPSHOT_SQL, params);
        log.info(
                "Patched the original first balance snapshot with {} account balances and {} token balances in {}",
                accountBalanceCount,
                tokenBalanceCount,
                stopwatch);
    }

    private TransactionOperations transactionOperations() {
        var transactionManager = new DataSourceTransactionManager(
                Objects.requireNonNull(getJdbcTemplate().getDataSource()));
        return new TransactionTemplate(transactionManager);
    }
}
