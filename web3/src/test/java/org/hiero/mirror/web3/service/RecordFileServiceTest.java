// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import jakarta.annotation.Resource;
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

        // Warm the index cache via a timestamp lookup, then remove the underlying row.
        assertThat(recordFileService.findByTimestamp(timestamp)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");

        // The index cache must only be keyed by block index, never by the timestamp: passing the timestamp
        // as a block number must not resolve to the record file (guards against cross-key cache poisoning).
        final var timestampAsBlock = BlockType.of(String.valueOf(timestamp));
        assertThat(recordFileService.findByBlockType(timestampAsBlock))
                .as("a timestamp passed as a block index must not hit the warmed index cache")
                .isEmpty();
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
