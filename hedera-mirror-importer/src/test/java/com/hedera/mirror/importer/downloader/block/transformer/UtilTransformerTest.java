// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UtilTransformerTest extends AbstractTransformerTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrng(int range) {
        // given
        var expectedRecordItem =
                recordItemBuilder.prng(range).customize(this::finalize).build();
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();
        var expectedRecordItemBytes = recordItemBuilder
                .prng(range)
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var blockItemBytes = blockItemBuilder.utilPrng(expectedRecordItemBytes).build();
        var blockFile =
                blockFileBuilder.items(List.of(blockItem, blockItemBytes)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, List.of(expectedRecordItem, expectedRecordItemBytes));
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrngWhenStatusNotSuccess(int range) {
        // given
        var expectedRecordItem = recordItemBuilder
                .prng(range)
                .record(TransactionRecord.Builder::clearEntropy)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
