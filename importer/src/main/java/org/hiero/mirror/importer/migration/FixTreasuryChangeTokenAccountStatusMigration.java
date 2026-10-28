// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.collect.Lists;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Set new treasury relationships to unfrozen and KYC granted without inspecting token keys. Readers give the token's
 * NOT_APPLICABLE statuses precedence over these relationship statuses. For each treasury change, the snapshot of the new
 * treasury's token account at the treasury change is split so the snapshot prior to the treasury change is preserved in
 * history. The freeze and KYC statuses are then fixed independently, in every snapshot from the treasury change until
 * the first subsequent snapshot that explicitly sets that status or resets the association.
 */
@Named
final class FixTreasuryChangeTokenAccountStatusMigration extends ConfigurableJavaMigration {

    private static final int BATCH_SIZE = 10_000;

    private static final String CURRENT_TABLE = "token_account";

    private static final int GRANTED = TokenKycStatusEnum.GRANTED.ordinal();

    private static final String HISTORY_TABLE = "token_account_history";

    // Copies the split snapshot, which now starts at the treasury change, into history ending at the treasury change
    private static final String INSERT_HISTORY = """
            insert into token_account_history (
              account_id,
              associated,
              automatic_association,
              balance,
              balance_timestamp,
              created_timestamp,
              freeze_status,
              kyc_status,
              timestamp_range,
              token_id
            )
            select
              account_id,
              associated,
              automatic_association,
              balance,
              balance_timestamp,
              created_timestamp,
              freeze_status,
              kyc_status,
              int8range(:lower, :timestamp),
              token_id
            from %s
            where account_id = :accountId
              and token_id = :tokenId
              and lower(timestamp_range) = :timestamp
            """;

    private static final String SELECT_TOKEN_ACCOUNT_SNAPSHOTS = """
            select
              associated,
              created_timestamp,
              freeze_status,
              kyc_status,
              lower(timestamp_range) as timestamp_lower,
              true as current
            from token_account
            where account_id = :accountId and token_id = :tokenId
            union all
            select
              associated,
              created_timestamp,
              freeze_status,
              kyc_status,
              lower(timestamp_range) as timestamp_lower,
              false as current
            from token_account_history
            where account_id = :accountId and token_id = :tokenId
            order by timestamp_lower
            """;

    private static final String SELECT_TRANSACTION_TYPES = """
            select consensus_timestamp, type
            from transaction
            where consensus_timestamp = any(:timestamps::bigint[])
              and consensus_timestamp between :minTimestamp and :maxTimestamp
            """;

    private static final String SELECT_TREASURY_CHANGES = """
            with token_version as (
              select token_id, treasury_account_id, lower(timestamp_range) as timestamp
              from token
              union all
              select token_id, treasury_account_id, lower(timestamp_range) as timestamp
              from token_history
            ), change as (
              select
                token_id,
                treasury_account_id,
                timestamp,
                lag(treasury_account_id) over w as previous_treasury_account_id
              from token_version
              window w as (partition by token_id order by timestamp)
            )
            select token_id, treasury_account_id, timestamp
            from change
            where previous_treasury_account_id is not null
              and previous_treasury_account_id <> treasury_account_id
            order by timestamp
            """;

    private static final int UNFROZEN = TokenFreezeStatusEnum.UNFROZEN.ordinal();

    private static final String UPDATE_FREEZE_STATUS = """
            update %s
            set freeze_status = :status
            where account_id = :accountId
              and token_id = :tokenId
              and lower(timestamp_range) >= :timestamp
              and lower(timestamp_range) < :end
            """;

    private static final String UPDATE_KYC_STATUS = """
            update %s
            set kyc_status = :status
            where account_id = :accountId
              and token_id = :tokenId
              and lower(timestamp_range) >= :timestamp
              and lower(timestamp_range) < :end
            """;

    private static final String UPDATE_LOWER = """
            update %s
            set timestamp_range = int8range(:timestamp, upper(timestamp_range))
            where account_id = :accountId
              and token_id = :tokenId
              and lower(timestamp_range) = :lower
            """;

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final boolean v2;

