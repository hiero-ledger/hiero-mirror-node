// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenTransformerTest extends AbstractTransformerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenAirdrop(boolean assessedCustomFees) {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenAirdrop()
                .record(r -> {
                    if (assessedCustomFees) {
                        r.addAssessedCustomFees(recordItemBuilder.assessedCustomFee())
                                .addAssessedCustomFees(recordItemBuilder.assessedCustomFee());
                    } else {
                        r.clearAssessedCustomFees();
                    }
                })
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenAirdrop(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenAirdropUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenAirdrop()
                .record(Builder::clearNewPendingAirdrops)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenAirdrop(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenBurn() {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenBurn().customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenBurnUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .receipt(r -> r.clearNewTotalSupply().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenCreate() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(
                        b -> b.setInitialSupply(domainBuilder.number()).setTokenType(TokenType.FUNGIBLE_COMMON))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenCreate()
                .receipt(r -> r.clearTokenID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalize)
                .build();
        var blockTransaction = blockTransactionBuilder
                .unsuccessfulTransaction(expectedRecordItem)
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenMint(TokenType type) {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenMint(type).customize(this::finalize).build();
        var blockTransaction =
                blockTransactionBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenMintUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenMint()
                .receipt(r -> r.clearNewTotalSupply().clearSerialNumbers())
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipe(TokenType type) {
        // given
        var builder = recordItemBuilder.tokenWipe(type).customize(this::finalize);
        var expectedRecordItem = type.equals(TokenType.NON_FUNGIBLE_UNIQUE)
                ? builder.transactionBody(t -> t.setAmount(0).addSerialNumbers(2))
                        .build()
                : builder.build();
        var blockTransaction =
                blockTransactionBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipeUnsuccessful(TokenType type) {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenWipe(type)
                .receipt(r -> r.clearNewTotalSupply().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalize)
                .build();
        var blockTransaction =
                blockTransactionBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }
}
