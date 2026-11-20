// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
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
final class HistoricalAccountInfoMigration extends RepeatableMigration {

    static final Instant EXPORT_DATE = Instant.parse("2019-09-14T00:00:10Z");
    static final Instant KEY_SNAPSHOT = Instant.parse("2026-10-19T20:00:00.767739Z");
    static final long KEY_SNAPSHOT_TIMESTAMP = DomainUtils.convertToNanosMax(KEY_SNAPSHOT);

    private static final String SELECT_HISTORICAL_KEY = """
            select key as previous_key, upper(timestamp_range) as upper_timestamp
            from entity_history
            where id = :id
              and timestamp_range @> :timestamp
              and key is distinct from :key
            """;

    private static final String UPDATE_HISTORICAL_KEY = """
            update entity_history
            set key = :key, public_key = :publicKey
            where id = :id
              and timestamp_range @> :timestamp
              and key is distinct from :key
            """;

    private static final String SELECT_LATER_HISTORICAL_KEYS = """
            select key, lower(timestamp_range) as lower_timestamp
            from entity_history
            where id = :id
              and lower(timestamp_range) >= :upperTimestamp
            order by lower(timestamp_range)
            """;

    private static final String UPDATE_LATER_HISTORICAL_KEYS = """
            update entity_history
            set key = :key, public_key = :publicKey
            where id = :id
              and lower(timestamp_range) >= :upperTimestamp
              and lower(timestamp_range) <= :lastLower
              and key is not distinct from cast(:previousKey as bytea)
            """;

    private final BlockStreamResolver blockStreamResolver;
    private final Set<Long> contractIds = new HashSet<>();
    private final ObjectProvider<EntityRepository> entityRepositoryProvider;
    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ImporterProperties importerProperties;
    private final boolean v2;

    @Value("classpath:accountInfoContracts.txt")
    private Resource accountInfoContracts;

    @Value("classpath:accountInfo.txt.gz")
    private Resource accountInfoPath;

    @Value("classpath:accountInfoKeyUpdates.txt.gz")
    private Resource accountInfoKeyUpdatesPath;