    public FixTreasuryChangeTokenAccountStatusMigration(
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.transactionOperationsProvider = transactionOperationsProvider;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Unfreeze and grant KYC to the token account of new treasuries changed by token updates";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.35.1") : MigrationVersion.fromVersion("1.130.1");
    }

    @Override
    protected void doMigrate() {
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var transactionOperations = transactionOperationsProvider.getObject();
        final var treasuryChanges = jdbcOperations.query(
                SELECT_TREASURY_CHANGES,
                (rs, rowNum) -> new TreasuryChange(
                        rs.getLong("treasury_account_id"), rs.getLong("token_id"), rs.getLong("timestamp")));

        long count = 0;
        for (final var treasuryChange : treasuryChanges) {
            // The JDBC operations don't participate in the Flyway transaction, so each repair is made atomic on its
            // own. A retry after a failure then sees a repair either fully applied, and skips it, or not applied.
            final var fixed = transactionOperations.execute(s -> fixTreasuryChange(jdbcOperations, treasuryChange));
            if (Boolean.TRUE.equals(fixed)) {
                count++;
            }
        }

        log.info("Fixed freeze and KYC status of {} treasury token accounts", count);
    }

    private boolean fixTreasuryChange(
            final NamedParameterJdbcOperations jdbcOperations, final TreasuryChange treasuryChange) {
        final var params = new MapSqlParameterSource()
                .addValue("accountId", treasuryChange.accountId())
                .addValue("timestamp", treasuryChange.timestamp())
                .addValue("tokenId", treasuryChange.tokenId());
        final var snapshots = jdbcOperations.query(
                SELECT_TOKEN_ACCOUNT_SNAPSHOTS,
                params,
                (rs, rowNum) -> new TokenAccountSnapshot(
                        rs.getBoolean("associated"),
                        rs.getObject("created_timestamp", Long.class),
                        rs.getBoolean("current"),
                        rs.getObject("freeze_status", Integer.class),
                        rs.getObject("kyc_status", Integer.class),
                        rs.getLong("timestamp_lower")));

        // The snapshot in effect at the treasury change
        int index = -1;
        for (int i = 0; i < snapshots.size() && snapshots.get(i).timestampLower() <= treasuryChange.timestamp(); i++) {
            index = i;
        }

        if (index == -1 || !snapshots.get(index).associated()) {
            return false;
        }

        final var atChange = snapshots.get(index);
        final boolean unfreeze = !Objects.equals(atChange.freezeStatus(), UNFROZEN);
        final boolean grantKyc = !Objects.equals(atChange.kycStatus(), GRANTED);
        if (!unfreeze && !grantKyc) {
            return false;
        }

        final var subsequent = snapshots.subList(index + 1, snapshots.size());
        final var transactionTypes = getTransactionTypes(jdbcOperations, subsequent);
        long freezeEnd = Long.MAX_VALUE;
        long kycEnd = Long.MAX_VALUE;
        var previous = atChange;
        for (final var snapshot : subsequent) {
            if (!snapshot.associated() || !Objects.equals(snapshot.createdTimestamp(), atChange.createdTimestamp())) {
                // Dissociated or associated again, both statuses are reset
                freezeEnd = Math.min(freezeEnd, snapshot.timestampLower());
                kycEnd = Math.min(kycEnd, snapshot.timestampLower());
                break;
            }

            final boolean freezeChanged = !Objects.equals(snapshot.freezeStatus(), previous.freezeStatus());
            final boolean kycChanged = !Objects.equals(snapshot.kycStatus(), previous.kycStatus());
            final var type = transactionTypes.get(snapshot.timestampLower());
            // Without the transaction, a snapshot with neither status changed could have set either of them
            final boolean unknown = type == null && !freezeChanged && !kycChanged;
            if (freezeChanged || unknown || isFreezeType(type)) {
                freezeEnd = Math.min(freezeEnd, snapshot.timestampLower());
            }
            if (kycChanged || unknown || isKycType(type)) {
                kycEnd = Math.min(kycEnd, snapshot.timestampLower());
            }

            previous = snapshot;
        }

        final var table = atChange.current() ? CURRENT_TABLE : HISTORY_TABLE;
        if (atChange.timestampLower() < treasuryChange.timestamp()) {
            final var splitParams =
                    new MapSqlParameterSource(params.getValues()).addValue("lower", atChange.timestampLower());
            jdbcOperations.update(UPDATE_LOWER.formatted(table), splitParams);
            jdbcOperations.update(INSERT_HISTORY.formatted(table), splitParams);
        }

        if (unfreeze) {
            updateStatus(jdbcOperations, UPDATE_FREEZE_STATUS, params, UNFROZEN, freezeEnd);
        }
        if (grantKyc) {
            updateStatus(jdbcOperations, UPDATE_KYC_STATUS, params, GRANTED, kycEnd);
        }

        return true;
    }

    private static Map<Long, Integer> getTransactionTypes(
            final NamedParameterJdbcOperations jdbcOperations, final List<TokenAccountSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return Map.of();
        }

        final var transactionTypes = new HashMap<Long, Integer>();
        for (final var batch : Lists.partition(snapshots, BATCH_SIZE)) {
            final var timestamps = new Long[batch.size()];
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] = batch.get(i).timestampLower();
            }

            final var params = new MapSqlParameterSource("timestamps", timestamps)
                    .addValue("minTimestamp", timestamps[0])
                    .addValue("maxTimestamp", timestamps[timestamps.length - 1]);
            jdbcOperations.query(SELECT_TRANSACTION_TYPES, params, (RowCallbackHandler)
                    rs -> transactionTypes.put(rs.getLong("consensus_timestamp"), rs.getInt("type")));
        }
        return transactionTypes;
    }

    private static boolean isFreezeType(@Nullable Integer type) {
        return type != null
                && (type == TransactionType.TOKENFREEZE.getProtoId()
                        || type == TransactionType.TOKENUNFREEZE.getProtoId());
    }

    private static boolean isKycType(@Nullable Integer type) {
        return type != null
                && (type == TransactionType.TOKENGRANTKYC.getProtoId()
                        || type == TransactionType.TOKENREVOKEKYC.getProtoId());
    }

    private static void updateStatus(
            final NamedParameterJdbcOperations jdbcOperations,
            final String sql,
            final MapSqlParameterSource params,
            final int status,
            final long end) {
        final var statusParams = new MapSqlParameterSource(params.getValues())
                .addValue("end", end)
                .addValue("status", status);
        jdbcOperations.update(sql.formatted(CURRENT_TABLE), statusParams);
        jdbcOperations.update(sql.formatted(HISTORY_TABLE), statusParams);
    }

    private record TokenAccountSnapshot(
            boolean associated,
            @Nullable Long createdTimestamp,
            boolean current,
            @Nullable Integer freezeStatus,
            @Nullable Integer kycStatus,
            long timestampLower) {}

    private record TreasuryChange(long accountId, long tokenId, long timestamp) {}
}
