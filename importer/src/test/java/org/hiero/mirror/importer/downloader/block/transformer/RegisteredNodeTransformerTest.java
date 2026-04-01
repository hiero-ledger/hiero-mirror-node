// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RegisteredNodeTransformerTest extends AbstractTransformerTest {

    @Test
    void registeredNodeCreateTransform() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .registeredNodeCreate()
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.registeredNodeCreate(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void registeredNodeCreateTransformWhenStatusIsNotSuccess() {
        // given
        final var expectedRecordItem = recordItemBuilder
                .registeredNodeCreate()
                .receipt(r -> r.clearRegisteredNodeId().setStatus(ResponseCodeEnum.INVALID_NODE_ID))
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.registeredNodeCreate(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
