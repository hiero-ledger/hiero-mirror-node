// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
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
            mode = INCLUDE,
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
                .returns(0L, BlockItem::consensusTimestamp)
                .returns(null, BlockItem::parentConsensusTimestamp)
                .returns(true, BlockItem::successful);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            mode = INCLUDE,
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
                .returns(12346000000000L, BlockItem::consensusTimestamp)
                .returns(12345000000000L, BlockItem::parentConsensusTimestamp)
                .returns(true, BlockItem::successful);
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

        assertThat(blockItem.successful()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            mode = INCLUDE,
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
        assertThat(blockItem.successful()).isFalse();
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

        // When: The parent is not set
        var parent = blockItem.parent();

        // Then: The parent should remain null
        assertThat(parent).isNull();
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

        // When: The parent matches the consensus timestamp of the previous block item
        var parent = blockItem.parent();

        // Then: The parent should match the previous block item
        assertThat(parent).isSameAs(previousBlockItem);
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

        // When: The parent consensus timestamp does not match the previous block item
        var parent = blockItem.parent();

        // Then: The parent should not match, return the parent as is
        assertThat(parent).isNotEqualTo(previousBlockItem);
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

        var parent = blockItem.parent();

        assertThat(parent).isSameAs(parentBlockItem);
    }
}
