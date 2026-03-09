// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FileTransformerTest extends AbstractTransformerTest {

    @Test
    void fileCreateTransform() {
        // given
        final var expectedRecordItem =
                recordItemBuilder.fileCreate().customize(this::finalize).build();
        final var blockTransaction =
                blockTransactionBuilder.fileCreate(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void fileCreateTransformWhenStatusIsNotSuccess() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .receipt(r -> r.clearFileID().setStatus(ResponseCodeEnum.AUTHORIZATION_FAILED))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.fileCreate(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();
        blockFile.setLastWrappedRecordBlockHash(recordItemBuilder.text(96));

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
