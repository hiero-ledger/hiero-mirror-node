// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.hiero.mirror.importer.reader.block.BlockStreamTestUtils.BLOCK_STREAM_HASH_SIZE;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.configuration.Configuration;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
final class HistoricalAccountInfoMigrationTest extends ImporterIntegrationTest {

    // These are the three accounts present in the test accountInfo.txt.gz
    private static final long ACCOUNT_ID1 = 2977L;
    private static final long ACCOUNT_ID2 = 2978L;
    private static final long ACCOUNT_ID3 = 2979L;
    private static final long CONTRACT_ID1 = 13236L;
    private static final int CONTRACT_COUNT = 1;
    private static final int ENTITY_COUNT = 3;

    private final BlockProperties blockProperties;
    private final HistoricalAccountInfoMigration historicalAccountInfoMigration;
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
    }

    @Test
    void checksum() {
        assertThat(historicalAccountInfoMigration.getChecksum()).isEqualTo(4);
    }

    @Test
    void noExistingEntities() throws Exception {
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void existingEntitiesFromBeforeReset() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, false);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, false);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, false);
        Entity contract1 = createEntity(CONTRACT_ID1, EntityType.CONTRACT, false);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll())
                .hasSize(ENTITY_COUNT + CONTRACT_COUNT)
                .allMatch(e -> e.getAutoRenewPeriod() > 0)
                .allMatch(e -> e.getExpirationTimestamp() > 0)
                .allMatch(e -> e.getKey().length > 0)
                .map(Entity::getNum)
                .containsExactlyInAnyOrder(entity1.getNum(), entity2.getNum(), entity3.getNum(), contract1.getNum());
    }

    @Test
    void existingEntitiesAfterReset() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, true);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, true);
        Entity contract1 = createEntity(CONTRACT_ID1, EntityType.CONTRACT, true);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3, contract1);
    }

    @Test
    void existingEntitiesWrongType() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, true);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, true);
        Entity entity4 = createEntity(CONTRACT_ID1, EntityType.ACCOUNT, true);

        historicalAccountInfoMigration.doMigrate();

        entity4.setType(EntityType.CONTRACT);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3, entity4);
    }

    @Test
    void noChangesWhenRanAgain() throws Exception {
        historicalAccountInfoMigration.doMigrate();

        Iterable<Entity> entities = entityRepository.findAll();
        assertThat(entities).hasSize(ENTITY_COUNT + CONTRACT_COUNT);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, true
            1, false
            """)
    void skipWhenFirstBlockIsWrb(final long blockNumber, final boolean shouldSkip) {
        // given
        domainBuilder
                .recordFile()
                .customize(
                        r -> r.index(blockNumber).wrappedRecordBlockHash(domainBuilder.bytes(BLOCK_STREAM_HASH_SIZE)))
                .persist();

        // when / then
        assertThat(historicalAccountInfoMigration.skipMigration(mock(Configuration.class)))
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
        assertThat(historicalAccountInfoMigration.skipMigration(mock(Configuration.class)))
                .isEqualTo(shouldSkip);
    }

    @Test
    void disabled() throws Exception {
        importerProperties.setImportHistoricalAccountInfo(false);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void notMainnet() throws Exception {
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.DEMO);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void startDateAfter() throws Exception {
        importerProperties.setStartDate(Instant.now());
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void startDateBefore() throws Exception {
        importerProperties.setStartDate(HistoricalAccountInfoMigration.EXPORT_DATE.minusNanos(1));
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void startDateEquals() throws Exception {
        importerProperties.setStartDate(HistoricalAccountInfoMigration.EXPORT_DATE);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void startDateNull() throws Exception {
        importerProperties.setStartDate(null);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @SuppressWarnings("deprecation")
    @Test
    void create() {
        AccountInfo.Builder accountInfo = accountInfo();
        String publicKey = DomainUtils.getPublicKey(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entity::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entity::getDeleted))
                .returns(publicKey, from(Entity::getPublicKey))
                .returns(
                        DomainUtils.timeStampInNanos(accountInfo.getExpirationTime()),
                        from(Entity::getExpirationTimestamp))
                .returns(accountInfo.getKey().toByteArray(), from(Entity::getKey))
                .returns(accountInfo.getMemo(), from(Entity::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entity::getProxyAccountId));
    }

    @SuppressWarnings("deprecation")
    @Test
    void update() {
        createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, false);
        AccountInfo.Builder accountInfo = accountInfo();
        String publicKey = DomainUtils.getPublicKey(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entity::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entity::getDeleted))
                .returns(publicKey, from(Entity::getPublicKey))
                .returns(
                        DomainUtils.timeStampInNanos(accountInfo.getExpirationTime()),
                        from(Entity::getExpirationTimestamp))
                .returns(accountInfo.getKey().toByteArray(), from(Entity::getKey))
                .returns(accountInfo.getMemo(), from(Entity::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entity::getProxyAccountId));
    }

    @Test
    void emptyValues() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(ACCOUNT_ID1).build();
        AccountInfo.Builder accountInfo = accountInfo().clear().setAccountID(accountId);
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(null, from(Entity::getAutoRenewPeriod))
                .returns(false, from(Entity::getDeleted))
                .returns(null, from(Entity::getPublicKey))
                .returns(null, from(Entity::getExpirationTimestamp))
                .returns(null, from(Entity::getKey))
                .returns("", from(Entity::getMemo))
                .returns(null, from(Entity::getProxyAccountId));
    }

    @Test
    void deleted() {
        AccountInfo.Builder accountInfo = accountInfo().setDeleted(true);

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .extracting(Entity::getDeleted)
                .isEqualTo(true);
    }

    @Test
    void longOverflow() {
        AccountInfo.Builder accountInfo = accountInfo()
                .setExpirationTime(
                        Timestamp.newBuilder().setSeconds(31556889864403199L).build());
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .extracting(Entity::getExpirationTimestamp)
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void skipExisting() {
        AccountInfo.Builder accountInfo = accountInfo();
        Entity entity = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isFalse();
        assertThat(entityRepository.findAll()).hasSize(1).containsExactly(entity);
    }

    @Test
    void skipMigrationFalse() {
        assertThat(historicalAccountInfoMigration.skipMigration(mock(Configuration.class)))
                .isFalse();
    }

    @Test
    void updatesKeyActiveBeforeSnapshot() {
        final Entity entity = createEntity(2L, EntityType.ACCOUNT, true);
        final AccountInfo accountInfo = correctedKey(2L, "new");

        assertThat(historicalAccountInfoMigration.updateKey(accountInfo)).isTrue();

        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getKey())
                .containsExactly(accountInfo.getKey().toByteArray());
        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getMemo())
                .isEqualTo("Bar");
    }

    @Test
    void keepsKeyChangedAfterSnapshot() {
        final long id = 2L;
        final long afterSnapshot = HistoricalAccountInfoMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final Entity entity = createEntity(id, EntityType.ACCOUNT, true);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        final byte[] currentKey = entity.getKey().clone();
        final byte[] historicalKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("old"))
                .build()
                .toByteArray();
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history -> history.key(historicalKey).timestampRange(Range.closedOpen(0L, afterSnapshot)))
                .persist();

        assertThat(historicalAccountInfoMigration.updateKey(correctedKey(id, "new")))
                .isTrue();

        assertThat(entityRepository.findById(id).orElseThrow().getKey()).containsExactly(currentKey);
        assertThat(historicalKey(id))
                .containsExactly(correctedKey(id, "new").getKey().toByteArray());
    }

    @Test
    void updatesCurrentKeyWhenItStillMatchesHistoricalKey() {
        final long id = 2L;
        final long afterSnapshot = HistoricalAccountInfoMigration.KEY_SNAPSHOT_TIMESTAMP + 1;
        final byte[] historicalKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("old"))
                .build()
                .toByteArray();
        final Entity entity = createEntity(id, EntityType.ACCOUNT, true);
        entity.setKey(historicalKey);
        entity.setTimestampRange(Range.atLeast(afterSnapshot));
        entityRepository.save(entity);
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history ->
                        history.key(historicalKey.clone()).timestampRange(Range.closedOpen(0L, afterSnapshot)))
                .persist();

        final AccountInfo corrected = correctedKey(id, "new");
        assertThat(historicalAccountInfoMigration.updateKey(corrected)).isTrue();

        assertThat(entityRepository.findById(id).orElseThrow().getKey())
                .containsExactly(corrected.getKey().toByteArray());
        assertThat(entityRepository.findById(id).orElseThrow().getMemo()).isEqualTo("Bar");
        assertThat(historicalKey(id)).containsExactly(corrected.getKey().toByteArray());
    }

    @Test
    void propagatesKeyThroughLaterRowsWithTheSameKey() {
        final long id = 2L;
        final long snapshot = HistoricalAccountInfoMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long affectedEnd = snapshot + 1;
        final long laterEnd = snapshot + 2;
        final byte[] oldKey = ed25519("old");
        final Entity entity = createEntity(id, EntityType.ACCOUNT, true);
        entity.setKey(oldKey.clone());
        entity.setTimestampRange(Range.atLeast(laterEnd));
        entityRepository.save(entity);
        persistHistory(id, oldKey, 0L, snapshot);
        persistHistory(id, oldKey, snapshot, affectedEnd);
        persistHistory(id, oldKey, affectedEnd, laterEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(historicalAccountInfoMigration.updateKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).containsExactly(oldKey);
        assertThat(keys.get(1)).containsExactly(corrected);
        assertThat(keys.get(2)).containsExactly(corrected);
        assertThat(entityRepository.findById(id).orElseThrow().getKey()).containsExactly(corrected);
    }

    @Test
    void stopsPropagatingKeyAtFirstDifferentLaterRow() {
        final long id = 2L;
        final long snapshot = HistoricalAccountInfoMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long affectedEnd = snapshot + 10;
        final long sameEnd = snapshot + 20;
        final long changedEnd = snapshot + 30;
        final byte[] oldKey = ed25519("old");
        final byte[] changedKey = ed25519("changed");
        final Entity entity = createEntity(id, EntityType.ACCOUNT, true);
        entity.setKey(oldKey.clone());
        entity.setTimestampRange(Range.atLeast(changedEnd));
        entityRepository.save(entity);
        persistHistory(id, oldKey, snapshot, affectedEnd);
        persistHistory(id, oldKey, affectedEnd, sameEnd);
        persistHistory(id, changedKey, sameEnd, changedEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(historicalAccountInfoMigration.updateKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).containsExactly(corrected);
        assertThat(keys.get(1)).containsExactly(corrected);
        assertThat(keys.get(2)).containsExactly(changedKey);
        assertThat(entityRepository.findById(id).orElseThrow().getKey()).containsExactly(oldKey);
    }

    @Test
    void stopsPropagatingWhenCurrentEntityKeyChanged() {
        final long id = 2L;
        final long snapshot = HistoricalAccountInfoMigration.KEY_SNAPSHOT_TIMESTAMP;
        final long entityLower = snapshot + 10;
        final long laterEnd = snapshot + 20;
        final byte[] oldKey = ed25519("old");
        final byte[] changedKey = ed25519("changed");
        final Entity entity = createEntity(id, EntityType.ACCOUNT, true);
        entity.setKey(changedKey);
        entity.setTimestampRange(Range.atLeast(entityLower));
        entityRepository.save(entity);
        persistHistory(id, oldKey, snapshot, entityLower);
        persistHistory(id, oldKey, entityLower, laterEnd);

        final byte[] corrected = correctedKey(id, "new").getKey().toByteArray();
        assertThat(historicalAccountInfoMigration.updateKey(correctedKey(id, "new")))
                .isTrue();

        final List<byte[]> keys = historicalKeys(id);
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).containsExactly(corrected);
        assertThat(keys.get(1)).containsExactly(oldKey);
        assertThat(entityRepository.findById(id).orElseThrow().getKey()).containsExactly(changedKey);
    }

    @Test
    void skipsKeyThatAlreadyMatches() {
        final AccountInfo accountInfo = correctedKey(2L, "123");
        final Entity entity = createEntity(2L, EntityType.ACCOUNT, true);

        assertThat(historicalAccountInfoMigration.updateKey(accountInfo)).isFalse();
        assertThat(entityRepository.findById(entity.getId()).orElseThrow().getKey())
                .containsExactly(entity.getKey());
    }

    private static AccountInfo correctedKey(long accountNum, String key) {
        return AccountInfo.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(accountNum).build())
                .setKey(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8(key))
                        .build())
                .build();
    }

    private static byte[] ed25519(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build().toByteArray();
    }

    private void persistHistory(long id, byte[] key, long lower, long upper) {
        domainBuilder
                .entityHistory(EntityId.of(id))
                .customize(history -> history.key(key.clone()).timestampRange(Range.closedOpen(lower, upper)))
                .persist();
    }

    private byte[] historicalKey(long id) {
        return jdbcOperations.queryForObject(
                "select key from entity_history where id = ?", (result, row) -> result.getBytes(1), id);
    }

    private List<byte[]> historicalKeys(long id) {
        return jdbcOperations.query(
                "select key from entity_history where id = ? order by lower(timestamp_range)",
                (result, row) -> result.getBytes(1),
                id);
    }

    @SuppressWarnings("deprecation")
    private AccountInfo.Builder accountInfo() {
        return AccountInfo.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(ACCOUNT_ID1).build())
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1).build())
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(Key.newBuilder()
                        .setEd25519(ByteString.copyFromUtf8("abc"))
                        .build())
                .setMemo("Foo")
                .setProxyAccountID(AccountID.newBuilder()
                        .setShardNum(0)
                        .setRealmNum(0)
                        .setAccountNum(2)
                        .build());
    }

    private Entity createEntity(long num, EntityType type, boolean afterReset) {
        Entity entity = EntityId.of(0L, 0L, num).toEntity();
        entity.setDeclineReward(false);
        entity.setNum(num);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(type);
        entity.setId(num);
        entity.setDeleted(false);
        entity.setMemo("");
        entity.setTimestampRange(Range.atLeast(0L));
        entity.setType(type);

        if (afterReset) {
            Key key =
                    Key.newBuilder().setEd25519(ByteString.copyFromUtf8("123")).build();
            entity.setAutoRenewPeriod(5L);
            entity.setExpirationTimestamp(1L);
            entity.setKey(key.toByteArray());
            entity.setMemo("Bar");
            entity.setProxyAccountId(EntityId.of(0, 0, 3));
        }

        entityRepository.save(entity);
        return entity;
    }
}
