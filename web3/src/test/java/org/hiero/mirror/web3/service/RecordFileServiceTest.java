// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.validation.HexValidator;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
class RecordFileServiceTest extends Web3IntegrationTest {

    private static final long CONSENSUS_END = RecordFileServiceTest.TIMESTAMP + 1;
    private static final String HASH = "a".repeat(96);
    private static final long INDEX = 5L;
    private static final long TIMESTAMP = 100L;

    /**
     * A record file with the fixed keys above, served by {@link #serviceWithMockRepository} through a mocked repository
     * so tests can control and count queries.
     */
    private RecordFile cachedRecordFile;

    private CacheManager hashCacheManager;
    private CacheManager indexCacheManager;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private RecordFileRepository mockRecordFileRepository;
    private final RecordFileService recordFileService;
    private RecordFileService serviceWithMockRepository;

    /**
     * Drives the expiry of the caches behind {@link #serviceWithMockRepository}.
     */
    private AtomicLong ticker;

    private CacheManager timestampCacheManager;

    @BeforeEach
    void setupServiceWithMockRepository() {
        ticker = new AtomicLong();
        hashCacheManager = cacheManager(CACHE_NAME);
        indexCacheManager = cacheManager(CACHE_NAME);
        timestampCacheManager = cacheManager(CACHE_NAME);
        mockRecordFileRepository = mock(RecordFileRepository.class);
        serviceWithMockRepository = new RecordFileServiceImpl(
                mockRecordFileRepository,
                cacheManager(CACHE_NAME),
                hashCacheManager,
                indexCacheManager,
                cacheManager(CACHE_NAME_RECORD_FILE_LATEST),
                timestampCacheManager);
        cachedRecordFile = domainBuilder
                .recordFile()
                .customize(
                        r -> r.hash(HASH).index(INDEX).consensusStart(TIMESTAMP).consensusEnd(CONSENSUS_END))
                .get();
    }

    @Test
    void testFindByTimestamp() {
        final var timestamp = domainBuilder.timestamp();
        final var recordFile = domainBuilder
                .recordFile()
                .customize(e -> e.consensusEnd(timestamp))
                .persist();
        assertThat(recordFileService.findByTimestamp(timestamp)).contains(recordFile);
    }

    @Test
    void findByTimestampWarmsIndexCacheByIndex() {
        final var timestamp = domainBuilder.timestamp();
        final var recordFile = domainBuilder
                .recordFile()
                .customize(e -> e.consensusEnd(timestamp))
                .persist();

        // Resolving by timestamp warms the index cache under the record file's index.
        assertThat(recordFileService.findByTimestamp(timestamp)).contains(recordFile);

        jdbcTemplate.update("delete from record_file");

        final var byIndex = BlockType.of(recordFile.getIndex().toString());
        assertThat(recordFileService.findByBlockType(byIndex))
                .as("findByBlockType(index) should be served from the index cache warmed by findByTimestamp")
                .contains(recordFile);
    }

    @Test
    void findByTimestampDoesNotWarmIndexCacheUnderTimestampKey() {
        final var timestamp = domainBuilder.timestamp();
        final var recordFile = domainBuilder
                .recordFile()
                .customize(e -> e.consensusEnd(timestamp))
                .persist();

        assertThat(recordFileService.findByTimestamp(timestamp)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        final var timestampAsBlock = BlockType.of(String.valueOf(timestamp));
        assertThat(recordFileService.findByBlockType(timestampAsBlock))
                .as("a timestamp passed as a block index must not hit the warmed index cache")
                .isEmpty();
    }

    @Test
    void findByTimestampServesFromCacheWithoutQueryingDatabase() {
        final var timestamp = domainBuilder.timestamp();
        final var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(timestamp);
                    r.consensusEnd(timestamp + 1);
                })
                .persist();

