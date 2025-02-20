// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class CryptoTransformerTest extends AbstractTransformerTest {

    @ParameterizedTest
    @MethodSource("provideAliasAndExpectedEvmAddress")
    void cryptoCreate(ByteString alias, ByteString expectedEvmAddress) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.clearEvmAddress().setEvmAddress(expectedEvmAddress))
                .transactionBody(b -> b.clearAlias().setAlias(alias))
                .customize(this::finalize)
                .build();
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void cryptoCreateUnsuccessfulTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .receipt(r -> r.clearAccountID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalize)
                .build();
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(value = TransferType.class)
    void cryptoTransfer(TransferType transferType) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer(transferType)
                .customize(this::finalize)
                .build();
        var expectedRecordItem2 = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalize)
                .build();
        var blockItem1 = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var blockItem2 = blockItemBuilder.cryptoTransfer(expectedRecordItem2).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem1, blockItem2)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, List.of(expectedRecordItem, expectedRecordItem2));
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }

    private static Stream<Arguments> provideAliasAndExpectedEvmAddress() {
        RecordItemBuilder recordItemBuilder1 = new RecordItemBuilder();
        var randomAlias = recordItemBuilder1.bytes(20);
        return Stream.of(
                arguments(ByteString.EMPTY, ByteString.EMPTY),
                arguments(recordItemBuilder1.key().toByteString(), ByteString.EMPTY),
                arguments(randomAlias, randomAlias));
    }
}
