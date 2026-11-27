// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.MAINNET;
import static org.hiero.mirror.importer.ImporterProperties.HederaNetwork.TESTNET;
import static org.hiero.mirror.importer.migration.AccountInfoKeyMigration.CN_DUMP_TIMESTAMP;

import java.time.Duration;
import java.util.HexFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
final class AccountInfoKeyMigrationTest extends AbstractAsyncJavaMigrationTest<AccountInfoKeyMigration> {

    private static final long AFTER_TIMESTAMP_ACCOUNT_ID = 1038L;
    private static final long BEFORE_TIMESTAMP_ACCOUNT_ID = 1022L;
    private static final String BEFORE_TIMESTAMP_KEY_HEX =
            "12200527923814cdd6409c4fa42346691437bff1e7fa0a0944638bf6e4c7b04eec85";
    private static final String BEFORE_TIMESTAMP_PUBLIC_KEY =
            "0527923814cdd6409c4fa42346691437bff1e7fa0a0944638bf6e4c7b04eec85";
    private static final long COMPLEX_ACCOUNT_ID = 927L;
    private static final String COMPLEX_KEY_HEX =
            "32500a4e2a4c080112480a22122061caaab6ed85e0e39b61cf3c8f57379466662f0cfd1ec102b84a7372fec4fd780a221220ee7d5c542fea5dd7622cbab2290f3b514bad888a70b31335cc25cca5af78fbe3";
    private static final long HISTORY_ACCOUNT_ID = 1017L;
    private static final long LAST_ACCOUNT_ID = 14678L;
    private static final String LAST_KEY_HEX = "1220e90b696a69ccbea189631c877e96e27c634eddb15bd37771890f71830fc82e05";
    private static final String LAST_PUBLIC_KEY = "e90b696a69ccbea189631c877e96e27c634eddb15bd37771890f71830fc82e05";
    private static final long ON_TIMESTAMP_ACCOUNT_ID = 1023L;
    private static final long SIMPLE_ACCOUNT_ID = 1011L;
    private static final String SIMPLE_KEY_HEX = "1220b5238a7964882fe1ac664ae32f41bdbe726a058773559e197cbd17abaf00e647";
    private static final String SIMPLE_PUBLIC_KEY = "b5238a7964882fe1ac664ae32f41bdbe726a058773559e197cbd17abaf00e647";
    private static final long UNLISTED_ACCOUNT_ID = 2_000_000L;

    @Getter
    private final AccountInfoKeyMigration migration;

    private final ImporterProperties importerProperties;

    private String network;

    @BeforeEach
    void setup() {
        network = importerProperties.getNetwork();
        importerProperties.setNetwork(MAINNET);
        resetChecksum();
    }

    @AfterEach
    void restoreNetwork() {
        importerProperties.setNetwork(network);
        migration.migrationProperties.getParams().remove("batchSize");
    }

    @Test
    void checksum() {
        assertThat(migration.getSuccessChecksum()).isEqualTo(1);
    }

    @Test
    void defersUntilRecordStreamReachesDump() {
        final long timestamp = DomainUtils.convertToNanosMax(CN_DUMP_TIMESTAMP);
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");

        runMigration();
        assertAccount(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        assertThat(latestChecksum()).isEqualTo(-1);

        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp - 2).consensusEnd(timestamp - 1))
                .persist();
        runMigration();

        assertAccount(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        assertThat(latestChecksum()).isEqualTo(-1);
    }

    @Test
    void doesNotUpdateAccountWithoutHistoryButOutsideOfFile() {
        ingestThroughDump();
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        insertEntity(UNLISTED_ACCOUNT_ID, new byte[] {3}, "keep");

        runMigration();
        waitForCompletion();

        assertAccount(SIMPLE_ACCOUNT_ID, HexFormat.of().parseHex(SIMPLE_KEY_HEX), SIMPLE_PUBLIC_KEY);
        assertAccount(UNLISTED_ACCOUNT_ID, new byte[] {3}, "keep");
    }

    @Test
    void doesNotUpdateAccountsWithHistory() {
        ingestThroughDump();
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        insertEntity(HISTORY_ACCOUNT_ID, new byte[] {4}, "keep");
        insertHistory(HISTORY_ACCOUNT_ID);

        runMigration();
        waitForCompletion();

        assertAccount(SIMPLE_ACCOUNT_ID, HexFormat.of().parseHex(SIMPLE_KEY_HEX), SIMPLE_PUBLIC_KEY);
        assertAccount(HISTORY_ACCOUNT_ID, new byte[] {4}, "keep");
    }

    @Test
    void keepsKeysUpdatedAfterDump() {
        final long timestamp = DomainUtils.convertToNanosMax(CN_DUMP_TIMESTAMP);
        ingestThroughDump();
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale", timestamp - 1);

        runMigration();
        waitForCompletion();
        assertAccount(SIMPLE_ACCOUNT_ID, HexFormat.of().parseHex(SIMPLE_KEY_HEX), SIMPLE_PUBLIC_KEY);

        final byte[] laterKey = new byte[] {9};
        jdbcOperations.update(
                "update entity set key = ?, public_key = ?, timestamp_range = int8range(?, null) where id = ?",
                laterKey,
                "later",
                timestamp,
                SIMPLE_ACCOUNT_ID);
        resetChecksum();
        runMigration();
        waitForCompletion();

        assertAccount(SIMPLE_ACCOUNT_ID, laterKey, "later");
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        migration.migrationProperties.getParams().put("batchSize", "0");
        migration.setComplete(false);
        ingestThroughDump();
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");

        runMigration();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(migration.isComplete()).isTrue());
        assertAccount(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        assertThat(latestChecksum()).isEqualTo(-1);
    }

