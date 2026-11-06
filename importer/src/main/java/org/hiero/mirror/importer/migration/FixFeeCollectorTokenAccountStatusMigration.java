// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.collect.Lists;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.util.Version;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Since services 0.49.0, a token create only unfreezes and grants KYC to the treasury. The fee collectors
 * automatically associated with the new token start with the token's default statuses: frozen if the token has a
 * freeze key and freezes by default, and KYC revoked if the token has a KYC key. The importer used to unfreeze and
 * grant KYC to them as well, so this migration resets their statuses to the token's defaults. A status is fixed in
 * every snapshot from the token creation until the first subsequent snapshot that explicitly sets that status or
 * resets the association, or until a token update makes the collector the treasury. In the latter case, the snapshot
 * in effect at the treasury change is split so the snapshot from the treasury change keeps the unfrozen and KYC
 * granted statuses.
 *
 * <p>Since services 0.77.0, an empty key list KYC key (the HIP-540 removal sentinel) on token create disables KYC, so
 * every account associated with the token is KYC granted. Such a key can't be updated, so the KYC status of every
 * snapshot of these tokens is set to granted, which the accounts associated with them inherit.
 */
@Named
final class FixFeeCollectorTokenAccountStatusMigration extends ConfigurableJavaMigration {

    private static final int BATCH_SIZE = 200;

    private static final String CURRENT_TABLE = "token_account";

    // The empty key list (the HIP-540 removal sentinel), which disables KYC since services 0.77.0
    private static final byte[] EMPTY_KEY_LIST =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build().toByteArray();

    private static final int FROZEN = TokenFreezeStatusEnum.FROZEN.ordinal();

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

    private static final int REVOKED = TokenKycStatusEnum.REVOKED.ordinal();

    // Collectors of fixed fees charged in the token itself and of fractional fees are automatically associated with the
    // new token. A fee schedule snapshot starting at the token creation is the fee schedule of the token create.
    private static final String SELECT_COLLECTORS = """
            with fee_schedule as (
              select entity_id, fixed_fees, fractional_fees, lower(timestamp_range) as timestamp
              from custom_fee
              union all
              select entity_id, fixed_fees, fractional_fees, lower(timestamp_range) as timestamp
              from custom_fee_history
            )
            select entity_id as token_id, timestamp, (fee ->> 'collector_account_id')::bigint as account_id
            from fee_schedule, jsonb_array_elements(coalesce(fixed_fees, '[]'::jsonb)) as fee
            where (fee ->> 'denominating_token_id')::bigint = entity_id
            union
            select entity_id as token_id, timestamp, (fee ->> 'collector_account_id')::bigint as account_id
            from fee_schedule, jsonb_array_elements(coalesce(fractional_fees, '[]'::jsonb)) as fee
            """;

    // The record file containing the timestamp. The candidate is found by consensus_end alone so the index is used, and
    // nothing is returned when the containing record file is unavailable, e.g. pruned by retention.
    private static final String SELECT_HAPI_VERSION = """
            select hapi_version_major, hapi_version_minor, hapi_version_patch
            from (
              select consensus_start, hapi_version_major, hapi_version_minor, hapi_version_patch
              from record_file
              where consensus_end >= :timestamp
              order by consensus_end
              limit 1
            ) as candidate
            where consensus_start <= :timestamp
            """;

