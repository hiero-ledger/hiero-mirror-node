// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;

import com.google.common.base.Stopwatch;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
final class AccountInfoKeyMigration extends RepeatableMigration {

    static final Instant KEY_SNAPSHOT = Instant.parse("2026-10-19T20:00:00.767739Z");
    static final long KEY_SNAPSHOT_TIMESTAMP = DomainUtils.convertToNanosMax(KEY_SNAPSHOT);

    private static final String SELECT_HISTORICAL_KEY = """
            select key as previous_key, lower(timestamp_range) as lower_timestamp
            from entity_history
            where id = :id
              and timestamp_range @> :timestamp
            """;

    private static final String UPDATE_MATCHING_HISTORICAL_KEYS = """
            update entity_history
            set key = :key,
                public_key = coalesce(:publicKey, public_key)
            where id = :id
              and lower(timestamp_range) >= :lowerTimestamp
              and key is not distinct from cast(:previousKey as bytea)
            """;

    private static final String UPDATE_MATCHING_ENTITY_KEY = """
            update entity
            set key = :key,
                public_key = coalesce(:publicKey, public_key)
            where id = :id
              and lower(timestamp_range) >= :lowerTimestamp
              and key is not distinct from cast(:previousKey as bytea)
            """;

    private static final String COPY_ENTITY_TO_HISTORY = """
            insert into entity_history (
                alias, auto_renew_account_id, auto_renew_period, balance, balance_timestamp, created_timestamp,
                decline_reward, delegation_address, deleted, ethereum_nonce, evm_address, expiration_timestamp, id,
                key, max_automatic_token_associations, memo, num, obtainer_id, permanent_removal, proxy_account_id,
                public_key, realm, receiver_sig_required, shard, stake_period_start, staked_account_id,
                staked_node_id, timestamp_range, type)
            select alias, auto_renew_account_id, auto_renew_period, balance, balance_timestamp, created_timestamp,
                   decline_reward, delegation_address, deleted, ethereum_nonce, evm_address, expiration_timestamp, id,
                   key, max_automatic_token_associations, memo, num, obtainer_id, permanent_removal, proxy_account_id,
                   public_key, realm, receiver_sig_required, shard, stake_period_start, staked_account_id,
                   staked_node_id, int8range(lower(timestamp_range), :snapshot), type
            from entity
            where id = :id
            """;

    private final BlockStreamResolver blockStreamResolver;
    private final ObjectProvider<EntityRepository> entityRepositoryProvider;
    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ImporterProperties importerProperties;
    private final boolean v2;

    @Value("classpath:accountInfoKeyUpdates.txt.gz")
    private Resource accountInfoKeyUpdatesPath;

    AccountInfoKeyMigration(
            final BlockStreamResolver blockStreamResolver,
            final ObjectProvider<EntityRepository> entityRepositoryProvider,
            final Environment environment,
            final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            final ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.blockStreamResolver = blockStreamResolver;
        this.entityRepositoryProvider = entityRepositoryProvider;
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.importerProperties = importerProperties;
        v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Update historical account keys that differed before the key snapshot";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return blockStreamResolver.getMinimumMigrationVersion(v2);
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!importerProperties.isImportHistoricalAccountInfo()) {
            log.info("Skipping migration since importing historical account information is disabled");
            return;
        }

        if (!ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
            log.info("Skipping migration since it only applies to mainnet");
            return;
        }

        final Instant startDate = Objects.requireNonNullElseGet(importerProperties.getStartDate(), Instant::now);
        if (startDate.isAfter(KEY_SNAPSHOT)) {
            log.info("Skipping migration since start date {} is after the export date {}", startDate, KEY_SNAPSHOT);
            return;
        }

