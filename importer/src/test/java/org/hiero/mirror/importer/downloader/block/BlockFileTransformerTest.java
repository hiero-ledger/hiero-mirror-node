// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.hedera.hapi.block.stream.protoc.Block;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class BlockFileTransformerTest extends ImporterIntegrationTest {

    private final BlockStreamReader reader;
    private final BlockFileTransformer transformer;

    @Test
    void transform() {
        //        var name = "000000000000000000000000000062370992.blk.gz";
        var name =
                "000000000000000000000000000062370980.blk.gz"; // with contract call transactions, block items 197-201
        var file = TestUtils.getResource("data/blockstreams/" + name);
        var streamFileData = StreamFileData.from(file);
        byte[] bytes = streamFileData.getBytes();
        var blockStream = new BlockStream(
                getBlock(streamFileData).getItemsList(),
                bytes,
                name,
                System.currentTimeMillis(),
                System.currentTimeMillis());
        var blockFile = reader.read(blockStream);
        assertThatCode(() -> transformer.transform(blockFile)).doesNotThrowAnyException();
    }

    @SneakyThrows
    private Block getBlock(StreamFileData blockFileData) {
        try (var is = blockFileData.getInputStream()) {
            return Block.parseFrom(is.readAllBytes());
        }
    }
}
