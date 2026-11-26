// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.MAINNET;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Backfills {@code entity.key} and {@code entity.public_key} from {@code accountKey.csv}. Each row is an account id and
 * the hex-encoded protobuf key. Only accounts listed in the file that have no {@code entity_history} row for that id
 * and whose {@code lower(timestamp_range)} is before {@link #CN_DUMP_TIMESTAMP} are updated.
 */
@Named
final class AccountInfoKeyMigration extends RepeatableMigration {

    // The timestamp from the consensus node dump that was used to compare the keys.
    static final Instant CN_DUMP_TIMESTAMP = Instant.parse("2026-10-19T20:00:00.767739Z");

    private static final String BATCH_SIZE = "batchSize";
    private static final String DEFAULT_BATCH_SIZE = "100";

    private static final String UPDATE_SQL = """
            update entity
            set key = :key,
                public_key = :publicKey
            where id = :id
              and lower(timestamp_range) < :timestamp
              and not exists (select 1 from entity_history where id = :id)
            """;

    private final Resource accountKey;
    private final ImporterProperties importerProperties;
    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;

    AccountInfoKeyMigration(
            @Value("classpath:accountKey.csv") final Resource accountKey,
            final ImporterProperties importerProperties,
            final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider) {
        super(importerProperties.getMigration());
        this.accountKey = accountKey;
        this.importerProperties = importerProperties;
        this.jdbcOperationsProvider = jdbcOperationsProvider;
    }

    @Override
    public String getDescription() {
        return "Update entity account keys from accountKey.csv";
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
            log.info("Skipping migration since it only applies to mainnet");
            return;
        }

        final int batchSize = batchSize();
        final var stopwatch = Stopwatch.createStarted();
        final var count = migrateAccountKeys(batchSize);
        log.info("Updated {} of {} account keys in {}", count.updated(), count.loaded(), stopwatch);
    }

    private int batchSize() {
        final int batchSize =
                Integer.parseInt(migrationProperties.getParams().getOrDefault(BATCH_SIZE, DEFAULT_BATCH_SIZE));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Invalid non-positive batch size " + batchSize);
        }
        return batchSize;
    }

    private UpdateCount migrateAccountKeys(final int batchSize) throws IOException {
        final long timestamp = DomainUtils.convertToNanosMax(CN_DUMP_TIMESTAMP);
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        int loaded = 0;
        int updated = 0;
        try (var reader =
                new BufferedReader(new InputStreamReader(accountKey.getInputStream(), StandardCharsets.UTF_8))) {
            final var page = new ArrayList<AccountKey>(batchSize);
            String line;
            while ((line = reader.readLine()) != null) {
                final var trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("account_id")) {
                    continue;
                }
                page.add(parseLine(trimmed));
                if (page.size() == batchSize) {
                    loaded += page.size();
                    updated += update(jdbcOperations, page, timestamp);
                    page.clear();
                }
            }
            if (!page.isEmpty()) {
                loaded += page.size();
                updated += update(jdbcOperations, page, timestamp);
            }
        }
        return new UpdateCount(loaded, updated);
    }

    private static AccountKey parseLine(final String line) {
        final int separator = line.indexOf(';');
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid account key line: " + line);
        }

        final var accountId = line.substring(0, separator).trim();
        final var keyHex = line.substring(separator + 1).trim();
        if (accountId.isEmpty() || keyHex.isEmpty()) {
            throw new IllegalArgumentException("Invalid account key line: " + line);
        }

        final long id = EntityId.of(accountId).getId();
        final byte[] key = HexFormat.of().parseHex(keyHex);
        return new AccountKey(id, key, DomainUtils.getPublicKey(key));
    }

    private static int update(
            final NamedParameterJdbcOperations jdbcOperations,
            final List<AccountKey> accountKeys,
            final long timestamp) {
        final var parameters = new SqlParameterSource[accountKeys.size()];
        for (int i = 0; i < accountKeys.size(); i++) {
            final var accountKey = accountKeys.get(i);
            parameters[i] = new MapSqlParameterSource()
                    .addValue("id", accountKey.id())
                    .addValue("key", accountKey.key(), Types.BINARY)
                    .addValue("publicKey", accountKey.publicKey(), Types.VARCHAR)
                    .addValue("timestamp", timestamp);
        }

        final int[] counts = jdbcOperations.batchUpdate(UPDATE_SQL, parameters);
        int updated = 0;
        for (final int count : counts) {
            if (count > 0) {
                updated += count;
            }
        }
        return updated;
    }

    private record AccountKey(long id, byte[] key, String publicKey) {}

    private record UpdateCount(int loaded, int updated) {}
}