    @Test
    void skipsEntitiesAtOrAfterTimestamp() {
        final long timestamp = DomainUtils.convertToNanosMax(CN_DUMP_TIMESTAMP);
        ingestThroughDump();
        insertEntity(BEFORE_TIMESTAMP_ACCOUNT_ID, new byte[] {1}, "stale", timestamp - 1);
        insertEntity(ON_TIMESTAMP_ACCOUNT_ID, new byte[] {2}, "stale", timestamp);
        insertEntity(AFTER_TIMESTAMP_ACCOUNT_ID, new byte[] {3}, "stale", timestamp + 1);

        runMigration();
        waitForCompletion();

        assertAccount(
                BEFORE_TIMESTAMP_ACCOUNT_ID,
                HexFormat.of().parseHex(BEFORE_TIMESTAMP_KEY_HEX),
                BEFORE_TIMESTAMP_PUBLIC_KEY);
        assertAccount(ON_TIMESTAMP_ACCOUNT_ID, new byte[] {2}, "stale");
        assertAccount(AFTER_TIMESTAMP_ACCOUNT_ID, new byte[] {3}, "stale");
    }

    @Test
    void skipsNonMainnet() {
        importerProperties.setNetwork(TESTNET);
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        ingestThroughDump();

        runMigration();
        waitForCompletion();

        assertAccount(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
    }

    @Test
    void updatesAccountsWithoutHistory() {
        ingestThroughDump();
        insertEntity(SIMPLE_ACCOUNT_ID, new byte[] {1}, "stale");
        insertEntity(COMPLEX_ACCOUNT_ID, new byte[] {2}, "stale");
        insertEntity(UNLISTED_ACCOUNT_ID, new byte[] {3}, "keep");
        insertEntity(HISTORY_ACCOUNT_ID, new byte[] {4}, "keep");
        insertHistory(HISTORY_ACCOUNT_ID);

        runMigration();
        waitForCompletion();
        resetChecksum();
        runMigration();
        waitForCompletion();

        assertAccount(SIMPLE_ACCOUNT_ID, HexFormat.of().parseHex(SIMPLE_KEY_HEX), SIMPLE_PUBLIC_KEY);
        assertAccount(COMPLEX_ACCOUNT_ID, HexFormat.of().parseHex(COMPLEX_KEY_HEX), null);
        assertAccount(UNLISTED_ACCOUNT_ID, new byte[] {3}, "keep");
        assertAccount(HISTORY_ACCOUNT_ID, new byte[] {4}, "keep");
        assertThat(countEntities()).isEqualTo(4);
    }

    @Test
    void updatesInSmallBatches() {
        migration.migrationProperties.getParams().put("batchSize", "1");
        ingestThroughDump();
        insertEntity(COMPLEX_ACCOUNT_ID, new byte[] {1}, "stale");
        insertEntity(LAST_ACCOUNT_ID, new byte[] {2}, "stale");

        runMigration();
        waitForCompletion();

        assertAccount(COMPLEX_ACCOUNT_ID, HexFormat.of().parseHex(COMPLEX_KEY_HEX), null);
        assertAccount(LAST_ACCOUNT_ID, HexFormat.of().parseHex(LAST_KEY_HEX), LAST_PUBLIC_KEY);
    }

    private void assertAccount(long id, byte[] key, String publicKey) {
        var account = findAccount(id);
        assertThat(account.key()).isEqualTo(key);
        assertThat(account.publicKey()).isEqualTo(publicKey);
    }

    private int countEntities() {
        return jdbcOperations.queryForObject("select count(*) from entity", Integer.class);
    }

    private AccountRow findAccount(long id) {
        return jdbcOperations.queryForObject(
                "select key, public_key from entity where id = ?",
                (rs, rowNum) -> new AccountRow(rs.getBytes("key"), rs.getString("public_key")),
                id);
    }

    private void ingestThroughDump() {
        final long timestamp = DomainUtils.convertToNanosMax(CN_DUMP_TIMESTAMP);
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(timestamp - 1).consensusEnd(timestamp))
                .persist();
    }

    private void insertEntity(long id, byte[] key, String publicKey) {
        insertEntity(id, key, publicKey, 1L);
    }

    private void insertEntity(long id, byte[] key, String publicKey, long lowerTimestamp) {
        jdbcOperations.update("""
                insert into entity (created_timestamp, id, key, num, public_key, realm, shard, timestamp_range, type)
                values (1, ?, ?, ?, ?, 0, 0, int8range(?, null), 'ACCOUNT')
                """, id, key, id, publicKey, lowerTimestamp);
    }

    private void insertHistory(long id) {
        jdbcOperations.update("""
                insert into entity_history (created_timestamp, id, num, realm, shard, timestamp_range, type)
                values (1, ?, ?, 0, 0, '[0,1)', 'ACCOUNT')
                """, id, id);
    }

    private Integer latestChecksum() {
        return jdbcOperations.queryForObject("""
                select checksum from flyway_schema_history
                where description = ?
                order by installed_rank desc
                limit 1
                """, Integer.class, migration.getDescription());
    }

    private record AccountRow(byte[] key, String publicKey) {}
}
