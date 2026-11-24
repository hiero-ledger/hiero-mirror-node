// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.reader.block.BlockStreamTestUtils.BLOCK_STREAM_HASH_SIZE;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Key;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
final class AccountInfoKeyMigrationTest extends ImporterIntegrationTest {

    private final AccountInfoKeyMigration accountInfoKeyMigration;
    private final BlockProperties blockProperties;
    private final EntityRepository entityRepository;
    private final ImporterProperties importerProperties;

    private String network;

    @BeforeEach
    void before() {
        network = importerProperties.getNetwork();
        importerProperties.setImportHistoricalAccountInfo(true);
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void after() {
        blockProperties.setEnabled(false);
        importerProperties.setImportHistoricalAccountInfo(false);
        importerProperties.setNetwork(network);
        importerProperties.setStartBlockNumber(null);
        importerProperties.setStartDate(Instant.parse("1970-01-01T00:00:00Z"));
    }

    @Test
    void checksum() {
        assertThat(accountInfoKeyMigration.getChecksum()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, true
            1, false
            """)
    void skipWhenFirstBlockIsWrb(final long blockNumber, final boolean shouldSkip) {
        domainBuilder
                .recordFile()
                .customize(
                        r -> r.index(blockNumber).wrappedRecordBlockHash(domainBuilder.bytes(BLOCK_STREAM_HASH_SIZE)))
                .persist();

        assertThat(accountInfoKeyMigration.skipMigration(mock(Configuration.class)))
                .isEqualTo(shouldSkip);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, , true
            true, 0, true
            true, 2, false
            false, , false
            """)
    void skipWithBlockStreamConfig(final boolean blockEnabled, final Long startBlockNumber, final boolean shouldSkip) {
        blockProperties.setEnabled(blockEnabled);
        importerProperties.setStartBlockNumber(startBlockNumber);
        assertThat(accountInfoKeyMigration.skipMigration(mock(Configuration.class)))
                .isEqualTo(shouldSkip);
    }

    @Test
    void disabled() throws Exception {
        importerProperties.setImportHistoricalAccountInfo(false);
        accountInfoKeyMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void notMainnet() throws Exception {
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.DEMO);
        accountInfoKeyMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void startDateAfterExport() throws Exception {
        importerProperties.setStartDate(Instant.now());
        accountInfoKeyMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void updatesKeyActiveBeforeSnapshot() {
        final Entity entity = createEntity(2L);
        final AccountInfo accountInfo = correctedKey(2L, "new");

        assertThat(accountInfoKeyMigration.updateHistoricalKey(accountInfo)).isTrue();

        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getKey())
                .containsExactly(accountInfo.getKey().toByteArray());
        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getMemo())
                .isEqualTo("Bar");
    }

    @Test
    void keepsKeyChangedAfterSnapshot() {
        final long id = 2L;
        final long afterSnapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final Entity entity = createEntity(id);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        final byte[] currentKey = entity.getKey().clone();
        final byte[] historicalKey = ed25519("old");
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history -> history.key(historicalKey).timestampRange(Range.closedOpen(0L, afterSnapshot)))
                .persist();

        assertThat(accountInfoKeyMigration.updateHistoricalKey(correctedKey(id, "new")))
                .isTrue();

        assertThat(entityKey(id)).containsExactly(currentKey);
        assertThat(historicalKey(id))
                .containsExactly(correctedKey(id, "new").getKey().toByteArray());
    }

    @Test
    void updatesCurrentKeyWhenItStillMatchesHistoricalKey() {
        final long id = 2L;
        final long afterSnapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final byte[] historicalKey = ed25519("old");
        final Entity entity = createEntity(id);
        entity.setKey(historicalKey);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history ->
                        history.key(historicalKey.clone()).timestampRange(Range.closedOpen(0L, afterSnapshot)))
                .persist();

        final AccountInfo corrected = correctedKey(id, "new");
        assertThat(accountInfoKeyMigration.updateHistoricalKey(corrected)).isTrue();

        assertThat(entityKey(id)).containsExactly(corrected.getKey().toByteArray());
        assertThat(entityRepository.findById(id).orElseThrow().getMemo()).isEqualTo("Bar");
        assertThat(historicalKey(id)).containsExactly(corrected.getKey().toByteArray());
    }

