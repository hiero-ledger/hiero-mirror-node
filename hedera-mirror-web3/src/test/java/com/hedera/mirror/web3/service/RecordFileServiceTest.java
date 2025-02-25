// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class RecordFileServiceTest extends Web3IntegrationTest {
    private final RecordFileService recordFileService;

    @Test
    void testFindByTimestamp() {
        var timestamp = domainBuilder.timestamp();
        var recordFile = domainBuilder
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
        var recordFileLatest =
                domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        assertThat(recordFileService.findByBlockType(BlockType.LATEST)).contains(recordFileLatest);
    }

    @Test
    void testFindByBlockTypeIndex() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        var recordFile = domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        var blockType = BlockType.of(recordFile.getIndex().toString());
        assertThat(recordFileService.findByBlockType(blockType)).contains(recordFile);
    }

    @Test
    void testFindByBlockTypeIndexOutOfRange() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(1L)).persist();
        domainBuilder.recordFile().customize(f -> f.index(2L)).persist();
        var recordFileLatest =
                domainBuilder.recordFile().customize(f -> f.index(3L)).persist();
        var blockType = BlockType.of(String.valueOf(recordFileLatest.getIndex() + 1L));
        assertThatThrownBy(() -> recordFileService.findByBlockType(blockType))
                .isInstanceOf(BlockNumberOutOfRangeException.class)
                .hasMessage(UNKNOWN_BLOCK_NUMBER);
    }
}
