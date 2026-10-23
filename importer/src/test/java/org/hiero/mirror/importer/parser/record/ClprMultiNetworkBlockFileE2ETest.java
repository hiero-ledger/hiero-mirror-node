// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.protoc.Block;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A real block captured from ledgerA of a local two-network {@code hiero-consensus-node} v0.80.0-alpha.3 run of
 * {@code ClprHieroToHieroSuite.configUpdateMidStreamBumpsBundleCap}, the only scenario in the suite that produces a
 * {@code CONTROL}-type {@code ClprMessage} ({@code ClprConfigUpdate}, prepended to an outbound bundle when the
 * sender's config changed since the channel's last recorded config timestamp). See
 * {@code data/blockstreams-clpr/README.md} for the full breakdown and a catalog of other CLPR content captured in
 * the same run but not (yet) turned into a fixture.
 *
 * <p>Parsed standalone, unlike {@code 001.blk.zstd}/{@code 002.blk.zstd} (a chained pair from a different,
 * single-network capture): this block's own transactions don't carry a {@code ClprChannel} map-update (the channel's
 * fields are unchanged by this bundle, so the real node didn't re-emit one), so no {@code clpr_channel} row exists
 * unless the preceding register/complete blocks are parsed first. {@code clpr_message} has no such dependency —
 * it's insert-only with no foreign key — so this fixture only asserts on message rows.
 */
@RequiredArgsConstructor
class ClprMultiNetworkBlockFileE2ETest extends ImporterIntegrationTest {

    private static final String CLPR_BLOCK_FILE = "data/blockstreams-clpr/0000/0000/0000/0000/003.blk.zstd";

    private final BlockFileTransformer blockFileTransformer;
    private final BlockStreamReader blockStreamReader;
    private final ClprMessageRepository clprMessageRepository;
    private final RecordFileParser recordFileParser;

    @BeforeEach
    void setup() {
        // RecordFileParser's hash-chain check is in-memory and shared across test classes in this Spring context;
        // this fixture isn't chained to any other captured block, so reset it before parsing.
        recordFileParser.clear();
    }

    @Test
    void clprControlMessageConfigPropagation() throws Exception {
        // given/when
        var file = TestUtils.getResource(CLPR_BLOCK_FILE);
        var streamFileData = StreamFileData.from(file);
        var bytes = streamFileData.getBytes();
        var block = Block.parseFrom(streamFileData.getInputStream());
        var loadStart = System.currentTimeMillis();
        var blockStream =
                new BlockStream(block.getItemsList(), loadStart, bytes, file.getName(), loadStart - 1000, bytes.length);
        var blockFile = blockStreamReader.read(blockStream);
        var recordFile = blockFileTransformer.transform(blockFile);
        recordFileParser.parse(recordFile);

        // then
        assertThat(clprMessageRepository.count()).isEqualTo(7L);
        assertThat(clprMessageRepository.findAll())
                .filteredOn(m -> m.getMessageType() == ClprMessageType.CONTROL)
                .singleElement()
                .satisfies(control -> {
                    assertThat(control.getMessageId()).isEqualTo(7L);
                    assertThat(control.getControlMessage()).isNotNull();
                    var configUpdate = control.getControlMessage().getConfigUpdate();
                    assertThat(configUpdate).isNotNull();
                    assertThat(configUpdate.getConfiguration().getMaxMessagesPerBundle())
                            .isEqualTo(6L);
                });
        assertThat(clprMessageRepository.findAll())
                .filteredOn(m -> m.getMessageType() == ClprMessageType.DATA)
                .hasSize(6);
    }
}
