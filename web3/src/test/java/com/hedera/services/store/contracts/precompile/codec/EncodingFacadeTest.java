// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.convertBesuAddressToHeadlongAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.*;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.Test;

class EncodingFacadeTest {
    public static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_3_TOKENS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "00000000000000000000000000000000000000000000000000000000000000000"
                    + "00000000000000000000000000000000000000000000000000000000000006"
                    + "00000000000000000000000000000000000000000000000000000000000000003"
                    + "0000000000000000000000000000000000000000000000000000000000000001"
                    + "0000000000000000000000000000000000000000000000000000000000000002"
                    + "0000000000000000000000000000000000000000000000000000000000000003");
    private static final Address senderAddress = Address.ALTBN128_PAIRING;
    private static final Address recipientAddress = Address.ALTBN128_ADD;
    private static final Bytes RETURN_FUNGIBLE_MINT_FOR_10_TOKENS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000"
                    + "00000a0000000000000000000000000000000000000000000000000000"
                    + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000"
                    + "0000020000000000000000000000000000000000000000000000000000"
                    + "00000000006000000000000000000000000000000000000000000000000"
                    + "00000000000000002000000000000000000000000000000000000000000"
                    + "00000000000000000000010000000000000000000000000000000000000000000000000000000000000002");
    private static final Bytes RETURN_BURN_FOR_49_TOKENS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000000031");
    private static final Bytes MINT_FAILURE_FROM_INVALID_TOKEN_ID =
            Bytes.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000a7"
                    + "0000000000000000000000000000000000000000000000000000000000"
                    + "0000000000000000000000000000000000000000000000000000000000"
                    + "0000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes BURN_FAILURE_FROM_TREASURY_NOT_OWNER =
            Bytes.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000fc"
                    + "0000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes RETURN_SUCCESS_3 =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000000003");
    private static final Bytes RETURN_TRUE =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes RETURN_SUCCESS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016");
    private static final Bytes RETURN_SUCCESS_TRUE =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes TRANSFER_EVENT =
            Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    private static final Bytes RETURN_CREATE_SUCCESS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "0000000000000000000000000000000000000000000000000000000000000008");
    private static final Bytes CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME =
            Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000002d"
                    + "0000000000000000000000000000000000000000000000000000000000000000");
    final Address logger = Address.fromHexString("0x167");
    private final EncodingFacade subject = new EncodingFacade();

    @Test
    void canEncodeEip1014Address() {
        final var literalEip1014 = "0x8ff8eb31713b9ff374d893d21f3b9eb732a307a5";
        final var besuAddress = Address.fromHexString(literalEip1014);
        final var headlongAddress = convertBesuAddressToHeadlongAddress(besuAddress);
        assertEquals(literalEip1014, (String.valueOf(headlongAddress)).toLowerCase());
    }

    @Test
    void decodeReturnResultForFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(10, null);
        assertEquals(RETURN_FUNGIBLE_MINT_FOR_10_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForNonFungibleMint() {
        final var decodedResult = subject.encodeMintSuccess(2, new long[] {1, 2});
        assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_2_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForNonFungibleMint3() {
        final var decodedResult = subject.encodeMintSuccess(0, new long[] {1, 2, 3});
        assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_3_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForBurn() {
        final var decodedResult = subject.encodeBurnSuccess(49);
        assertEquals(RETURN_BURN_FOR_49_TOKENS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateSuccess() {
        final var decodedResult = subject.encodeCreateSuccess(senderAddress);
        assertEquals(RETURN_CREATE_SUCCESS, decodedResult);
    }

    @Test
    void decodeReturnResultForCreateFailure() {
        final var decodedResult = subject.encodeCreateFailure(INVALID_EXPIRATION_TIME);
        assertEquals(CREATE_FAILURE_FROM_INVALID_EXPIRATION_TIME, decodedResult);
    }

    @Test
    void decodeReturnResultForTransfer() {
        final var decodedResult = subject.encodeEcFungibleTransfer(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveERC() {
        final var decodedResult = subject.encodeApprove(true);
        assertEquals(RETURN_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveHAPI() {
        final var decodedResult = subject.encodeApprove(SUCCESS.getNumber(), true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForApproveNFTHAPI() {
        final var decodedResult = subject.encodeApproveNFT(SUCCESS.getNumber());
        assertEquals(RETURN_SUCCESS, decodedResult);
    }

    @Test
    void decodeReturnResultForIsApprovedForAllHAPI() {
        final var decodedResult = subject.encodeIsApprovedForAll(SUCCESS.getNumber(), true);
        assertEquals(RETURN_SUCCESS_TRUE, decodedResult);
    }

    @Test
    void decodeReturnResultForAllowanceHAPI() {
        final var decodedResult = subject.encodeAllowance(SUCCESS.getNumber(), 3);
        assertEquals(RETURN_SUCCESS_3, decodedResult);
    }

    @Test
    void decodeReturnResultForGetApprovedHAPI() {
        final var decodedResult = subject.encodeGetApproved(SUCCESS.getNumber(), senderAddress);
        assertEquals(RETURN_CREATE_SUCCESS, decodedResult);
    }

    @Test
    void logBuilderWithTopics() {
        final var log = EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(TRANSFER_EVENT)
                .forIndexedArgument(senderAddress)
                .forIndexedArgument(recipientAddress)
                .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000006")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithTopicsWithDifferentTypes() {
        final var log = EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(TRANSFER_EVENT)
                .forIndexedArgument(senderAddress)
                .forIndexedArgument(20L)
                .forIndexedArgument(BigInteger.valueOf(20))
                .forIndexedArgument(Boolean.TRUE)
                .forIndexedArgument(false)
                .build();

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000008")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000014")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001")));
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")));

        assertEquals(new Log(logger, Bytes.EMPTY, topics), log);
    }

    @Test
    void logBuilderWithData() {
        final var tupleType = TupleType.parse("(address,uint256,uint256,bool,bool)");
        final var log = EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(TRANSFER_EVENT)
                .forDataItem(senderAddress)
                .forDataItem(9L)
                .forDataItem(BigInteger.valueOf(9))
                .forDataItem(Boolean.TRUE)
                .forDataItem(false)
                .build();

        final var dataItems = new ArrayList<>();
        dataItems.add(convertBesuAddressToHeadlongAddress(senderAddress));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(BigInteger.valueOf(9));
        dataItems.add(true);
        dataItems.add(false);
        final var tuple = Tuple.from(dataItems.toArray());

        final List<LogTopic> topics = new ArrayList<>();
        topics.add(LogTopic.wrap(
                Bytes.fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")));

        assertEquals(new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics), log);
    }

    @Test
    void createsExpectedMintFailureResult() {
        assertEquals(MINT_FAILURE_FROM_INVALID_TOKEN_ID, subject.encodeMintFailure(INVALID_TOKEN_ID));
    }

    @Test
    void createsExpectedBurnFailureResult() {
        assertEquals(BURN_FAILURE_FROM_TREASURY_NOT_OWNER, subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
    }

    @Test
    void createsSuccessResult() {
        assertNotEquals(SUCCESS_RESULT, subject.encodeBurnFailure(TREASURY_MUST_OWN_BURNED_NFT));
    }
}
