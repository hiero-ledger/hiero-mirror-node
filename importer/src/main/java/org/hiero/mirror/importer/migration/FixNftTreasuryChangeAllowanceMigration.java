// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Named
final class FixNftTreasuryChangeAllowanceMigration extends RepeatableMigration {

    private static final String CREATE_TREASURY_CHANGE_EVENT_TABLE_SQL = """
            create temp table treasury_change_event on commit drop as
            with token_revision as (
              select token_id, treasury_account_id, lower(timestamp_range) as timestamp
              from token
              where type = 'NON_FUNGIBLE_UNIQUE'
              union all
              select token_id, treasury_account_id, lower(timestamp_range) as timestamp
              from token_history
              where type = 'NON_FUNGIBLE_UNIQUE'
            ), ordered as (
              select
                token_id,
                timestamp,
                treasury_account_id,
                lag(treasury_account_id) over (partition by token_id order by timestamp)
                  as previous_treasury_account_id
              from token_revision
            )
            select token_id, timestamp
            from ordered
            where previous_treasury_account_id is not null and treasury_account_id <> previous_treasury_account_id;

            create index on treasury_change_event (timestamp);
            """;

    private static final String TREASURY_CHANGE_EVENT_BATCH_SQL = """
            select token_id, timestamp
            from treasury_change_event
            where timestamp > :lastTimestamp
            order by timestamp
            limit :limit
            """;

    private static final RowMapper<TreasuryChangeEvent> TREASURY_CHANGE_EVENT_ROW_MAPPER =
            new DataClassRowMapper<>(TreasuryChangeEvent.class);

    private static final String UPDATE_NFT_HISTORY_SQL = updateSql("nft_history");
    private static final String UPDATE_NFT_SQL = updateSql("nft");

    @VisibleForTesting
    int eventBatchSize = 1000;

    private final ObjectProvider<JdbcOperations> jdbcOperationsProvider;

    FixNftTreasuryChangeAllowanceMigration(
            final ImporterProperties importerProperties,
            final @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
    }

    @Override
    public String getDescription() {
        return "Preserve nft spender and delegating spender across token treasury changes";
    }

    @Override
    protected void doMigrate() {
        final var stopwatch = Stopwatch.createStarted();
        final var jdbcOperations = new NamedParameterJdbcTemplate(jdbcOperationsProvider.getObject());

        transactionOperations(jdbcOperations.getJdbcTemplate()).executeWithoutResult(_ -> {
            List<TreasuryChangeEvent> events;
            long lastTimestamp = Long.MIN_VALUE;
            long totalEvents = 0;
            long totalNftAllowances = 0;

            jdbcOperations.getJdbcOperations().execute(CREATE_TREASURY_CHANGE_EVENT_TABLE_SQL);
            do {
                final var eventParameters = new MapSqlParameterSource()
                        .addValue("lastTimestamp", lastTimestamp)
                        .addValue("limit", eventBatchSize);
                events = jdbcOperations.query(
                        TREASURY_CHANGE_EVENT_BATCH_SQL, eventParameters, TREASURY_CHANGE_EVENT_ROW_MAPPER);

                // Events must be applied oldest-first within a batch, and across batches, so a later treasury change
                // for the same token always sees the fix from an earlier one rather than propagating a stale null
                // forward.
                for (var event : events) {
                    var parameters = new MapSqlParameterSource()
                            .addValue("tokenId", event.tokenId())
                            .addValue("timestamp", event.timestamp());
                    totalNftAllowances += jdbcOperations.update(UPDATE_NFT_SQL, parameters);
                    totalNftAllowances += jdbcOperations.update(UPDATE_NFT_HISTORY_SQL, parameters);
                }

                if (!events.isEmpty()) {
                    lastTimestamp = events.getLast().timestamp();
                    totalEvents += events.size();
                }
            } while (events.size() == eventBatchSize);

            log.info(
                    "Fixed {} nft allowances for {} token treasury change events in {}",
                    totalNftAllowances,
                    totalEvents,
                    stopwatch);
        });
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version token_history was added, the last of the three prerequisite schema pieces. Since in V2, the
        // tables were created with the required schema, it's unnecessary to use a V2 specific minimum version.
        return MigrationVersion.fromVersion("1.81.3");
    }

    private static TransactionTemplate transactionOperations(final JdbcTemplate jdbcTemplate) {
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private static String updateSql(final String table) {
        return """
                update %1$s as t
                set spender = p.spender, delegating_spender = p.delegating_spender
                from (
                  select n.serial_number, h.spender, h.delegating_spender
                  from %1$s as n
                  cross join lateral (
                    select spender, delegating_spender
                    from nft_history as prev
                    where prev.token_id = :tokenId
                      and prev.serial_number = n.serial_number
                      and lower(prev.timestamp_range) < :timestamp
                    order by lower(prev.timestamp_range) desc
                    limit 1
                  ) as h
                  where n.token_id = :tokenId
                    and lower(n.timestamp_range) = :timestamp
                    and h.spender is not null
                ) as p
                where t.token_id = :tokenId
                  and t.serial_number = p.serial_number
                """.formatted(table);
    }

    private record TreasuryChangeEvent(long timestamp, long tokenId) {}
}