    HistoricalAccountInfoMigration(
            BlockStreamResolver blockStreamResolver,
            ObjectProvider<EntityRepository> entityRepositoryProvider,
            Environment environment,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.blockStreamResolver = blockStreamResolver;
        this.entityRepositoryProvider = entityRepositoryProvider;
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.importerProperties = importerProperties;
        v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public String getDescription() {
        return "Import historical account information from before open access";
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

        Instant startDate = Objects.requireNonNullElseGet(importerProperties.getStartDate(), Instant::now);
        if (startDate.isAfter(EXPORT_DATE)) {
            log.info("Skipping migration since start date {} is after the export date {}", startDate, EXPORT_DATE);
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        loadContractIds();
        fixContractEntities();
        processFile(accountInfoPath, this::process, "Importing historical account information", stopwatch);
        processFile(
                accountInfoKeyUpdatesPath,
                this::updateKey,
                "Updating account keys that differed before " + KEY_SNAPSHOT,
                stopwatch);
    }

    @Override
    protected boolean skipMigration(final Configuration configuration) {
        if (super.skipMigration(configuration)) {
            return true;
        }

        if (blockStreamResolver.isInitialStateFromGenesisWrb()) {
            log.info("Skip migration since initial state is loaded from the genesis wrapped record block");
            return true;
        }

        return false;
    }

    private void processFile(
            Resource resource, Function<AccountInfo, Boolean> updater, String message, Stopwatch stopwatch)
            throws IOException {
        log.info(message);

        try (BufferedReader reader = toReader(new GZIPInputStream(resource.getInputStream()))) {
            long count = reader.lines()
                    .map(this::parse)
                    .filter(Objects::nonNull)
                    .map(updater)
                    .filter(Boolean::booleanValue)
                    .count();
            log.info("Successfully updated {} accounts in {}", count, stopwatch);
        }
    }

    private BufferedReader toReader(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    private void loadContractIds() throws IOException {
        try (BufferedReader reader = toReader(accountInfoContracts.getInputStream())) {
            reader.lines().filter(StringUtils::isNotBlank).map(Long::parseLong).forEach(contractIds::add);
            log.info("Loaded {} contract IDs", contractIds.size());
        }
    }

    private void fixContractEntities() {
        int inserted = jdbcOperationsProvider
                .getObject()
                .update(
                        "update entity set type = 'CONTRACT' where id in (:ids)",
                        new MapSqlParameterSource("ids", contractIds));
        log.info("Changed {} entity to be type contract", inserted);

        inserted = jdbcOperationsProvider
                .getObject()
                .update(
                        "update entity_history set type = 'CONTRACT' where id in (:ids)",
                        new MapSqlParameterSource("ids", contractIds));
        log.info("Changed {} entity_history to be type contract", inserted);
    }

    private AccountInfo parse(String line) {
        try {
            if (StringUtils.isNotBlank(line)) {
                byte[] data = Base64.decodeBase64(line);
                return parseProtobuf(data, AccountInfo::parseFrom);
            }
        } catch (Exception e) {
            log.error("Unable to parse AccountInfo from line: {}", line, e);
        }

        return null;
    }

    @SuppressWarnings({"deprecation", "java:S1874", "java:S3776"})
    boolean process(AccountInfo accountInfo) {
        EntityType entityType = EntityType.ACCOUNT;
        long id = EntityId.of(accountInfo.getAccountID()).getId();

        if (contractIds.contains(id)) {
            entityType = EntityType.CONTRACT;
        }

        EntityId entityId = EntityId.of(id);
        Optional<Entity> currentEntity = entityRepositoryProvider.getObject().findById(entityId.getId());
        boolean exists = currentEntity.isPresent();

        Entity entity = currentEntity.orElseGet(entityId::toEntity);
        entity.setType(entityType);
        boolean updated = !exists;

        // All regular accounts have a key so if it's missing we know it had to have been created before the reset.
        // All contract accounts don't have to have a key, but luckily in our file they do.
        if (exists && ArrayUtils.isNotEmpty(entity.getKey())) {
            log.trace("Skipping entity {} that was created after the reset", entityId);
            return false;
        }

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
            updated = true;
        }

        if (entity.getDeclineReward() == null) {
            entity.setDeclineReward(false);
        }

        // Accounts can't be undeleted
        if (entity.getDeleted() == null
                || (entity.getDeleted() != accountInfo.getDeleted() && accountInfo.getDeleted())) {
            entity.setDeleted(accountInfo.getDeleted());
            updated = true;
        }

        if (entity.getExpirationTimestamp() == null && accountInfo.hasExpirationTime()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(accountInfo.getExpirationTime()));
            updated = true;
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
            updated = true;
        }

        if (StringUtils.isEmpty(entity.getMemo())) {
            entity.setMemo(accountInfo.getMemo());
            updated = true;
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            EntityId proxyAccountEntityId = EntityId.of(accountInfo.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountEntityId); // Proxy account should get created separately
            updated |= proxyAccountEntityId != null;
        }

        if (updated) {
            log.info("Saving {} entity: {}", exists ? "existing" : "new", entity);
            entityRepositoryProvider.getObject().save(entity);
        }

        return updated;
    }

    /**
     * Merges a corrected key onto the entity version that was current at {@link #KEY_SNAPSHOT} and persists it. When
     * that version is in entity_history, the same correction is copied onto each later entity_history row and the
     * current entity row while they still have the key that version had before the correction. The copy stops at the
     * first later row whose key is different.
     */
    boolean updateKey(AccountInfo accountInfo) {
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
            if (!mergeKey(current, correctedKey)) {
                return false;
            }
            log.info("Saving corrected key for entity {}", id);
            entityRepositoryProvider.getObject().save(current);
            return true;
        }

        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("key", correctedKey)
                .addValue("publicKey", publicKey(correctedKey))
                .addValue("timestamp", KEY_SNAPSHOT_TIMESTAMP);
        final var snapshots = jdbcOperations.query(SELECT_HISTORICAL_KEY, params, (resultSet, rowNum) -> {
            final byte[] previousKey = resultSet.getBytes("previous_key");
            final long upperTimestamp = resultSet.getLong("upper_timestamp");
            return new SnapshotKey(previousKey, upperTimestamp, !resultSet.wasNull());
        });
        if (snapshots.isEmpty()) {
            return false;
        }

        jdbcOperations.update(UPDATE_HISTORICAL_KEY, params);
        log.info("Saving corrected historical key for entity {}", id);
        final var snapshot = snapshots.getFirst();
        if (snapshot.hasUpper()) {
            propagateCorrectedKey(id, snapshot.previousKey(), correctedKey, snapshot.upperTimestamp(), current);
        }
        return true;
    }