    @Test
    void updatesLaterRowsWithTheSameKey() {
        final long id = 2L;
        final long snapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long affectedEnd = snapshot + 1;
        final long laterEnd = snapshot + 2;
        final byte[] oldKey = ed25519("old");
        final Entity entity = createEntity(id);
        entity.setKey(oldKey.clone());
        entity.setTimestampRange(Range.atLeast(laterEnd));
        entityRepository.save(entity);
        persistHistory(id, oldKey, 0L, snapshot);
        persistHistory(id, oldKey, snapshot, affectedEnd);
        persistHistory(id, oldKey, affectedEnd, laterEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(accountInfoKeyMigration.updateHistoricalKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).containsExactly(oldKey);
        assertThat(keys.get(1)).containsExactly(corrected);
        assertThat(keys.get(2)).containsExactly(corrected);
        assertThat(entityKey(id)).containsExactly(corrected);
    }

    @Test
    void updatesLaterRowsThatStillHaveThePreviousKey() {
        final long id = 2L;
        final long snapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long affectedEnd = snapshot + 10;
        final long sameEnd = snapshot + 20;
        final long changedEnd = snapshot + 30;
        final byte[] oldKey = ed25519("old");
        final byte[] changedKey = ed25519("changed");
        final Entity entity = createEntity(id);
        entity.setKey(oldKey.clone());
        entity.setTimestampRange(Range.atLeast(changedEnd));
        entityRepository.save(entity);
        persistHistory(id, oldKey, snapshot, affectedEnd);
        persistHistory(id, oldKey, affectedEnd, sameEnd);
        persistHistory(id, changedKey, sameEnd, changedEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(accountInfoKeyMigration.updateHistoricalKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).containsExactly(corrected);
        assertThat(keys.get(1)).containsExactly(corrected);
        assertThat(keys.get(2)).containsExactly(changedKey);
        assertThat(entityKey(id)).containsExactly(corrected);
    }

    @Test
    void keepsEntityKeyWhenItDiffersFromThePreviousKey() {
        final long id = 2L;
        final long snapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long entityLower = snapshot + 10;
        final long laterEnd = snapshot + 20;
        final byte[] oldKey = ed25519("old");
        final byte[] changedKey = ed25519("changed");
        final Entity entity = createEntity(id);
        entity.setKey(changedKey);
        entity.setTimestampRange(Range.atLeast(entityLower));
        entityRepository.save(entity);
        persistHistory(id, oldKey, snapshot, entityLower);
        persistHistory(id, oldKey, entityLower, laterEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(accountInfoKeyMigration.updateHistoricalKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).containsExactly(corrected);
        assertThat(keys.get(1)).containsExactly(corrected);
        assertThat(entityKey(id)).containsExactly(changedKey);
    }

    @Test
    void replacesUnparsedHistoricalKey() {
        final long id = 2L;
        final long afterSnapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final byte[] unparsed = new byte[] {0x0d};
        final Entity entity = createEntity(id);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        persistHistory(id, unparsed, 0L, afterSnapshot);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(accountInfoKeyMigration.updateHistoricalKey(correctedKey(id, "new")))
                .isTrue();
        assertThat(historicalKey(id)).containsExactly(corrected);
        assertThat(entityRepository.findById(id).orElseThrow().getKey()).containsExactly(entity.getKey());
    }

    @Test
    void keepsPublicKeyWhenCorrectedKeyHasNoSinglePublicKey() {
        final long id = 2L;
        final long afterSnapshot = AccountInfoKeyMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final byte[] historicalKey = ed25519("old");
        final Entity entity = createEntity(id);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        persistHistory(id, historicalKey, 0L, afterSnapshot);
        final String previousPublicKey =
                jdbcOperations.queryForObject("select public_key from entity_history where id = ?", String.class, id);
        assertThat(previousPublicKey).isNotBlank();
        final Key keyList = Key.newBuilder()
                .setKeyList(com.hederahashgraph.api.proto.java.KeyList.newBuilder()
                        .addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("a")))
                        .addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("b"))))
                .build();

        assertThat(accountInfoKeyMigration.updateHistoricalKey(AccountInfo.newBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(id).build())
                        .setKey(keyList)
                        .build()))
                .isTrue();

        assertThat(historicalKey(id)).containsExactly(keyList.toByteArray());
        assertThat(jdbcOperations.queryForObject(
                        "select public_key from entity_history where id = ?", String.class, id))
                .isEqualTo(previousPublicKey);
    }

    @Test
    void skipsKeyThatAlreadyMatches() {
        final AccountInfo accountInfo = correctedKey(2L, "123");
        final Entity entity = createEntity(2L);

        assertThat(accountInfoKeyMigration.updateHistoricalKey(accountInfo)).isFalse();
        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getKey())
                .containsExactly(entity.getKey());
    }

    private static AccountInfo correctedKey(final long accountNum, final String key) {
        return AccountInfo.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(accountNum).build())
                .setKey(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8(key))
                        .build())
                .build();
    }

    private static byte[] ed25519(final String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build().toByteArray();
    }

    private void persistHistory(final long id, final byte[] key, final long lower, final long upper) {
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history -> history.key(key.clone()).timestampRange(Range.closedOpen(lower, upper)))
                .persist();
    }

    private byte[] entityKey(final long id) {
        return jdbcOperations.queryForObject(
                "select key from entity where id = ?", (result, row) -> result.getBytes(1), id);
    }

    private byte[] historicalKey(final long id) {
        return jdbcOperations.queryForObject(
                "select key from entity_history where id = ?", (result, row) -> result.getBytes(1), id);
    }

    private List<byte[]> historicalKeys(final long id) {
        return jdbcOperations.query(
                "select key from entity_history where id = ? order by lower(timestamp_range)",
                (result, row) -> result.getBytes(1),
                id);
    }

    private Entity createEntity(final long num) {
        final Entity entity = EntityId.of(0L, 0L, num).toEntity();
        entity.setAutoRenewPeriod(5L);
        entity.setDeclineReward(false);
        entity.setDeleted(false);
        entity.setExpirationTimestamp(1L);
        entity.setId(num);
        entity.setKey(ed25519("123"));
        entity.setMemo("Bar");
        entity.setNum(num);
        entity.setProxyAccountId(EntityId.of(0, 0, 3));
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setTimestampRange(Range.atLeast(0L));
        entity.setType(EntityType.ACCOUNT);
        entityRepository.save(entity);
        return entity;
    }
}
