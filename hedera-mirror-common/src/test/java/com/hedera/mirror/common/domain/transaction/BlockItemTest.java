// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import static com.hedera.mirror.common.domain.transaction.StateChangeContext.EMPTY_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BlockItemTest {

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessWhenNoParentPresentAndCorrectStatusReturnTrue(ResponseCodeEnum status) {
        var transactionResult = TransactionResult.newBuilder().setStatus(status).build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        assertThat(blockItem)
                .returns(0L, BlockItem::getConsensusTimestamp)
                .returns(null, BlockItem::getParentConsensusTimestamp)
                .returns(true, BlockItem::isSuccessful);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessWithSuccessfulParentAndCorrectStatusReturnTrue(ResponseCodeEnum status) {

        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(status)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var transactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12346L))
                .setStatus(status)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        assertThat(blockItem)
                .returns(12346000000000L, BlockItem::getConsensusTimestamp)
                .returns(12345000000000L, BlockItem::getParentConsensusTimestamp)
                .returns(true, BlockItem::isSuccessful);
        ;
    }

    @Test
    void parseSuccessParentNotSuccessfulReturnFalse() {
        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.INVALID_TRANSACTION)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        assertThat(blockItem.isSuccessful()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessParentSuccessfulButStatusNotOneOfTheExpectedReturnFalse(ResponseCodeEnum status) {

        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(status)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.BUSY)
                .setParentConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parentBlockItem) // Parent is successful but status is not one of the expected
                .build();

        // Assert: The block item should not be successful because the status is not one of the expected ones
        assertThat(blockItem.isSuccessful()).isFalse();
    }

    @Test
    void parseParentWhenNoParent() {
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        // When, Then: The parent should remain null
        assertThat(blockItem.getParent()).isNull();
    }

    @Test
    void parseParentWhenConsensusTimestampMatchParentIsPrevious() {
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var previousTransaction = Transaction.newBuilder().build();
        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var previousBlockItem = BlockItem.builder()
                .transaction(previousTransaction)
                .transactionResult(previousTransactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When, Then: The parent should match the previous block item
        assertThat(blockItem.getParent()).isSameAs(previousBlockItem);
    }

    @Test
    void parseParentWhenConsensusTimestampDoNotMatchNoParent() {
        // Given: Create a previous block item with a non-matching parent consensus timestamp
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var previousTransaction = Transaction.newBuilder().build();
        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(67890L).build())
                .build();

        var previousBlockItem = BlockItem.builder()
                .transaction(previousTransaction)
                .transactionResult(previousTransactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When, Then: The parent should not match, return the parent as is
        assertThat(blockItem.getParent()).isNotEqualTo(previousBlockItem);
    }

    @Test
    void parseParentConsensusTimestampMatchesOlderSibling() {
        var parentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build()) // Parent timestamp
                .build();

        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(parentTransactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12346L).build())
                .setParentConsensusTimestamp(parentTransactionResult.getConsensusTimestamp())
                .build();

        var previousBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(previousTransactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        var transactionResult = TransactionResult.newBuilder()
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12347L).build())
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        assertThat(blockItem.getParent()).isSameAs(parentBlockItem);
    }

    @Test
    void hasTransactionOutput() {
        // given
        var callContractOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.getDefaultInstance())
                .build();
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of(TransactionCase.CONTRACT_CALL, callContractOutput))
                .stateChanges(List.of())
                .previous(null)
                .build();

        // when, then
        assertThat(blockItem.hasTransactionOutput(TransactionCase.CONTRACT_CALL))
                .isTrue();
        assertThat(blockItem.hasTransactionOutput(TransactionCase.CONTRACT_CREATE))
                .isFalse();
    }

    @Test
    void getStateChangeContext() {
        // given
        var parentConsensusTimestamp = Timestamp.newBuilder().setSeconds(12345L).build();
        var tokenId = TokenID.newBuilder().setTokenNum(123L).build();
        var parent = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(parentConsensusTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of(StateChanges.newBuilder()
                        .addStateChanges(StateChange.newBuilder()
                                .setStateId(StateIdentifier.STATE_ID_TOKENS_VALUE)
                                .setMapUpdate(MapUpdateChange.newBuilder()
                                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                                        .setValue(MapChangeValue.newBuilder()
                                                .setTokenValue(
                                                        Token.newBuilder().setTokenId(tokenId)))))
                        .build()))
                .build();
        var childConsensusTimestamp = Timestamp.newBuilder().setSeconds(12346L).build();
        var child = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(childConsensusTimestamp)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parent)
                .build();
        var failed = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(
                                Timestamp.newBuilder().setSeconds(12347L).build())
                        .setStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                        .build())
                .transactionOutputs(Map.of())
                .stateChanges(List.of())
                .previous(parent)
                .build();

        // when, then
        assertThat(child.getStateChangeContext()).isSameAs(parent.getStateChangeContext());
        assertThat(child.getStateChangeContext().getNewTokenId()).contains(tokenId);
        assertThat(failed.getStateChangeContext()).isSameAs(EMPTY_CONTEXT);
    }

    @Test
    void getTransactionOutput() {
        // given
        var callContractOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.getDefaultInstance())
                .build();
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of(TransactionCase.CONTRACT_CALL, callContractOutput))
                .stateChanges(List.of())
                .previous(null)
                .build();

        // when, then
        assertThat(blockItem.getTransactionOutput(TransactionCase.CONTRACT_CALL))
                .isSameAs(callContractOutput);
        assertThatThrownBy(() -> blockItem.getTransactionOutput(TransactionCase.CONTRACT_CREATE))
                .isInstanceOf(IllegalStateException.class);
    }
}
