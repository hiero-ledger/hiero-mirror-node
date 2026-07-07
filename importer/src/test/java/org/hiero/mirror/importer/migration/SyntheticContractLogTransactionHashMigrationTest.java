// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONSENSUSSUBMITMESSAGE;
import static org.hiero.mirror.importer.migration.SyntheticContractLogTransactionHashMigration.DEFAULT_BATCH_INTERVAL;

import com.google.common.collect.Range;
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
        // establishes a non-null lower bound floor well below all test timestamps, via a topic with a custom fee
        var topic = domainBuilder.entity().customize(e -> e.type(TOPIC)).persist();
        domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(topic.getId()).timestampRange(Range.atLeast(1L)))
                .persist();
    }

    @Test
    void emptyDatabase() {
        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
        assertThat(progressTableExists()).isFalse();
    }

    @Test
    void backfillsSyntheticLogsWithTransactionHash() {
        final var fullHash1 = domainBuilder.bytes(48);
        final var fullHash2 = domainBuilder.bytes(48);
        final var payer = EntityId.of(9000001L);

        // two logs from the same transaction (same timestamp) → one transaction_hash entry
        persistSyntheticLog(1000L, 0);
        persistSyntheticLog(1000L, 1);
        persistConsensusSubmitMessage(1000L, fullHash1, payer);
        // second distinct transaction
        persistSyntheticLog(2000L, 0);
        persistConsensusSubmitMessage(2000L, fullHash2, payer);

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isEqualTo(2);
        assertThat(findHashByTimestamp(1000L)).isEqualTo(Arrays.copyOfRange(fullHash1, 0, 32));
        assertThat(findHashSuffixByTimestamp(1000L)).isEqualTo(Arrays.copyOfRange(fullHash1, 32, 48));
        assertThat(findHashByTimestamp(2000L)).isEqualTo(Arrays.copyOfRange(fullHash2, 0, 32));
        assertThat(findHashSuffixByTimestamp(2000L)).isEqualTo(Arrays.copyOfRange(fullHash2, 32, 48));
    }

    @Test
    void skipsNonSyntheticLogs() {
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(1000L).synthetic(false))
                .persist();
        persistConsensusSubmitMessage(1000L, domainBuilder.bytes(48), EntityId.of(9000001L));

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void skipsWhenNoMatchingConsensusSubmitMessageTransaction() {
        persistSyntheticLog(1000L, 0);

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isZero();
    }

    @Test
    void skipsHashesAlreadyPresentInTransactionHashTable() {
        // Simulates TOKENREJECT: already in transaction_hash via the regular onTransaction() path
        final var fullHash = domainBuilder.bytes(48);
        final var payer = EntityId.of(9000003L);

        persistSyntheticLog(5000L, 0);
        persistConsensusSubmitMessage(5000L, fullHash, payer);

        domainBuilder
                .transactionHash()
                .customize(th -> th.hash(Arrays.copyOfRange(fullHash, 0, 32)).consensusTimestamp(5000L))
                .persist();

        runMigration();
        waitForCompletion();

        // pre-existing entry must not be duplicated
        assertThat(countTransactionHashes()).isEqualTo(1);
    }

    @Test
    void isIdempotent() {
        final var fullHash = domainBuilder.bytes(48);
        final var payer = EntityId.of(9000002L);

        persistSyntheticLog(3000L, 0);
        persistConsensusSubmitMessage(3000L, fullHash, payer);

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
        persistSyntheticLog(1000L, 0);
        persistConsensusSubmitMessage(1000L, domainBuilder.bytes(48), EntityId.of(9000001L));

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
        final var payer = EntityId.of(9000001L);

        persistSyntheticLog(1000L, 0);
        persistConsensusSubmitMessage(1000L, domainBuilder.bytes(48), payer);
        // second log more than one batch interval away to force multiple iterations
        final long laterTimestamp = 1000L + batchIntervalNs + 1L;
        persistSyntheticLog(laterTimestamp, 0);
        persistConsensusSubmitMessage(laterTimestamp, domainBuilder.bytes(48), payer);

        runMigration();
        waitForCompletion();

        assertThat(countTransactionHashes()).isEqualTo(2);
    }

    private void persistSyntheticLog(long consensusTimestamp, int index) {
        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .synthetic(true)
                        .index(index))
                .persist();
    }

    private void persistConsensusSubmitMessage(long consensusTimestamp, byte[] fullHash, EntityId payer) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)
                        .type(CONSENSUSSUBMITMESSAGE.getProtoId())
                        .transactionHash(fullHash)
                        .payerAccountId(payer)
                        .parentConsensusTimestamp(null))
                .persist();
    }

    private int countTransactionHashes() {
        return ownerJdbcTemplate.queryForObject("select count(*)::int from transaction_hash", Integer.class);
    }

    private boolean progressTableExists() {
        return ownerJdbcTemplate.queryForObject(
                "select exists(select 1 from information_schema.tables "
                        + "where table_name = 'synthetic_contract_log_transaction_hash_progress_temp')",
                Boolean.class);
    }

    private byte[] findHashByTimestamp(long consensusTimestamp) {
        final List<Map<String, Object>> rows = ownerJdbcTemplate.queryForList(
                "select hash from transaction_hash where consensus_timestamp = ?", consensusTimestamp);
        return rows.isEmpty() ? null : (byte[]) rows.get(0).get("hash");
    }

    private byte[] findHashSuffixByTimestamp(long consensusTimestamp) {
        final List<Map<String, Object>> rows = ownerJdbcTemplate.queryForList(
                "select hash_suffix from transaction_hash where consensus_timestamp = ?", consensusTimestamp);
        return rows.isEmpty() ? null : (byte[]) rows.get(0).get("hash_suffix");
    }
}
