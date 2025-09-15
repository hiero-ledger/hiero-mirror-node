// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeContext.EMPTY_CONTEXT;

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
import com.hedera.hapi.block.stream.trace.protoc.AutoAssociateTraceData;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.SubmitMessageTraceData;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

final class BlockTransactionTest {

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessWhenNoParentPresentAndCorrectStatusReturnTrue(ResponseCodeEnum status) {
        var transactionResult = TransactionResult.newBuilder().setStatus(status).build();
        var blockTransaction =
                defaultBuilder().transactionResult(transactionResult).build();

        assertThat(blockTransaction)
                .returns(0L, BlockTransaction::getConsensusTimestamp)
                .returns(null, BlockTransaction::getParentConsensusTimestamp)
                .returns(true, BlockTransaction::isSuccessful);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessWithSuccessfulParentAndCorrectStatusReturnTrue(ResponseCodeEnum status) {
        var parentBlockTransaction = defaultBuilder()
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(status)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .build();
        var transactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12346L))
                .setStatus(status)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();
        var blockTransaction = defaultBuilder()
                .previous(parentBlockTransaction)
                .transactionResult(transactionResult)
                .build();

        assertThat(blockTransaction)
                .returns(12346000000000L, BlockTransaction::getConsensusTimestamp)
                .returns(12345000000000L, BlockTransaction::getParentConsensusTimestamp)
                .returns(true, BlockTransaction::isSuccessful);
    }

    @Test
    void parseSuccessParentNotSuccessfulReturnFalse() {
        var parentBlockTransaction = defaultBuilder()
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.INVALID_TRANSACTION)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .build();
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();
        var blockTransaction = defaultBuilder()
                .previous(parentBlockTransaction)
                .transactionResult(transactionResult)
                .build();

        assertThat(blockTransaction.isSuccessful()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"FEE_SCHEDULE_FILE_PART_UPLOADED", "SUCCESS", "SUCCESS_BUT_MISSING_EXPECTED_OPERATION"})
    void parseSuccessParentSuccessfulButStatusNotOneOfTheExpectedReturnFalse(ResponseCodeEnum status) {
        var parentBlockTransaction = defaultBuilder()
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(status)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .build();
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.BUSY)
                .setParentConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                .build();
        var blockTransaction = defaultBuilder()
                .previous(parentBlockTransaction) // Parent is successful but status is not one of the expected
                .transactionResult(transactionResult)
                .build();

        // Assert: The block item should not be successful because the status is not one of the expected ones
        assertThat(blockTransaction.isSuccessful()).isFalse();
    }

    @Test
    void parseParentWhenNoParent() {
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .build();
        var blockTransaction =
                defaultBuilder().transactionResult(transactionResult).build();

        // When, Then: The parent should remain null
        assertThat(blockTransaction.getParent()).isNull();
    }

    @Test
    void parseParentWhenConsensusTimestampMatchParentIsPrevious() {
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();
        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build())
                .build();
        var previousBlockTransaction =
                defaultBuilder().transactionResult(previousTransactionResult).build();
        var blockTransaction = defaultBuilder()
                .previous(previousBlockTransaction)
                .transactionResult(transactionResult)
                .build();

        // When, Then: The parent should match the previous block item
        assertThat(blockTransaction.getParent()).isSameAs(previousBlockTransaction);
    }

    @Test
    void parseParentWhenConsensusTimestampDoNotMatchNoParent() {
        // Given: Create a previous block item with a non-matching parent consensus timestamp
        var transactionResult = TransactionResult.newBuilder()
                .setStatus(ResponseCodeEnum.SUCCESS)
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .build();
        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(67890L).build())
                .build();
        var previousBlockTransaction =
                defaultBuilder().transactionResult(previousTransactionResult).build();
        var blockTransaction = defaultBuilder()
                .previous(previousBlockTransaction)
                .transactionResult(transactionResult)
                .build();

        // When, Then: The parent should not match, return the parent as is
        assertThat(blockTransaction.getParent()).isNotEqualTo(previousBlockTransaction);
    }

    @Test
    void parseParentConsensusTimestampMatchesOlderSibling() {
        var parentTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L).build()) // Parent timestamp
                .build();
        var parentBlockTransaction =
                defaultBuilder().transactionResult(parentTransactionResult).build();
        var previousTransactionResult = TransactionResult.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12346L).build())
                .setParentConsensusTimestamp(parentTransactionResult.getConsensusTimestamp())
                .build();
        var previousBlockTransaction = defaultBuilder()
                .previous(parentBlockTransaction)
                .transactionResult(previousTransactionResult)
                .build();
        var transactionResult = TransactionResult.newBuilder()
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(12345L).build())
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12347L).build())
                .build();
        var blockTransaction = defaultBuilder()
                .previous(previousBlockTransaction)
                .transactionResult(transactionResult)
                .build();

        assertThat(blockTransaction.getParent()).isSameAs(parentBlockTransaction);
    }

    @Test
    void getStateChangeContext() {
        // given
        var parentConsensusTimestamp = Timestamp.newBuilder().setSeconds(12345L).build();
        var tokenId = TokenID.newBuilder().setTokenNum(123L).build();
        var parent = defaultBuilder()
                .stateChanges(List.of(StateChanges.newBuilder()
                        .addStateChanges(StateChange.newBuilder()
                                .setStateId(StateIdentifier.STATE_ID_TOKENS_VALUE)
                                .setMapUpdate(MapUpdateChange.newBuilder()
                                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                                        .setValue(MapChangeValue.newBuilder()
                                                .setTokenValue(
                                                        Token.newBuilder().setTokenId(tokenId)))))
                        .build()))
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(parentConsensusTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build())
                .build();
        var childConsensusTimestamp = Timestamp.newBuilder().setSeconds(12346L).build();
        var child = defaultBuilder()
                .previous(parent)
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(childConsensusTimestamp)
                        .setParentConsensusTimestamp(parentConsensusTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build())
                .build();
        var failed = defaultBuilder()
                .previous(parent)
                .transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(
                                Timestamp.newBuilder().setSeconds(12347L).build())
                        .setStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)
                        .build())
                .build();

        // when, then
        assertThat(child.getStateChangeContext())
                .isSameAs(parent.getStateChangeContext())
                .extracting(StateChangeContext::getNewTokenId, InstanceOfAssertFactories.optional(TokenID.class))
                .contains(tokenId);
        assertThat(failed.getStateChangeContext()).isSameAs(EMPTY_CONTEXT);
    }

    @MethodSource("provideTransactionHashTestArguments")
    @ParameterizedTest(name = "{0}")
    @SneakyThrows
    void getTransactionHash(String name, String signedTransactionHex, String expectedTransactionHash) {
        byte[] signedTransactionBytes = Hex.decodeHex(signedTransactionHex);
        var signedTransaction = SignedTransaction.parseFrom(signedTransactionBytes);
        var blockTransaction = defaultBuilder()
                .signedTransaction(signedTransaction)
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        assertThat(blockTransaction.getTransactionHash())
                .isEqualTo(DomainUtils.fromBytes(Hex.decodeHex(expectedTransactionHash)));
    }

    @Test
    void getTransactionOutput() {
        // given
        var callContractOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.getDefaultInstance())
                .build();
        var blockTransaction = defaultBuilder()
                .transactionResult(TransactionResult.newBuilder()
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(12345L))
                        .build())
                .transactionOutputs(Map.of(TransactionCase.CONTRACT_CALL, callContractOutput))
                .build();

        // when, then
        assertThat(blockTransaction.getTransactionOutput(TransactionCase.CONTRACT_CALL))
                .contains(callContractOutput);
        assertThat(blockTransaction.getTransactionOutput(TransactionCase.CONTRACT_CREATE))
                .isEmpty();
    }

    @Test
    void allTraceData() {
        // given
        var blockTransaction = defaultBuilder()
                .traceData(List.of(
                        TraceData.newBuilder()
                                .setAutoAssociateTraceData(AutoAssociateTraceData.getDefaultInstance())
                                .build(),
                        TraceData.newBuilder()
                                .setEvmTraceData(EvmTraceData.getDefaultInstance())
                                .build(),
                        TraceData.newBuilder()
                                .setSubmitMessageTraceData(SubmitMessageTraceData.getDefaultInstance())
                                .build()))
                .build();

        // when, then
        assertThat(blockTransaction)
                .satisfies(
                        b -> assertThat(b.getAutoAssociateTraceData()).isNotNull(),
                        b -> assertThat(b.getEvmTraceData()).isNotNull(),
                        b -> assertThat(b.getSubmitMessageTraceData()).isNotNull());
    }

    @Test
    void noTraceData() {
        // given
        var blockTransaction = defaultBuilder().build();

        // when, then
        assertThat(blockTransaction)
                .returns(null, BlockTransaction::getAutoAssociateTraceData)
                .returns(null, BlockTransaction::getEvmTraceData)
                .returns(null, BlockTransaction::getSubmitMessageTraceData);
    }

    private BlockTransaction.BlockTransactionBuilder defaultBuilder() {
        var signedTransaction = SignedTransaction.getDefaultInstance();
        var transactionBody = TransactionBody.getDefaultInstance();
        return BlockTransaction.builder()
                .transactionBody(transactionBody)
                .signedTransaction(signedTransaction)
                .signedTransactionBytes(signedTransaction.toByteArray())
                .stateChanges(Collections.emptyList())
                .traceData(Collections.emptyList())
                .transactionOutputs(Collections.emptyMap())
                .transactionResult(TransactionResult.getDefaultInstance());
    }

    @SneakyThrows
    private static Stream<Arguments> provideTransactionHashTestArguments() {
        var signedTransactionHex =
                """
                0a440a0f0a0908b9c1e4c00610aa07120218021202180318988c0522020878320766756e642d3536721c0a1a0a0b0a02183810\
                80d0dbc3f4020a0b0a02180210ffcfdbc3f40212470a450a010a1a40bc3c1881027d632801f12558b9d432803ba5f3854d42b2\
                01ecc51581126d8efd29db0ebed54cae5130eff35013d1f219ce3bd33459488e164427b5cbb21f6707\
                """;
        var signedTransactionHash =
                "364747682d512bf8cdd4323a277b8bdb81734b96fcdacfccef3e213de7f29db07e8465c8d9ed05d5276108b5f2f2d1a7";
        // fields serialized in the reverse order, sigMap first then bodyBytes
        var invertedSignedTransactionHex =
                """
                12470a450a010a1a40bc3c1881027d632801f12558b9d432803ba5f3854d42b201ecc51581126d8efd29db0ebed54cae5130ef\
                f35013d1f219ce3bd33459488e164427b5cbb21f67070a440a0f0a0908b9c1e4c00610aa07120218021202180318988c052202\
                0878320766756e642d3536721c0a1a0a0b0a0218381080d0dbc3f4020a0b0a02180210ffcfdbc3f402\
                """;
        var invertedSignedTransactionHash =
                "3a61a1a809da09aa49c44dd290fcb687243b593a34cd62c0be15b65a6424e8d49e6985afa83dc4cb0f56f5a383657f6b";
        var signedTransaction = SignedTransaction.parseFrom(Hex.decodeHex(signedTransactionHex));
        var legacySignedTransactionHex = Hex.encodeHexString(signedTransaction.toBuilder()
                .setUseSerializedTxMessageHashAlgorithm(true)
                .build()
                .toByteArray());
        var legacySignedTransactionHash =
                "89e787e62ac5cbe42245bb427cb91e458ad001f3baeed2e464a4e8ca8c73a7e7b13825b72cfbbaf1127dd6a988d179c7";
        return Stream.of(
                Arguments.of("signed transaction", signedTransactionHex, signedTransactionHash),
                Arguments.of(
                        "inverted signed transaction", invertedSignedTransactionHex, invertedSignedTransactionHash),
                Arguments.of("legacy signed transaction", legacySignedTransactionHex, legacySignedTransactionHash));
    }
}
