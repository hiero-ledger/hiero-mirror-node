// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class CryptoTransformerTest extends AbstractTransformerTest {

    private static Stream<Arguments> provideAliasAndExpectedEvmAddress() {
        var ecDsaAlias = ByteString.copyFrom(
                Hex.decode("3a21029afbc3562d9ebb6e6a4d784fd7bf7389ea3047dd2a7ad3864192326388185387"));
        var evmAddressFromEcDsaAlias = ByteString.copyFrom(Hex.decode("5b718ea220b7784a8f4069b18506155f2ea28ef6"));
        var randomAlias = recordItemBuilder.bytes(20);
        return Stream.of(
                arguments(ByteString.EMPTY, ByteString.EMPTY),
                arguments(recordItemBuilder.key().toByteString(), ByteString.EMPTY),
                arguments(ecDsaAlias, evmAddressFromEcDsaAlias),
                arguments(randomAlias, ByteString.EMPTY));
    }

    @ParameterizedTest
    @MethodSource("provideAliasAndExpectedEvmAddress")
    void cryptoCreate(ByteString alias, ByteString expectedEvmAddress) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.setEvmAddress(expectedEvmAddress))
                .transactionBody(b -> b.setAlias(alias))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

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
        var blockTransaction =
                blockTransactionBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

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
        var blockItem1 =
                blockTransactionBuilder.cryptoTransfer(expectedRecordItem).build();
        var blockItem2 =
                blockTransactionBuilder.cryptoTransfer(expectedRecordItem2).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem1, blockItem2)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, List.of(expectedRecordItem, expectedRecordItem2));
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }

    @Test
    void cryptoTransferLegacyTransaction() {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer()
                .useTransactionBodyBytesAndSigMap(true)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.cryptoTransfer(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cryptoUpdate(boolean hasAlias) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoUpdate()
                .transactionBody(b -> {
                    if (hasAlias) {
                        b.getAccountIDToUpdateBuilder().setAlias(recordItemBuilder.bytes(34));
                    }
                })
                .customize(this::finalize)
                .build();
        final var blockTransaction =
                blockTransactionBuilder.cryptoUpdate(expectedRecordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
