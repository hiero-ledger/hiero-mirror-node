// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tokenBurnOrWipeDuplicateSerialNumbersInBatch(boolean burn) {
        // given
        // Consensus nodes de-duplicate serial numbers, so burning / wiping [2, 2, 2] only reduces the supply by 1
        var tokenId = recordItemBuilder.tokenId();
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(StateIdentifier.STATE_ID_TOKENS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setTokenValue(Token.newBuilder()
                                                .setTokenId(tokenId)
                                                .setTotalSupply(7L)))))
                .build();
        var atomicBatchRecordItem =
                recordItemBuilder.atomicBatch().customize(this::finalize).build();
        var atomicBatchBlockTransaction = blockTransactionBuilder
                .atomicBatch(atomicBatchRecordItem)
                .stateChanges(s -> s.add(stateChanges))
                .build();
        var parentConsensusTimestamp =
                atomicBatchRecordItem.getTransactionRecord().getConsensusTimestamp();

        var recordItem1 = innerBurnOrWipe(burn, tokenId, List.of(1L), 8L, 1, parentConsensusTimestamp);
        var blockTransaction1 = burnOrWipeBlockTransaction(burn, recordItem1)
                .previous(atomicBatchBlockTransaction)
                .stateChanges(List::clear)
                .build();
        var recordItem2 = innerBurnOrWipe(burn, tokenId, List.of(2L, 2L, 2L), 7L, 2, parentConsensusTimestamp);
        var blockTransaction2 = burnOrWipeBlockTransaction(burn, recordItem2)
                .previous(blockTransaction1)
                .stateChanges(List::clear)
                .build();

        var blockFile = blockFileBuilder
                .items(List.of(atomicBatchBlockTransaction, blockTransaction1, blockTransaction2))
                .build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        var expected = List.of(atomicBatchRecordItem, recordItem1, recordItem2);
        assertRecordFile(recordFile, blockFile, items -> assertRecordItems(items, expected));
    }

    private RecordItem innerBurnOrWipe(
            boolean burn,
            TokenID tokenId,
            List<Long> serialNumbers,
            long newTotalSupply,
            int transactionIndex,
            Timestamp parentConsensusTimestamp) {
        if (burn) {
            return recordItemBuilder
                    .tokenBurn()
                    .transactionBody(b -> b.setAmount(0)
                            .setToken(tokenId)
                            .clearSerialNumbers()
                            .addAllSerialNumbers(serialNumbers))
                    .clearIncrementer()
                    .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                    .receipt(r -> r.setNewTotalSupply(newTotalSupply))
                    .recordItem(r -> r.transactionIndex(transactionIndex))
                    .customize(this::finalize)
                    .build();
        }

        return recordItemBuilder
                .tokenWipe(TokenType.NON_FUNGIBLE_UNIQUE)
                .transactionBody(b ->
                        b.setAmount(0).setToken(tokenId).clearSerialNumbers().addAllSerialNumbers(serialNumbers))
                .clearIncrementer()
                .record(r -> r.setParentConsensusTimestamp(parentConsensusTimestamp))
                .receipt(r -> r.setNewTotalSupply(newTotalSupply))
                .recordItem(r -> r.transactionIndex(transactionIndex))
                .customize(this::finalize)
                .build();
    }

    private BlockTransactionBuilder.Builder burnOrWipeBlockTransaction(boolean burn, RecordItem recordItem) {
        return burn ? blockTransactionBuilder.tokenBurn(recordItem) : blockTransactionBuilder.tokenWipe(recordItem);
    }

    @Test
    void tokenBurnTotalSupplyAbsent() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .receipt(TransactionReceipt.Builder::clearNewTotalSupply)
                .customize(b -> b.recordItem(r -> r.tokenTotalSupplyAbsent(true)))
                .customize(this::finalize)
                .build();
        // the token's state change is partial without total supply, since the unit's net change is zero
        var blockTransaction = blockTransactionBuilder
                .tokenBurn(expectedRecordItem)
                .stateChanges(stateChangesList -> stateChangesList.replaceAll(stateChanges -> {
                    var builder = stateChanges.toBuilder();
                    for (var stateChange : builder.getStateChangesBuilderList()) {
                        if (stateChange.getStateId() == STATE_ID_TOKENS_VALUE) {
                            stateChange
                                    .getMapUpdateBuilder()
                                    .setPartial(true)
                                    .getValueBuilder()
                                    .getTokenValueBuilder()
                                    .clearTotalSupply();
                        }
                    }
                    return builder.build();
                }))
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenBurnTotalSupplyMissing() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .receipt(TransactionReceipt.Builder::clearNewTotalSupply)
                .customize(b -> b.recordItem(r -> r.tokenTotalSupplyAbsent(true)))
                .customize(this::finalize)
                .build();
        // the token's state change is missing, a recoverable error is logged instead of failing the block
        var blockTransaction = blockTransactionBuilder
                .tokenBurn(expectedRecordItem)
                .stateChanges(stateChangesList -> stateChangesList.replaceAll(stateChanges -> {
                    var builder = StateChanges.newBuilder().setConsensusTimestamp(stateChanges.getConsensusTimestamp());
                    for (var stateChange : stateChanges.getStateChangesList()) {
                        if (stateChange.getStateId() != STATE_ID_TOKENS_VALUE) {
                            builder.addStateChanges(stateChange);
                        }
                    }
                    return builder.build();
                }))
                .build();
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