    // Tokens created with an empty key list KYC key whose KYC status hasn't been fixed
    private static final String SELECT_KYC_DISABLED_TOKENS = """
            select token_id, created_timestamp
            from token
            where lower(timestamp_range) = created_timestamp and kyc_key = :emptyKeyList and kyc_status = :revoked
            union all
            select token_id, created_timestamp
            from token_history
            where lower(timestamp_range) = created_timestamp and kyc_key = :emptyKeyList and kyc_status = :revoked
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

    // The token snapshots at the token creation with a status that differs from the treasury's
    private static final String SELECT_TOKEN_CREATIONS = """
            select token_id, created_timestamp, freeze_status, kyc_key, kyc_status, treasury_account_id
            from token
            where token_id = any(:tokenIds::bigint[])
              and lower(timestamp_range) = created_timestamp
              and (freeze_status = :frozen or kyc_status = :revoked)
            union all
            select token_id, created_timestamp, freeze_status, kyc_key, kyc_status, treasury_account_id
            from token_history
            where token_id = any(:tokenIds::bigint[])
              and lower(timestamp_range) = created_timestamp
              and (freeze_status = :frozen or kyc_status = :revoked)
            """;

    private static final String SELECT_TRANSACTION_TYPES = """
            select consensus_timestamp, type
            from transaction
            where consensus_timestamp = any(:timestamps::bigint[])
              and consensus_timestamp between :minTimestamp and :maxTimestamp
            """;

    // The first token update after the token creation that makes the account the treasury
    private static final String SELECT_TREASURY_CHANGE = """
            select min(timestamp) from (
              select lower(timestamp_range) as timestamp
              from token
              where token_id = :tokenId
                and treasury_account_id = :accountId
                and lower(timestamp_range) > :createdTimestamp
              union all
              select lower(timestamp_range) as timestamp
              from token_history
              where token_id = :tokenId
                and treasury_account_id = :accountId
                and lower(timestamp_range) > :createdTimestamp
            ) as treasury_change
            """;

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

    private static final String UPDATE_TOKEN_KYC_STATUS = """
            update %s
            set kyc_status = :granted
            where token_id = :tokenId and kyc_status = :revoked
            """;

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final boolean v2;

    public FixFeeCollectorTokenAccountStatusMigration(
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
        return "Reset the freeze and KYC status of fee collectors automatically associated by token creates and grant"
                + " KYC for tokens created with KYC disabled";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.35.3") : MigrationVersion.fromVersion("1.130.3");
    }

    @Override
    protected void doMigrate() {
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var transactionOperations = transactionOperationsProvider.getObject();
        final var collectors = jdbcOperations.query(
                SELECT_COLLECTORS,
                (rs, rowNum) ->
                        new Collector(rs.getLong("account_id"), rs.getLong("token_id"), rs.getLong("timestamp")));
        final var tokenCreations = getTokenCreations(jdbcOperations, collectors);
        final var hapiVersions = new HashMap<Long, Version>();

        long count = 0;
        for (final var collector : collectors) {
            final var tokenCreation = tokenCreations.get(collector.tokenId());
            if (tokenCreation == null
                    || tokenCreation.createdTimestamp() != collector.timestamp()
                    || tokenCreation.treasuryAccountId() == collector.accountId()) {
                continue;
            }

            // Prior to services 0.49.0, a token create unfroze and granted KYC to every automatically associated
            // account. The version is unset when the record file is unavailable, so the collector is skipped.
            final var hapiVersion = hapiVersions.computeIfAbsent(
                    tokenCreation.createdTimestamp(), timestamp -> getHapiVersion(jdbcOperations, timestamp));
            if (hapiVersion.isLessThan(RecordFile.HAPI_VERSION_0_49_0)) {
                continue;
            }

            // Since services 0.77.0, an empty key list KYC key disables KYC, so the collector is KYC granted
            final boolean kycDisabled =
                    tokenCreation.emptyKycKey() && hapiVersion.isGreaterThanOrEqualTo(RecordFile.HAPI_VERSION_0_77_0);

            // The JDBC operations don't participate in the Flyway transaction, so each repair is made atomic on its
            // own. A retry after a failure then sees a repair either fully applied, and skips it, or not applied.
            final var fixed = transactionOperations.execute(
                    s -> fixCollector(jdbcOperations, collector, tokenCreation, kycDisabled));
            if (Boolean.TRUE.equals(fixed)) {
                count++;
            }
        }

        log.info("Fixed freeze and KYC status of {} fee collector token accounts", count);

        final long kycDisabledCount = fixKycDisabledTokens(jdbcOperations, transactionOperations, hapiVersions);
        log.info("Granted KYC for {} tokens created with KYC disabled", kycDisabledCount);
    }

    private boolean fixCollector(
            final NamedParameterJdbcOperations jdbcOperations,
            final Collector collector,
            final TokenCreation tokenCreation,
            final boolean kycDisabled) {
        final long createdTimestamp = tokenCreation.createdTimestamp();
        final var params = new MapSqlParameterSource()
                .addValue("accountId", collector.accountId())
                .addValue("createdTimestamp", createdTimestamp)
                .addValue("tokenId", collector.tokenId());
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

        int index = -1;
        for (int i = 0; i < snapshots.size(); i++) {
            if (snapshots.get(i).timestampLower() == createdTimestamp) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return false;
        }

        final var atCreation = snapshots.get(index);
        if (!atCreation.associated() || !Objects.equals(atCreation.createdTimestamp(), createdTimestamp)) {
            return false;
        }

        final boolean freeze =
                tokenCreation.freezeStatus() == FROZEN && !Objects.equals(atCreation.freezeStatus(), FROZEN);
        final boolean revokeKyc = !kycDisabled
                && tokenCreation.kycStatus() == REVOKED
                && !Objects.equals(atCreation.kycStatus(), REVOKED);
        if (!freeze && !revokeKyc) {
            return false;
        }

        final var treasuryChange = jdbcOperations.queryForObject(SELECT_TREASURY_CHANGE, params, Long.class);
        final long treasuryEnd = treasuryChange != null ? treasuryChange : Long.MAX_VALUE;
        final var subsequent = new ArrayList<TokenAccountSnapshot>();
        for (int i = index + 1; i < snapshots.size() && snapshots.get(i).timestampLower() < treasuryEnd; i++) {
            subsequent.add(snapshots.get(i));
        }

        final var transactionTypes = getTransactionTypes(jdbcOperations, subsequent);
        long freezeEnd = treasuryEnd;
        long kycEnd = treasuryEnd;
        var previous = atCreation;
        var inEffect = atCreation;
        for (final var snapshot : subsequent) {
            inEffect = snapshot;
            if (!snapshot.associated() || !Objects.equals(snapshot.createdTimestamp(), createdTimestamp)) {
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

        // The snapshot in effect at the treasury change is split if it starts before the treasury change and one of
        // its statuses is fixed. Its part from the treasury change keeps the unfrozen and KYC granted statuses.
        final long inEffectLower = inEffect.timestampLower();
        final boolean split = treasuryEnd != Long.MAX_VALUE
                && !hasSnapshotAt(snapshots, treasuryEnd)
                && ((freeze && freezeEnd > inEffectLower) || (revokeKyc && kycEnd > inEffectLower));
        if (split) {
            final var table = inEffect.current() ? CURRENT_TABLE : HISTORY_TABLE;
            final var splitParams = new MapSqlParameterSource(params.getValues())
                    .addValue("lower", inEffectLower)
                    .addValue("timestamp", treasuryEnd);
            jdbcOperations.update(UPDATE_LOWER.formatted(table), splitParams);
            jdbcOperations.update(INSERT_HISTORY.formatted(table), splitParams);
        }

        if (freeze) {
            updateStatus(jdbcOperations, UPDATE_FREEZE_STATUS, params, FROZEN, freezeEnd);
        }
        if (revokeKyc) {
            updateStatus(jdbcOperations, UPDATE_KYC_STATUS, params, REVOKED, kycEnd);
        }

        return true;
    }

    private static long fixKycDisabledTokens(
            final NamedParameterJdbcOperations jdbcOperations,
            final TransactionOperations transactionOperations,
            final Map<Long, Version> hapiVersions) {
        final var params = new MapSqlParameterSource()
                .addValue("emptyKeyList", EMPTY_KEY_LIST)
                .addValue("granted", GRANTED)
                .addValue("revoked", REVOKED);
        final var tokens = jdbcOperations.query(
                SELECT_KYC_DISABLED_TOKENS,
                params,
                (rs, rowNum) -> new TokenCreated(rs.getLong("token_id"), rs.getLong("created_timestamp")));

        long count = 0;
        for (final var token : tokens) {
            // Prior to services 0.77.0, an empty key list KYC key is a KYC key
            final var hapiVersion = hapiVersions.computeIfAbsent(
                    token.createdTimestamp(), timestamp -> getHapiVersion(jdbcOperations, timestamp));
            if (hapiVersion.isLessThan(RecordFile.HAPI_VERSION_0_77_0)) {
                continue;
            }

            final var tokenParams = new MapSqlParameterSource(params.getValues()).addValue("tokenId", token.tokenId());
            transactionOperations.executeWithoutResult(s -> {
                jdbcOperations.update(UPDATE_TOKEN_KYC_STATUS.formatted("token"), tokenParams);
                jdbcOperations.update(UPDATE_TOKEN_KYC_STATUS.formatted("token_history"), tokenParams);
            });
            count++;
        }
        return count;
    }

    private static Version getHapiVersion(final NamedParameterJdbcOperations jdbcOperations, final long timestamp) {
        final var versions = jdbcOperations.query(
                SELECT_HAPI_VERSION,
                new MapSqlParameterSource("timestamp", timestamp),
                (rs, rowNum) -> new Version(
                        rs.getInt("hapi_version_major"),
                        rs.getInt("hapi_version_minor"),
                        rs.getInt("hapi_version_patch")));
        return versions.isEmpty() ? RecordFile.HAPI_VERSION_NOT_SET : versions.getFirst();
    }

    private static Map<Long, TokenCreation> getTokenCreations(
            final NamedParameterJdbcOperations jdbcOperations, final List<Collector> collectors) {
        final var tokenIds = new LinkedHashSet<Long>();
        for (final var collector : collectors) {
            tokenIds.add(collector.tokenId());
        }

        final var tokenCreations = new HashMap<Long, TokenCreation>();
        for (final var batch : Lists.partition(List.copyOf(tokenIds), BATCH_SIZE)) {
            final var params = new MapSqlParameterSource("tokenIds", batch.toArray(Long[]::new))
                    .addValue("frozen", FROZEN)
                    .addValue("revoked", REVOKED);
            jdbcOperations.query(SELECT_TOKEN_CREATIONS, params, (RowCallbackHandler) rs -> tokenCreations.put(
                    rs.getLong("token_id"),
                    new TokenCreation(
                            rs.getLong("created_timestamp"),
                            rs.getInt("freeze_status"),
                            Arrays.equals(rs.getBytes("kyc_key"), EMPTY_KEY_LIST),
                            rs.getInt("kyc_status"),
                            rs.getLong("treasury_account_id"))));
        }
        return tokenCreations;
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

    private static boolean hasSnapshotAt(final List<TokenAccountSnapshot> snapshots, final long timestamp) {
        for (final var snapshot : snapshots) {
            if (snapshot.timestampLower() == timestamp) {
                return true;
            }
        }
        return false;
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
                .addValue("status", status)
                .addValue("timestamp", params.getValue("createdTimestamp"));
        jdbcOperations.update(sql.formatted(CURRENT_TABLE), statusParams);
        jdbcOperations.update(sql.formatted(HISTORY_TABLE), statusParams);
    }

    private record Collector(long accountId, long tokenId, long timestamp) {}

    private record TokenAccountSnapshot(
            boolean associated,
            @Nullable Long createdTimestamp,
            boolean current,
            @Nullable Integer freezeStatus,
            @Nullable Integer kycStatus,
            long timestampLower) {}

    private record TokenCreated(long tokenId, long createdTimestamp) {}

    private record TokenCreation(
            long createdTimestamp, int freezeStatus, boolean emptyKycKey, int kycStatus, long treasuryAccountId) {}
}
