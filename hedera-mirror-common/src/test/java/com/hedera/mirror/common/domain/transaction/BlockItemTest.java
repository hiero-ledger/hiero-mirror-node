// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        assertTrue(blockItem.successful());
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var transactionResult = TransactionResult.newBuilder()
                .setStatus(status)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        assertTrue(blockItem.successful());
    }

    @Test
    void parseSuccessParentNotSuccessfulReturnFalse() {

        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.INVALID_TRANSACTION)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutput(List.of())
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(parentBlockItem)
                .build();

        assertFalse(blockItem.successful());
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
                .transactionOutput(List.of())
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(parentBlockItem) // Parent is successful but status is not one of the expected
                .build();

        // Assert: The block item should not be successful because the status is not one of the expected ones
        assertFalse(blockItem.successful());
    }

    @Test
    void parseParentWhenNoParent() {
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        // When: The parent is not set
        var parent = blockItem.parent();

        // Then: The parent should remain null
        assertNull(parent, "Parent should be null when no parent is provided");
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When: The parent matches the consensus timestamp of the previous block item
        var parent = blockItem.parent();

        // Then: The parent should match the previous block item
        assertEquals(
                previousBlockItem, parent, "Parent should match the previous block item based on consensus timestamp");
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(null)
                .build();

        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(transactionResult)
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        // When: The parent consensus timestamp does not match the previous block item
        var parent = blockItem.parent();

        // Then: The parent should not match, return the parent as is
        assertNotEquals(
                previousBlockItem,
                parent,
                "Parent should not match the previous block item based on consensus timestamp");
    }

    @Test
    void parseParentConsensusTimestampMatchesOlderSibling() {
        var parentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build()) // Parent timestamp
                .build();

        var parentBlockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder().build())
                .transactionResult(parentTransactionResult)
                .transactionOutput(List.of())
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
                .transactionOutput(List.of())
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
                .transactionOutput(List.of())
                .stateChanges(List.of())
                .previous(previousBlockItem)
                .build();

        var parent = blockItem.parent();

        assertEquals(
                parentBlockItem,
                parent,
                "Parent should match the previous block item's parent based on consensus timestamp");
    }
}