        assertThat(recordFileService.findByTimestamp(timestamp)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByTimestamp(timestamp))
                .as("findByTimestamp should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByTimestampDoesNotCacheMissingRecordFile() {
        final var timestamp = domainBuilder.timestamp();
        assertThat(recordFileService.findByTimestamp(timestamp)).isEmpty();

        final var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(timestamp);
                    r.consensusEnd(timestamp + 1);
                })
                .persist();

        assertThat(recordFileService.findByTimestamp(timestamp))
                .as("a record file ingested after a miss must resolve immediately")
                .contains(recordFile);
    }

    @Test
    void findByIndexServesFromCacheWithoutQueryingDatabase() {
        final var recordFile = domainBuilder.recordFile().persist();
        final long index = recordFile.getIndex();

        assertThat(recordFileService.findByIndex(index)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByIndex(index))
                .as("findByIndex should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByIndexDoesNotCacheMissingRecordFile() {
        final long index = 42L;
        assertThat(recordFileService.findByIndex(index)).isEmpty();

        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.index(index)).persist();

        assertThat(recordFileService.findByIndex(index))
                .as("a record file ingested after a miss must resolve immediately")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeHashServesFromCacheWithoutQueryingDatabase() {
        final var recordFile = domainBuilder.recordFile().persist();
        final var blockType = BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash());

        assertThat(recordFileService.findByBlockType(blockType)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(blockType))
                .as("a block hash lookup should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeHashDoesNotCacheMissingRecordFile() {
        final var hash = "a".repeat(96);
        final var blockType = BlockType.of(HexValidator.HEX_PREFIX + hash);
        assertThat(recordFileService.findByBlockType(blockType)).isEmpty();

        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.hash(hash)).persist();

        assertThat(recordFileService.findByBlockType(blockType))
                .as("a record file ingested after a hash miss must resolve immediately")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeHashNormalizesCacheKey() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        // BlockType.of() lowercases the hash, so every casing of it must share one cache entry.
        final var upperCase =
                BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash().toUpperCase(Locale.ROOT));
        assertThat(recordFileService.findByBlockType(upperCase))
                .as("an upper case hash should hit the cache entry of its lower case form")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeHashWarmsIndexCache() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByIndex(recordFile.getIndex()))
                .as("findByIndex should be served from the index cache warmed by the hash lookup")
                .contains(recordFile);
    }

    @Test
    void findByIndexWarmsHashAndTimestampCaches() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByIndex(recordFile.getIndex())).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .as("a block hash lookup should be served from the hash cache warmed by the index lookup")
                .contains(recordFile);
        assertThat(recordFileService.findByTimestamp(recordFile.getConsensusEnd()))
                .as("findByTimestamp should be served from the timestamp cache warmed by the index lookup")
                .contains(recordFile);
    }

    @Test
    void findByTimestampWarmsHashCache() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByTimestamp(recordFile.getConsensusStart()))
                .contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .as("a block hash lookup should be served from the hash cache warmed by the timestamp lookup")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeHashWarmsTimestampCache() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByTimestamp(recordFile.getConsensusEnd()))
                .as("findByTimestamp should be served from the timestamp cache warmed by the hash lookup")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeLatestWarmsHashAndTimestampCaches() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash())))
                .contains(recordFile);
        assertThat(recordFileService.findByTimestamp(recordFile.getConsensusEnd()))
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeEarliestServesFromCacheWithoutQueryingDatabase() {
        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.index(0L)).persist();

        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST))
                .as("the earliest lookup should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeEarliestWarmsIndexCache() {
        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.index(0L)).persist();

        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByIndex(0L))
                .as("findByIndex should be served from the index cache warmed by the earliest lookup")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeEarliestDoesNotCacheMissingRecordFile() {
        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST)).isEmpty();

        // This cache has no TTL at all, so a cached miss would outlive every other one - it would persist for the
        // lifetime of the process, leaving the genesis block permanently unresolvable.
        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.index(0L)).persist();

        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST))
                .as("a record file ingested after a miss must resolve immediately")
                .contains(recordFile);
    }

    @Test
    void testFindByBlockTypeEarliest() {
        final var genesisRecordFile =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        assertThat(recordFileService.findByBlockType(BlockType.EARLIEST)).contains(genesisRecordFile);
    }

    @Test
    void testFindByBlockTypeLatest() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        final var recordFileLatest =
                domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).contains(recordFileLatest);
    }

    @Test
    void findByBlockTypeLatestDoesNotCacheMissingRecordFile() {
        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).isEmpty();

        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.LATEST))
                .as("a record file ingested after a miss must resolve immediately")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeLatestServesFromCacheWithoutQueryingDatabase() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByBlockType(BlockType.LATEST))
                .as("the latest lookup should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByBlockTypeLatestWarmsIndexCache() {
        final var recordFile = domainBuilder.recordFile().persist();

        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        assertThat(recordFileService.findByIndex(recordFile.getIndex()))
                .as("findByIndex should be served from the index cache warmed by the latest lookup")
                .contains(recordFile);
    }

    @Test
    void testFindByBlockTypeIndex() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        final var recordFile =
                domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        final var blockType = BlockType.of(recordFile.getIndex().toString());
        assertThat(recordFileService.findByBlockType(blockType)).contains(recordFile);
    }

    @Test
    void testFindByBlockTypeIndexOutOfRange() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        final var recordFileLatest =
                domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        final var blockType = BlockType.of(String.valueOf(recordFileLatest.getIndex() + 1L));
        assertThat(recordFileService.findByBlockType(blockType)).isEmpty();
    }

    @Test
    void testFindByBlockTypeFullRecordFileHash() {
        final var recordFile = domainBuilder.recordFile().persist();
        final var blockType = BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash());
        assertThat(recordFileService.findByBlockType(blockType)).contains(recordFile);
    }

    @Test
    void testFindByBlockTypeShortRecordFileHash() {
        final var recordFile = domainBuilder.recordFile().persist();
        final var shortHash = recordFile.getHash().substring(0, 64);
        final var blockType = BlockType.of(HexValidator.HEX_PREFIX + shortHash);
        assertThat(recordFileService.findByBlockType(blockType)).contains(recordFile);
    }

    @Test
    void testFindByBlockTypeByRecordFileHashNotFound() {
        domainBuilder.recordFile().persist();
        final var differentHash = HexValidator.HEX_PREFIX + "a".repeat(96);
        final var blockType = BlockType.of(differentHash);
        assertThat(recordFileService.findByBlockType(blockType)).isEmpty();
    }

    @Test
    void concurrentMissesOnSameRecordFileByDifferentKeysDoNotDeadlock() {
        final var recordFile = domainBuilder.recordFile().get();
        final var bothLoading = new CountDownLatch(2);

        // The mocked repository lets each query hold its cache entry until the other loader is also mid-load, so both
        // loaders cross-populate the other's cache while their own entry is still being computed.
        when(mockRecordFileRepository.findByHash(recordFile.getHash()))
                .thenAnswer(_ -> awaitBoth(bothLoading, recordFile));
        when(mockRecordFileRepository.findByIndex(recordFile.getIndex()))
                .thenAnswer(_ -> awaitBoth(bothLoading, recordFile));

        final var byHash = BlockType.of(HexValidator.HEX_PREFIX + recordFile.getHash());
        // Daemon threads that are never awaited, so a regression fails on the timeout instead of hanging the build on
        // threads blocked on each other's cache entry locks, which cannot be interrupted.
        final var executor = Executors.newFixedThreadPool(2, runnable -> {
            final var thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        final var hashResult = executor.submit(() -> serviceWithMockRepository.findByBlockType(byHash));
        final var indexResult = executor.submit(() -> serviceWithMockRepository.findByIndex(recordFile.getIndex()));
        executor.shutdown();

        assertThat(hashResult).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(Optional.of(recordFile));
        assertThat(indexResult).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(Optional.of(recordFile));
    }

    @ParameterizedTest
    @EnumSource(Lookup.class)
    void everyLoadWarmsCanonicalKeysAndRetainsRequestedKey(final Lookup lookup) {
        when(lookup.query(mockRecordFileRepository)).thenReturn(Optional.of(cachedRecordFile));

        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);
        assertThat(serviceWithMockRepository.findByIndex(INDEX)).contains(cachedRecordFile);
        assertThat(serviceWithMockRepository.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + HASH)))
                .contains(cachedRecordFile);
        assertThat(serviceWithMockRepository.findByTimestamp(CONSENSUS_END)).contains(cachedRecordFile);
        // Hash and timestamp cases use a prefix and an interior timestamp, distinct from the canonical keys.
        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);

        lookup.query(verify(mockRecordFileRepository));
        verifyNoMoreInteractions(mockRecordFileRepository);
    }

    @ParameterizedTest
    @EnumSource(Lookup.class)
    void cacheHitDoesNotRefreshOtherEntries(final Lookup lookup) {
        when(lookup.query(mockRecordFileRepository)).thenReturn(Optional.of(cachedRecordFile));
        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);

        ticker.set(Duration.ofMinutes(9).toNanos());
        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);
        ticker.set(Duration.ofMinutes(11).toNanos());

        assertThat(hashCacheManager.getCache(CACHE_NAME).get(HASH)).isNull();
        assertThat(indexCacheManager.getCache(CACHE_NAME).get(INDEX)).isNull();
        assertThat(timestampCacheManager.getCache(CACHE_NAME).get(CONSENSUS_END))
                .isNull();
        lookup.query(verify(mockRecordFileRepository));
        verifyNoMoreInteractions(mockRecordFileRepository);
    }

    @ParameterizedTest
    @EnumSource(Lookup.class)
    void missingResultCanBeRetried(final Lookup lookup) {
        when(lookup.query(mockRecordFileRepository))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cachedRecordFile));

        assertThat(lookup.call(serviceWithMockRepository)).isEmpty();
        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);
        lookup.query(verify(mockRecordFileRepository, times(2)));
    }

    @ParameterizedTest
    @EnumSource(Lookup.class)
    void failedLoadCanBeRetried(final Lookup lookup) {
        final var failure = new DataAccessResourceFailureException("Database unavailable");
        when(lookup.query(mockRecordFileRepository)).thenThrow(failure).thenReturn(Optional.of(cachedRecordFile));

        assertThatThrownBy(() -> lookup.call(serviceWithMockRepository)).isSameAs(failure);
        assertThat(lookup.call(serviceWithMockRepository)).contains(cachedRecordFile);
        lookup.query(verify(mockRecordFileRepository, times(2)));
    }

    private static Optional<RecordFile> awaitBoth(final CountDownLatch bothLoading, final RecordFile recordFile)
            throws InterruptedException {
        bothLoading.countDown();
        bothLoading.await(5, TimeUnit.SECONDS);
        return Optional.of(recordFile);
    }

    private CacheManager cacheManager(final String name) {
        final var manager = new CaffeineCacheManager();
        manager.setCacheNames(Set.of(name));
        manager.setCaffeine(Caffeine.newBuilder().ticker(ticker::get).expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }

    private enum Lookup {
        EARLIEST,
        HASH_PREFIX,
        INDEX,
        LATEST,
        TIMESTAMP;

        Optional<RecordFile> call(final RecordFileService service) {
            return switch (this) {
                case EARLIEST -> service.findByBlockType(BlockType.EARLIEST);
                case HASH_PREFIX ->
                    service.findByBlockType(BlockType.of(HexValidator.HEX_PREFIX + HASH.substring(0, 64)));
                case INDEX -> service.findByIndex(RecordFileServiceTest.INDEX);
                case LATEST -> service.findByBlockType(BlockType.LATEST);
                case TIMESTAMP -> service.findByTimestamp(RecordFileServiceTest.TIMESTAMP);
            };
        }

        Optional<RecordFile> query(final RecordFileRepository repository) {
            return switch (this) {
                case EARLIEST -> repository.findEarliest();
                case HASH_PREFIX -> repository.findByHash(HASH.substring(0, 64));
                case INDEX -> repository.findByIndex(RecordFileServiceTest.INDEX);
                case LATEST -> repository.findLatest();
                case TIMESTAMP -> repository.findByTimestamp(RecordFileServiceTest.TIMESTAMP);
            };
        }
    }
}
