// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Since HAPI 0.77.0, consensus nodes ignore approvedForAll=false in an nft allowance with a delegating spender, while
 * the importer still revoked the owner's approved for all allowance granted to the spender. This migration
 * retroactively reverts such approved for all nft allowance revocations.
 */
@Named
final class FixDelegatedNftApproveForAllMigration extends RepeatableMigration {

    static final long INTERVAL = Duration.ofDays(7).toNanos();

    // The consensus timestamp of the first HAPI 0.77.x transaction in mainnet
    static final long MAINNET_START_TIMESTAMP = 1790701462313613104L;

    // The consensus timestamp of the first HAPI 0.77.x transaction in testnet
    static final long TESTNET_START_TIMESTAMP = 1789491600910006104L;

    private static final String DELETE_CURRENT_SQL = """
            delete from nft_allowance
            where owner = :owner and spender = :spender and token_id = :tokenId
              and lower(timestamp_range) = :timestamp
            """;

    private static final String DELETE_HISTORY_SQL = """
            delete from nft_allowance_history
            where owner = :owner and spender = :spender and token_id = :tokenId
              and lower(timestamp_range) = :timestamp
            """;

    private static final String DELETE_PREVIOUS_SQL = """
            delete from nft_allowance_history
            where owner = :owner and spender = :spender and token_id = :tokenId
              and upper(timestamp_range) = :timestamp
            """;

    private static final String END_TIMESTAMP_SQL = "select max(consensus_end) from record_file";

    private static final String EXTEND_PREVIOUS_SQL = """
            update nft_allowance_history
            set timestamp_range = int8range(lower(timestamp_range), :upperTimestamp)
            where owner = :owner and spender = :spender and token_id = :tokenId
              and upper(timestamp_range) = :timestamp
            """;

    private static final String RESTORE_PREVIOUS_SQL = """
            insert into nft_allowance (approved_for_all, owner, payer_account_id, spender, timestamp_range, token_id)
            select approved_for_all, owner, payer_account_id, spender, int8range(lower(timestamp_range), null), token_id
            from nft_allowance_history
            where owner = :owner and spender = :spender and token_id = :tokenId
              and upper(timestamp_range) = :timestamp
            """;

    private static final RowMapper<Revocation> REVOCATION_ROW_MAPPER = new DataClassRowMapper<>(Revocation.class);

    // The timestamp_range && condition is redundant, it's to use the gist index on timestamp_range
    private static final String SELECT_REVOCATION_SQL = """
            with approve_allowance as (
              select consensus_timestamp
              from transaction
              where type = :type
                and consensus_timestamp >= :fromTimestamp
                and consensus_timestamp < :toTimestamp
            ), revocation as (
              select owner, spender, token_id, lower(timestamp_range) as timestamp, null::bigint as upper_timestamp
              from nft_allowance
              where approved_for_all = false
                and lower(timestamp_range) in (select consensus_timestamp from approve_allowance)
              union all
              select owner, spender, token_id, lower(timestamp_range), upper(timestamp_range)
              from nft_allowance_history
              where approved_for_all = false
                and timestamp_range && int8range(:fromTimestamp, :toTimestamp)
                and lower(timestamp_range) in (select consensus_timestamp from approve_allowance)
            )
            select r.*
            from revocation r
            join nft n on n.account_id = r.owner
              and n.spender = r.spender
              and n.token_id = r.token_id
              and lower(n.timestamp_range) = r.timestamp
            where n.delegating_spender is not null
            union
            select r.*
            from revocation r
            join nft_history n on n.account_id = r.owner
              and n.spender = r.spender
              and n.token_id = r.token_id
              and n.timestamp_range @> r.timestamp
              and lower(n.timestamp_range) = r.timestamp
            where n.delegating_spender is not null
            order by timestamp
            """;

    private static final Map<String, Long> START_TIMESTAMPS = Map.of(
            HederaNetwork.MAINNET, MAINNET_START_TIMESTAMP,
            HederaNetwork.TESTNET, TESTNET_START_TIMESTAMP);

