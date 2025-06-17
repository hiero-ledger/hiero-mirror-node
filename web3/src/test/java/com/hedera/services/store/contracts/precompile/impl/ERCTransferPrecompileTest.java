// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransfer;
import static com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile.decodeERCTransferFrom;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.contracts.precompile.HTSTestsUtil;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.evm.store.Store;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERCTransferPrecompileTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final Bytes TRANSFER_INPUT = Bytes.fromHexString(
            "0xa9059cbb00000000000000000000000000000000000000000000000000000000000005a50000000000000000000000000000000000000000000000000000000000000002");
    private static final Bytes TRANSFER_LONG_OVERFLOWN = Bytes.fromHexString(
            "0xa9059cbb00000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000010000000000000002");
    private static final Bytes TRANSFER_FROM_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");
    private static final Bytes TRANSFER_FROM_NON_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000003e900000000000000000000000000000000000000000000000000000000000003ea0000000000000000000000000000000000000000000000000000000000000001");
    private static final Bytes TRANSFER_FROM_LONG_OVERFLOWN = Bytes.fromHexString(
            "0x23b872dd00000000000000000000000000000000000000000000000000000000000003ef00000000000000000000000000000000000000000000000000000000000003f00000000000000000000000000000000000000000000000010000000000000002");
    private static final Bytes HAPI_TRANSFER_FROM_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x15dacbea000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");
    private static final Bytes HAPI_TRANSFER_FROM_NFT_INPUT = Bytes.fromHexString(
            "0x9b23d3d9000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000005aa00000000000000000000000000000000000000000000000000000000000005ab0000000000000000000000000000000000000000000000000000000000000005");

    private final Address senderAddress = HTSTestsUtil.senderAddress;

    @Mock
    private Store store;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private TokenID tokenID;

    @Test
    void decodeTransferInput() {
        final var decodedInput = decodeERCTransfer(TRANSFER_INPUT, tokenID, senderAddress, identity());
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertEquals(2, fungibleTransfer.amount());
    }

    @Test
    void decodeTransferShouldThrowOnAmountOverflown() {
        final UnaryOperator<byte[]> identity = identity();
        assertThrows(
                ArithmeticException.class,
                () -> decodeERCTransfer(TRANSFER_LONG_OVERFLOWN, tokenID, senderAddress, identity));
    }

    @Test
    void decodeTransferFromFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = domainBuilder.entityNum(1002L).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput = decodeERCTransferFrom(
                TRANSFER_FROM_FUNGIBLE_INPUT, tokenID, tokenAccessor, notOwner, identity(), x -> true);
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeHapiTransferFromFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = domainBuilder.entityNum(1002L).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput = decodeERCTransferFrom(
                HAPI_TRANSFER_FROM_FUNGIBLE_INPUT, null, tokenAccessor, notOwner, identity(), x -> true);
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        assertEquals(
                domainBuilder.entityNum(1L).toTokenID(), fungibleTransfer.get(0).getDenomination());
        assertEquals(
                fungibleTransfer.get(1).sender(), domainBuilder.entityNum(1450L).toAccountID());
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(
                fungibleTransfer.get(0).receiver(),
                domainBuilder.entityNum(1451).toAccountID());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromFungibleInputStillUsesApprovalIfFromIsOperator() {
        final var owner = domainBuilder.entityNum(1450L).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput = decodeERCTransferFrom(
                TRANSFER_FROM_FUNGIBLE_INPUT, tokenID, tokenAccessor, owner, identity(), x -> true);
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertTrue(fungibleTransfer.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeHapiTransferFromFungibleInputStillUsesApprovalIfFromIsOperator() {
        final var owner = domainBuilder.entityNum(1450L).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.FUNGIBLE_COMMON);
        final var decodedInput = decodeERCTransferFrom(
                HAPI_TRANSFER_FROM_FUNGIBLE_INPUT, null, tokenAccessor, owner, identity(), x -> true);
        final var fungibleTransfer = decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(
                domainBuilder.entityNum(1).toTokenID(), fungibleTransfer.get(0).getDenomination());
        assertEquals(fungibleTransfer.get(1).sender(), owner);
        assertTrue(fungibleTransfer.get(1).isApproval());
        assertEquals(
                fungibleTransfer.get(0).receiver(),
                domainBuilder.entityNum(1451L).toAccountID());
        assertEquals(5, fungibleTransfer.get(0).amount());
    }

    @Test
    void decodeTransferFromNonFungibleInputUsingApprovalIfNotOwner() {
        final var notOwner = domainBuilder.entityNum(102).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput = decodeERCTransferFrom(
                TRANSFER_FROM_NON_FUNGIBLE_INPUT, tokenID, tokenAccessor, notOwner, identity(), x -> true);
        final var nftTransfer = decodedInput
                .tokenTransferWrappers()
                .get(0)
                .nftExchanges()
                .get(0)
                .asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertTrue(nftTransfer.getIsApproval());
    }

    @Test
    void decodeHapiTransferFromNFTInputUsingApprovalIfNotOwner() {
        final var notOwner = domainBuilder.entityNum(1002).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput = decodeERCTransferFrom(
                HAPI_TRANSFER_FROM_NFT_INPUT, null, tokenAccessor, notOwner, identity(), x -> true);
        final var nftTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertEquals(domainBuilder.entityNum(1).toTokenID(), nftTransfer.getTokenType());
        final var nftTransferAsGrpc = nftTransfer.asGrpc();
        assertEquals(
                nftTransferAsGrpc.getSenderAccountID(),
                domainBuilder.entityNum(1450L).toAccountID());
        assertTrue(nftTransferAsGrpc.getIsApproval());
        assertEquals(
                nftTransferAsGrpc.getReceiverAccountID(),
                domainBuilder.entityNum(1451).toAccountID());
        assertEquals(5, nftTransferAsGrpc.getSerialNumber());
    }

    @Test
    void decodeTransferFromNonFungibleInputIfOwner() {
        final var owner = domainBuilder.entityNum(1001L).toAccountID();
        given(tokenAccessor.typeOf(any())).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        final var decodedInput = decodeERCTransferFrom(
                TRANSFER_FROM_NON_FUNGIBLE_INPUT, tokenID, tokenAccessor, owner, identity(), x -> true);
        final var nftTransfer = decodedInput
                .tokenTransferWrappers()
                .get(0)
                .nftExchanges()
                .get(0)
                .asGrpc();

        assertTrue(nftTransfer.getSenderAccountID().getAccountNum() > 0);
        assertTrue(nftTransfer.getReceiverAccountID().getAccountNum() > 0);
        assertEquals(1, nftTransfer.getSerialNumber());
        assertFalse(nftTransfer.getIsApproval());
    }

    @Test
    void decodeHapiTransferFromNFTIInputIfOwner() {
        final var owner = domainBuilder.entityNum(1450L).toAccountID();

        final var decodedInput =
                decodeERCTransferFrom(HAPI_TRANSFER_FROM_NFT_INPUT, null, tokenAccessor, owner, identity(), x -> true);
        final var nftTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertEquals(domainBuilder.entityNum(1).toTokenID(), nftTransfer.getTokenType());
        final var nftTransferAsGrpc = nftTransfer.asGrpc();
        assertEquals(
                nftTransferAsGrpc.getSenderAccountID(),
                domainBuilder.entityNum(1450L).toAccountID());
        assertFalse(nftTransferAsGrpc.getIsApproval());
        assertEquals(
                nftTransferAsGrpc.getReceiverAccountID(),
                domainBuilder.entityNum(1451L).toAccountID());
        assertEquals(5, nftTransferAsGrpc.getSerialNumber());
    }

    @Test
    void decodeTransferFromShouldThrowOnAmountOverflown() {
        final var owner = domainBuilder.entityNum(1450L).toAccountID();
        final UnaryOperator<byte[]> identity = identity();
        assertThrows(
                ArithmeticException.class,
                () -> decodeERCTransferFrom(
                        TRANSFER_FROM_LONG_OVERFLOWN, tokenID, tokenAccessor, owner, identity, x -> true));
    }
}
