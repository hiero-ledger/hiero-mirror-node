// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.protoc.Block;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.junit.jupiter.api.Test;

/**
 * TODO(CLPR): {@code data/blockstreams-clpr/.../001.blk.zstd} is an empty placeholder, not a real captured block
 * stream. Replace it with a real CLPR block file and fill in the assertions below. Expected to fail until then.
 */
@RequiredArgsConstructor
class ClprBlockFileE2ETest extends ImporterIntegrationTest {

    private static final String CLPR_BLOCK_FILE = "data/blockstreams-clpr/0000/0000/0000/0000/001.blk.zstd";

    private final BlockFileTransformer blockFileTransformer;
    private final BlockStreamReader blockStreamReader;
    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMessageRepository clprMessageRepository;
    private final RecordFileParser recordFileParser;

    @Test
    void clprBlockFileLifecycle() throws Exception {
        // given
        var file = TestUtils.getResource(CLPR_BLOCK_FILE);
        var streamFileData = StreamFileData.from(file);
        var bytes = streamFileData.getBytes();
        var block = Block.parseFrom(streamFileData.getInputStream());
        var loadStart = System.currentTimeMillis();
        var blockStream =
                new BlockStream(block.getItemsList(), loadStart, bytes, file.getName(), loadStart - 1000, bytes.length);

        // when
        var blockFile = blockStreamReader.read(blockStream);
        var recordFile = blockFileTransformer.transform(blockFile);
        recordFileParser.parse(recordFile);

        // then -- TODO(CLPR): replace with real expected assertions once a real block file is available
        assertThat(clprChannelRepository.count()).isPositive();
        assertThat(clprConnectorRepository.count()).isPositive();
        assertThat(clprLedgerConfigurationRepository.count()).isPositive();
        assertThat(clprMessageRepository.count()).isPositive();
    }
}
