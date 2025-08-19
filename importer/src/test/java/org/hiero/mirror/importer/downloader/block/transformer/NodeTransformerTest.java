// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeTransformerTest extends AbstractTransformerTest {

    @Test
    void nodeCreateTransform() {
        // given
        var expectedRecordItem =
                recordItemBuilder.nodeCreate().customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.nodeCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void nodeCreateTransformWhenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .nodeCreate()
                .receipt(r -> r.clearNodeId().setStatus(ResponseCodeEnum.INVALID_NODE_ID))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.nodeCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