        final var stopwatch = Stopwatch.createStarted();
        log.info("Updating account keys that differed before {}", KEY_SNAPSHOT);
        try (var reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(accountInfoKeyUpdatesPath.getInputStream()), StandardCharsets.UTF_8))) {
            final long count = reader.lines()
                    .map(this::parse)
                    .filter(Objects::nonNull)
                    .map(this::updateHistoricalKey)
                    .filter(Boolean::booleanValue)
                    .count();
            log.info("Successfully updated {} accounts in {}", count, stopwatch);
        }
    }

    /**
     * Writes the corrected key onto the account version that was current at {@link #KEY_SNAPSHOT}, then onto every
     * later {@code entity_history} row and the current {@code entity} row that still have that version's previous key.
     */
    boolean updateHistoricalKey(final AccountInfo accountInfo) {
        if (!accountInfo.hasKey()) {
            return false;
        }

        final long id = EntityId.of(accountInfo.getAccountID()).getId();
        final byte[] correctedKey = accountInfo.getKey().toByteArray();
        final var current = entityRepositoryProvider.getObject().findById(id).orElse(null);
        if (current == null) {
            return false;
        }
        if (activeAtSnapshot(current.getTimestampRange())) {
            return updateCurrentKey(current, correctedKey);
        }

        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var snapshots = jdbcOperations.query(
                SELECT_HISTORICAL_KEY,
                new MapSqlParameterSource("id", id).addValue("timestamp", KEY_SNAPSHOT_TIMESTAMP),
                (resultSet, rowNum) ->
                        new SnapshotKey(resultSet.getBytes("previous_key"), resultSet.getLong("lower_timestamp")));
        if (snapshots.isEmpty()) {
            return false;
        }

        // The snapshot timestamp fits one entity_history row, so the collection contains one element.
        final var snapshot = snapshots.getFirst();
        if (Arrays.equals(snapshot.previousKey(), correctedKey)) {
            return false;
        }

        final var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("key", correctedKey)
                .addValue("lowerTimestamp", snapshot.lowerTimestamp())
                .addValue("previousKey", snapshot.previousKey(), Types.BINARY)
                .addValue("publicKey", DomainUtils.getPublicKey(correctedKey), Types.VARCHAR);
        jdbcOperations.update(UPDATE_MATCHING_HISTORICAL_KEYS, params);
        jdbcOperations.update(UPDATE_MATCHING_ENTITY_KEY, params);
        return true;
    }

    private AccountInfo parse(final String line) {
        try {
            if (!line.isBlank()) {
                final byte[] data = Base64.getDecoder().decode(line);
                return parseProtobuf(data, AccountInfo::parseFrom);
            }
        } catch (Exception e) {
            log.error("Unable to parse AccountInfo from line: {}", line, e);
        }

        return null;
    }

    private boolean updateCurrentKey(final Entity current, final byte[] correctedKey) {
        final byte[] previousKey =
                current.getKey() == null ? null : current.getKey().clone();
        if (Arrays.equals(previousKey, correctedKey)) {
            return false;
        }

        final String previousPublicKey = current.getPublicKey();
        final var range = current.getTimestampRange();
        final long lower = range.lowerEndpoint();
        if (lower < KEY_SNAPSHOT_TIMESTAMP) {
            // Copy while the managed entity is still clean, so Hibernate does not flush the new key first.
            final var jdbcOperations = jdbcOperationsProvider.getObject();
            final var params = new MapSqlParameterSource()
                    .addValue("id", current.getId())
                    .addValue("snapshot", KEY_SNAPSHOT_TIMESTAMP);
            jdbcOperations.update(COPY_ENTITY_TO_HISTORY, params);
            current.setTimestampRange(
                    range.hasUpperBound()
                            ? Range.range(
                                    KEY_SNAPSHOT_TIMESTAMP,
                                    BoundType.CLOSED,
                                    range.upperEndpoint(),
                                    range.upperBoundType())
                            : Range.atLeast(KEY_SNAPSHOT_TIMESTAMP));
        }

        current.setKey(correctedKey);
        // A key list or threshold key has no single public key. Keep the previous value instead of clearing it.
        if (current.getPublicKey().isEmpty()) {
            current.setPublicKey(previousPublicKey);
        }
        entityRepositoryProvider.getObject().save(current);
        return true;
    }

    private boolean activeAtSnapshot(final Range<Long> timestampRange) {
        return timestampRange != null && timestampRange.contains(KEY_SNAPSHOT_TIMESTAMP);
    }

    private record SnapshotKey(byte[] previousKey, long lowerTimestamp) {}
}