    /**
     * Copies the corrected key onto versions that start at or after {@code upperTimestamp}, in timestamp order. Stops
     * at the first version whose key is not the key the snapshot version had before the correction.
     */
    private void propagateCorrectedKey(
            long id, byte[] previousKey, byte[] correctedKey, long upperTimestamp, Entity current) {
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var later = new ArrayList<LaterVersion>();
        jdbcOperations.query(
                SELECT_LATER_HISTORICAL_KEYS,
                new MapSqlParameterSource("id", id).addValue("upperTimestamp", upperTimestamp),
                resultSet -> {
                    later.add(new LaterVersion(resultSet.getLong("lower_timestamp"), resultSet.getBytes("key"), false));
                });

        final var timestampRange = current.getTimestampRange();
        if (timestampRange != null
                && timestampRange.hasLowerBound()
                && timestampRange.lowerEndpoint() >= upperTimestamp) {
            later.add(new LaterVersion(timestampRange.lowerEndpoint(), current.getKey(), true));
        }
        later.sort(Comparator.comparingLong(LaterVersion::lowerTimestamp)
                .thenComparingInt(version -> version.entityRow() ? 0 : 1));

        Long lastMatchingHistoryLower = null;
        boolean updateEntity = false;
        for (final LaterVersion version : later) {
            if (!Arrays.equals(version.key(), previousKey)) {
                break;
            }
            if (version.entityRow()) {
                updateEntity = true;
            } else {
                lastMatchingHistoryLower = version.lowerTimestamp();
            }
        }

        if (lastMatchingHistoryLower != null) {
            jdbcOperations.update(
                    UPDATE_LATER_HISTORICAL_KEYS,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("key", correctedKey)
                            .addValue("lastLower", lastMatchingHistoryLower)
                            .addValue("previousKey", previousKey)
                            .addValue("publicKey", publicKey(correctedKey))
                            .addValue("upperTimestamp", upperTimestamp));
        }
        if (updateEntity) {
            current.setKey(correctedKey);
            entityRepositoryProvider.getObject().save(current);
            log.info("Saving corrected key for entity {} because it matched the historical key", id);
        }
    }

    private record SnapshotKey(byte[] previousKey, long upperTimestamp, boolean hasUpper) {}

    private record LaterVersion(long lowerTimestamp, byte[] key, boolean entityRow) {}

    private static boolean activeAtSnapshot(Range<Long> timestampRange) {
        return timestampRange != null && timestampRange.contains(KEY_SNAPSHOT_TIMESTAMP);
    }

    private static boolean mergeKey(AbstractEntity entity, byte[] correctedKey) {
        if (Arrays.equals(entity.getKey(), correctedKey)) {
            return false;
        }
        entity.setKey(correctedKey);
        return true;
    }

    private static String publicKey(byte[] key) {
        final String parsed = DomainUtils.getPublicKey(key);
        return parsed != null ? parsed : "";
    }
}