    private final ImporterProperties importerProperties;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    FixDelegatedNftApproveForAllMigration(
            final ImporterProperties importerProperties, final ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        super(importerProperties.getMigration());
        this.importerProperties = importerProperties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public String getDescription() {
        return "Restore nft approved for all allowances wrongly revoked by delegated nft allowances since HAPI 0.77.0";
    }

    @Override
    protected void doMigrate() {
        final var startTimestamp = START_TIMESTAMPS.get(importerProperties.getNetwork());
        if (startTimestamp == null) {
            log.info("Skipping migration since it only applies to mainnet and testnet");
            return;
        }

        final var jdbcTemplate = jdbcTemplateProvider.getObject();
        final var lastConsensusEnd = jdbcTemplate.queryForObject(END_TIMESTAMP_SQL, Long.class);
        if (lastConsensusEnd == null || lastConsensusEnd < startTimestamp) {
            log.info("Skipping migration since there is no data since the first HAPI 0.77.0 transaction");
            return;
        }

        final var namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        final var stopwatch = Stopwatch.createStarted();
        transactionOperations(jdbcTemplate).executeWithoutResult(_ -> {
            int count = 0;
            long minutes = 1L;
            // Fix the oldest first, so when the same allowance is wrongly revoked more than once, the later fix
            // restores the previous revision already extended by the earlier fix
            for (long fromTimestamp = startTimestamp; fromTimestamp <= lastConsensusEnd; fromTimestamp += INTERVAL) {
                final long toTimestamp = fromTimestamp + INTERVAL;
                count += fixRevokedAllowance(namedJdbcTemplate, fromTimestamp, toTimestamp);

                final long elapsed = stopwatch.elapsed(TimeUnit.MINUTES);
                if (elapsed >= minutes) {
                    log.info(
                            "Fixed {} nft approved for all allowances before consensus timestamp {} of in {}",
                            count,
                            toTimestamp,
                            stopwatch);
                    minutes = elapsed + 1;
                }
            }

            log.info("Fixed {} nft approved for all allowances in {}", count, stopwatch);
        });
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version nft_history was added, the last of the tables the migration reads. Since in V2, the tables were
        // created with the required schema, it's unnecessary to use a V2 specific minimum version.
        return MigrationVersion.fromVersion("1.81.1");
    }

    private static void batchUpdate(
            final NamedParameterJdbcTemplate namedJdbcTemplate,
            final List<SqlParameterSource> parameters,
            final String... statements) {
        if (parameters.isEmpty()) {
            return;
        }

        final var batchArgs = parameters.toArray(SqlParameterSource[]::new);
        for (final var statement : statements) {
            namedJdbcTemplate.batchUpdate(statement, batchArgs);
        }
    }

    private static int fixRevokedAllowance(
            final NamedParameterJdbcTemplate namedJdbcTemplate, final long fromTimestamp, final long toTimestamp) {
        final var range = new MapSqlParameterSource()
                .addValue("fromTimestamp", fromTimestamp)
                .addValue("toTimestamp", toTimestamp)
                .addValue("type", TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId());
        final var revocations = namedJdbcTemplate.query(SELECT_REVOCATION_SQL, range, REVOCATION_ROW_MAPPER);
        if (revocations.isEmpty()) {
            return 0;
        }

        final var current = new ArrayList<SqlParameterSource>();
        final var history = new ArrayList<SqlParameterSource>();

        for (final var revocation : revocations) {
            final var parameters = new MapSqlParameterSource()
                    .addValue("owner", revocation.owner())
                    .addValue("spender", revocation.spender())
                    .addValue("timestamp", revocation.timestamp())
                    .addValue("tokenId", revocation.tokenId())
                    .addValue("upperTimestamp", revocation.upperTimestamp());
            (revocation.upperTimestamp() == null ? current : history).add(parameters);
        }

        // The batches keep the revocations oldest first, so for consecutive revocations, the previous revision is
        // extended past each one in turn. Fix the history revocations before the current ones, which restore the
        // previous revisions already extended
        batchUpdate(namedJdbcTemplate, history, DELETE_HISTORY_SQL, EXTEND_PREVIOUS_SQL);
        batchUpdate(namedJdbcTemplate, current, DELETE_CURRENT_SQL, RESTORE_PREVIOUS_SQL, DELETE_PREVIOUS_SQL);
        return revocations.size();
    }

    private static TransactionTemplate transactionOperations(final JdbcTemplate jdbcTemplate) {
        final var transactionManager =
                new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    private record Revocation(
            long owner,
            long spender,
            long timestamp,
            long tokenId,
            @Nullable Long upperTimestamp) {}
}
