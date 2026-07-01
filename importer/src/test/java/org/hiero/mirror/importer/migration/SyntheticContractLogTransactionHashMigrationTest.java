// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.SyntheticContractLogTransactionHashMigration.DEFAULT_BATCH_INTERVAL;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class SyntheticContractLogTransactionHashMigrationTest
        extends AbstractAsyncJavaMigrationTest<SyntheticContractLogTransactionHashMigration> {

    @Getter
    private final SyntheticContractLogTransactionHashMigration migration;

    @BeforeEach
    void setup() {
        migration.getEntityProperties().getPersist().setTransactionHash(true);
    }

    @Test
    void emptyDatabase() {
        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void backfillsSyntheticLogsWithTransactionHash() {
        final var syntheticHash1 = new byte[32];
        Arrays.fill(syntheticHash1, (byte) 0x11);
        final var syntheticHash2 = new byte[32];
        Arrays.fill(syntheticHash2, (byte) 0x22);
        final var payer = EntityId.of(9000001L);

        // two logs from the same transaction (same hash + timestamp) → one transaction_hash entry
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L)
                        .transactionHash(syntheticHash1)
                        .payerAccountId(payer)
                        .synthetic(true)
                        .index(0))
                .persist();
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L)
                        .transactionHash(syntheticHash1)
                        .payerAccountId(payer)
                        .synthetic(true)
                        .index(1))
                .persist();
        // second distinct transaction
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(2000L)
                        .transactionHash(syntheticHash2)
                        .payerAccountId(payer)
                        .synthetic(true)
                        .index(0))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isEqualTo(2);
        assertThat(findHashByTimestamp(1000L)).isEqualTo(syntheticHash1);
        assertThat(findHashByTimestamp(2000L)).isEqualTo(syntheticHash2);
    }

    @Test
    void skipsNonSyntheticLogs() {
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L)
                        .transactionHash(new byte[32])
                        .synthetic(false))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void skipsLogsWithNullTransactionHash() {
        domainBuilder
                .contractLog()
                .customize(
                        cl -> cl.consensusTimestamp(1000L).transactionHash(null).synthetic(true))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void skipsHashesAlreadyPresentInTransactionHashTable() {
        // Simulates TOKENREJECT: already in transaction_hash via the regular onTransaction() path
        final var syntheticHash = new byte[32];
        Arrays.fill(syntheticHash, (byte) 0x33);
        final var payer = EntityId.of(9000003L);

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(5000L)
                        .transactionHash(syntheticHash)
                        .payerAccountId(payer)
                        .synthetic(true)
                        .index(0))
                .persist();

        domainBuilder
                .transactionHash()
                .customize(th -> th.hash(syntheticHash).consensusTimestamp(5000L))
                .persist();

        runMigration();
        waitForCompletion();

        // pre-existing entry must not be duplicated
        assertThat(countTransactionHashes()).isEqualTo(1);
    }

    @Test
    void isIdempotent() {
        final var syntheticHash = new byte[32];
        Arrays.fill(syntheticHash, (byte) 0xab);
        final var payer = EntityId.of(9000002L);

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(3000L)
                        .transactionHash(syntheticHash)
                        .payerAccountId(payer)
                        .synthetic(true)
                        .index(0))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isEqualTo(1);

        // reset flyway checksum so migration runs again
        jdbcOperations.update(
                "update flyway_schema_history set checksum = -1 where description = ?", migration.getDescription());

        runMigration();
        waitForCompletion();

        // must still be exactly 1, not duplicated
        assertThat(countTransactionHashes()).isEqualTo(1);
    }

    @Test
    void skipsWhenTransactionHashPersistenceDisabled() {
        final var syntheticHash = new byte[32];
        Arrays.fill(syntheticHash, (byte) 0xcc);

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L)
                        .transactionHash(syntheticHash)
                        .synthetic(true)
                        .index(0))
                .persist();

        migration.getEntityProperties().getPersist().setTransactionHash(false);
        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void batchesCorrectlyAcrossTimeRange() {
        final long batchIntervalNs = DurationStyle.SIMPLE
                .parse(DEFAULT_BATCH_INTERVAL, java.time.temporal.ChronoUnit.HOURS)
                .toNanos();

        final var hash1 = new byte[32];
        Arrays.fill(hash1, (byte) 0xd1);
        final var hash2 = new byte[32];
        Arrays.fill(hash2, (byte) 0xd2);

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L)
                        .transactionHash(hash1)
                        .synthetic(true)
                        .index(0))
                .persist();
        // second log more than one batch interval away to force multiple iterations
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L + batchIntervalNs + 1L)
                        .transactionHash(hash2)
                        .synthetic(true)
                        .index(0))
                .persist();

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isEqualTo(2);
    }

    private int countTransactionHashes() {
        return ownerJdbcTemplate.queryForObject("select count(*)::int from transaction_hash", Integer.class);
    }

    private byte[] findHashByTimestamp(long consensusTimestamp) {
        final List<Map<String, Object>> rows = ownerJdbcTemplate.queryForList(
                "select hash from transaction_hash where consensus_timestamp = ?", consensusTimestamp);
        return rows.isEmpty() ? null : (byte[]) rows.get(0).get("hash");
    }
}
