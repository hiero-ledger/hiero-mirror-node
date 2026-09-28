// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import jakarta.annotation.Resource;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.validation.HexValidator;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
class RecordFileServiceTest extends Web3IntegrationTest {
    private final RecordFileService recordFileService;

    @Resource
    private JdbcTemplate jdbcTemplate;

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
}
