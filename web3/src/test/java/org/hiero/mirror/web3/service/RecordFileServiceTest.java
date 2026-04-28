// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.validation.HexValidator;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class RecordFileServiceTest extends Web3IntegrationTest {
    private final RecordFileService recordFileService;

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
