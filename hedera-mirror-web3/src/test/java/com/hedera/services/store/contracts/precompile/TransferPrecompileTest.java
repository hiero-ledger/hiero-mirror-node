// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_EMPTY_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER2;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_NFT_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER2;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_NFT_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER_ALIASED;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFTS_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFT_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_TWO_HBAR_ONLY_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiverAliased;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.addNftExchanges;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.addSignedAdjustments;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransfer;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransferV2;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeHbarTransfers;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTokenTransfer;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFT;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFTs;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferToken;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferTokens;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.common.PrecompileContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.TransferParams;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPrecompileTest {

    private static final long TEST_CRYPTO_TRANSFER_MIN_FEE = 1_000_000;

    private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
            "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");

    private static final Bytes NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT = Bytes.fromHexString(
            "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004c0000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004bdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffce0000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");

    private static final Bytes NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT = Bytes.fromHexString(
            "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043afffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000");

    private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000010000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");

    private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT = Bytes.fromHexString(
            "0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");

    private static final Bytes TRANSFER_NFT_INPUT = Bytes.fromHexString(
            "0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");

    private static final Bytes TRANSFER_NFTS_INPUT = Bytes.fromHexString(
            "0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000010000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private static final Bytes CRYPTO_TRANSFER_HBAR_ONLY_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_NFT_INPUT = Bytes.fromHexString(
            "0x0e71804f000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000005fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes CRYPTO_TRANSFER_HBAR_NFT_INPUT = Bytes.fromHexString(
            "0x0e71804f00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000140000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000700000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000090000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000000");

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;

    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;

    @Mock
    private MessageFrame frame;

    private MockedStatic<TransferPrecompile> staticTransferPrecompile;

    private TransferPrecompile transferPrecompile;

    @Mock
    private PrecompileContext precompileContext;

    private BodyParams transferParams;

    @Mock
    private PrecompilePricingUtils pricingUtils;

    @Mock
    private TransactionBody.Builder transactionBodyBuilder;

    @Mock
    private TransferLogic transferLogic;

    @Mock
    private ContextOptionValidator contextOptionValidator;

    @Mock
    private Store store;

    @Mock
    private AutoCreationLogic autoCreationLogic;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private CryptoTransferTransactionBody cryptoTransferTransactionBody;

    @Mock
    private EntityAddressSequencer entityAddressSequencer;

    @Mock
    private Predicate<Address> systemAccountDetector;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    private HTSPrecompiledContract subject;

    private final TransactionBody.Builder transactionBody =
            TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder());

    @InjectMocks
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    public static final Address senderAddress = Address.ALTBN128_PAIRING;

    @BeforeEach
    void setup() {
        syntheticTxnFactory = new SyntheticTxnFactory();

        transferPrecompile = new TransferPrecompile(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer,
                systemAccountDetector);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(transferPrecompile));
        subject = new HTSPrecompiledContract(
                infrastructureFactory, mirrorNodeEvmProperties, precompileMapper, store, tokenAccessor, pricingUtils);

        staticTransferPrecompile = mockStatic(TransferPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        staticTransferPrecompile.close();
    }

    @Test
    void transferFailsFastGivenWrongSyntheticValidity() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(frame.getRemainingGas()).willReturn(10000L);

        staticTransferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        final var txnBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(TransferList.newBuilder()
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(1L)
                                        .build())
                                .addAccountAmounts(AccountAmount.newBuilder()
                                        .setAccountID(sender)
                                        .setAmount(1L)
                                        .build())
                                .build()));

        given(worldUpdater.getStore()).willReturn(store);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(txnBody);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(UInt256.valueOf(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS_VALUE), result);
    }

    @Test
    void transferTokenHappyPathWorks() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER2);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);

        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void transferNftsHappyPathWorks() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeTransferNFTs(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void cryptoTransferHappyPathWorks() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void hbarOnlyTransferHappyPathWorks() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void hbarFungibleTransferHappyPathWorks() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER2);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);

        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void nftTransferToLazyCreateHappyPathWorks() {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(worldUpdater.getStore()).willReturn(store);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void hbarTransferToLazyCreateHappyPathWorks() {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        when(precompileContext.getSenderAddress()).thenReturn(senderAddress);

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER_ALIASED);

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        given(worldUpdater.getStore()).willReturn(store);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void nftTransferToLazyCreateFailsWhenAutoCreationLogicReturnsNonOk() {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        givenMinimalFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(pretendArguments), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER_ALIAS_RECEIVER);

        final var txnBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .addTokenTransfers(TokenTransferList.newBuilder()
                                .setToken(HTSTestsUtil.token)
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setSenderAccountID(sender)
                                        .setReceiverAccountID(receiverAliased)
                                        .setSerialNumber(1L)
                                        .build())
                                .addNftTransfers(NftTransfer.newBuilder()
                                        .setSenderAccountID(sender)
                                        .setReceiverAccountID(receiverAliased)
                                        .setSerialNumber(2L)
                                        .build())
                                .build()));

        given(frame.getRemainingGas()).willReturn(10000L);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(transferPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(txnBody);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(worldUpdater.getStore()).willReturn(store);
        final var lazyCreationFee = 500L;
        when(autoCreationLogic.create(any(), any(), any(), any(), any()))
                .thenReturn(Pair.of(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, lazyCreationFee));

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(UInt256.valueOf(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED.getNumber()), result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferSingleToken() {
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));

        staticTransferPrecompile
                .when(() -> decodeTransferToken(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);
        when(pricingUtils.computeGasRequirement(anyLong(), any(), any(), any())).thenReturn(EXPECTED_GAS_PRICE);

        transferPrecompile.body(input, a -> a, null);

        final long result = transferPrecompile.getGasRequirement(TEST_CONSENSUS_TIME, transactionBodyBuilder, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void minimumFeeInTinybarsHbarOnlyCryptoTransfer() {
        transferParams = new TransferParams(ABI_ID_CRYPTO_TRANSFER_V2, store::exists);

        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER);
        when(pricingUtils.getMinimumPriceInTinybars(any(), any())).thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        final var txnBody = transferPrecompile.body(input, a -> a, transferParams);
        final var minimumFeeInTinybars = transferPrecompile.getMinimumFeeInTinybars(timestamp, txnBody.build(), sender);

        // then
        assertEquals(TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);
    }

    @Test
    void minimumFeeInTinybarsTwoHbarCryptoTransfer() {
        transferParams = new TransferParams(ABI_ID_CRYPTO_TRANSFER_V2, this::existsMock);

        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TWO_HBAR_ONLY_WRAPPER);
        when(pricingUtils.getMinimumPriceInTinybars(any(), any())).thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        final var txnBody = transferPrecompile.body(input, a -> a, transferParams);
        final var minimumFeeInTinybars = transferPrecompile.getMinimumFeeInTinybars(timestamp, txnBody.build(), sender);

        // then
        // expect 2 times the fee as there are two transfers
        assertEquals(2 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);
    }

    @Test
    void minimumFeeInTinybarsHbarFungibleCryptoTransfer() {
        transferParams = new TransferParams(ABI_ID_CRYPTO_TRANSFER_V2, store::exists);

        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));

        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER);
        when(pricingUtils.getMinimumPriceInTinybars(any(), any())).thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        final var txnBody = transferPrecompile.body(input, a -> a, transferParams);
        final var minimumFeeInTinybars = transferPrecompile.getMinimumFeeInTinybars(timestamp, txnBody.build(), sender);

        // then
        // 1 for hbars and 1 for fungible tokens
        assertEquals(2 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);
    }

    @Test
    void minimumFeeInTinybarsHbarNftCryptoTransfer() {
        transferParams = new TransferParams(ABI_ID_CRYPTO_TRANSFER_V2, store::exists);

        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_NFT_WRAPPER);
        when(pricingUtils.getMinimumPriceInTinybars(any(), any())).thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        final var txnBody = transferPrecompile.body(input, a -> a, transferParams);
        final var minimumFeeInTinybars = transferPrecompile.getMinimumFeeInTinybars(timestamp, txnBody.build(), sender);

        // then
        // 2 for nfts transfers and 1 for hbars
        assertEquals(3 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);
    }

    @Test
    void minimumFeeInTinybarsHbarFungibleNftCryptoTransfer() {
        transferParams = new TransferParams(ABI_ID_CRYPTO_TRANSFER_V2, store::exists);

        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(eq(input), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_NFT_WRAPPER);
        when(pricingUtils.getMinimumPriceInTinybars(any(), any())).thenReturn(TEST_CRYPTO_TRANSFER_MIN_FEE);

        final var txnBody = transferPrecompile.body(input, a -> a, transferParams);
        final var minimumFeeInTinybars = transferPrecompile.getMinimumFeeInTinybars(timestamp, txnBody.build(), sender);

        // then
        // 1 for fungible + 2 for nfts transfers + 1 for hbars
        assertEquals(4 * TEST_CRYPTO_TRANSFER_MIN_FEE, minimumFeeInTinybars);
    }

    @Test
    void decodeCryptoTransferPositiveFungibleAmountAndNftTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransfer(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeCryptoTransfer(
                POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT, identity(), this::existsMock);
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nftExchanges = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(fungibleTransfers);
        assertNotNull(nftExchanges);
        assertEquals(1, fungibleTransfers.size());
        assertEquals(1, nftExchanges.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
        assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
    }

    @Test
    void decodeCryptoTransferNegativeFungibleAmount() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransfer(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertNotNull(fungibleTransfers);
        assertEquals(1, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).sender().getAccountNum() > 0);
        assertEquals(50, fungibleTransfers.get(0).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenInput() {
        staticTransferPrecompile
                .when(() -> decodeTransferToken(TRANSFER_TOKEN_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferToken(TRANSFER_TOKEN_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.getDenomination().getTokenNum() > 0);
        assertEquals(20, fungibleTransfer.amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenWithNegativeInput() {
        staticTransferPrecompile
                .when(() -> decodeTransferToken(NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT, identity()))
                .thenCallRealMethod();
        UnaryOperator<byte[]> identity = identity();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> decodeTransferToken(NEGATIVE_AMOUNT_TRANSFER_TOKEN_INPUT, identity));

        assertEquals("Amount must be non-negative", exception.getMessage());
    }

    @Test
    void decodeTransferTokensPositiveAmounts() {
        staticTransferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveAmountsWithAliases() {
        staticTransferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveNegativeAmount() {
        staticTransferPrecompile
                .when(() -> decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> addSignedAdjustments(any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).receiver());
        assertTrue(fungibleTransfers.get(1).sender().getAccountNum() > 0);
        assertEquals(20, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTInput() {
        staticTransferPrecompile
                .when(() -> decodeTransferNFT(TRANSFER_NFT_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFT(TRANSFER_NFT_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertTrue(nonFungibleTransfer.asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.getTokenType().getTokenNum() > 0);
        assertEquals(101, nonFungibleTransfer.asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInput() {
        staticTransferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInputWithAlias() {
        staticTransferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> addNftExchanges(any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeCryptoTransferHBarOnlyTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_ONLY_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var tokenTransferWrappers = decodedInput.tokenTransferWrappers();

        assertNotNull(hbarTransfers);
        assertNotNull(tokenTransferWrappers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, tokenTransferWrappers.size());

        assertHbarTransfers(hbarTransfers);
    }

    @Test
    void decodeCryptoTransferFungibleTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_FUNGIBLE_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(0, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferNftTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_NFT_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(0, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    @Test
    void decodeCryptoTransferHbarFungibleTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_FUNGIBLE_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers);
        assertEquals(2, hbarTransfers.size());
        assertEquals(2, fungibleTransfers.size());
        assertEquals(0, nonFungibleTransfers.size());

        assertHbarTransfers(hbarTransfers);
        assertFungibleTransfers(fungibleTransfers);
    }

    @Test
    void decodeCryptoTransferHbarNFTTransfer() {
        staticTransferPrecompile
                .when(() -> decodeCryptoTransferV2(any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeTokenTransfer(any(), any(), any(), any()))
                .thenCallRealMethod();
        staticTransferPrecompile
                .when(() -> decodeHbarTransfers(any(), any(), any()))
                .thenCallRealMethod();
        final var decodedInput = decodeCryptoTransferV2(CRYPTO_TRANSFER_HBAR_NFT_INPUT, identity(), this::existsMock);
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nonFungibleTransfers1 =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges();
        final var nonFungibleTransfers2 =
                decodedInput.tokenTransferWrappers().get(1).nftExchanges();

        assertNotNull(hbarTransfers);
        assertNotNull(fungibleTransfers);
        assertNotNull(nonFungibleTransfers1);
        assertNotNull(nonFungibleTransfers2);
        assertEquals(2, hbarTransfers.size());
        assertEquals(0, fungibleTransfers.size());
        assertEquals(1, nonFungibleTransfers1.size());
        assertEquals(1, nonFungibleTransfers2.size());

        assertHbarTransfers(hbarTransfers);
        assertNftTransfers(nonFungibleTransfers1, nonFungibleTransfers2);
    }

    private void assertHbarTransfers(final List<HbarTransfer> hbarTransfers) {
        assertEquals(10, hbarTransfers.get(0).amount());
        assertFalse(hbarTransfers.get(0).isApproval());
        assertNull(hbarTransfers.get(0).sender());
        assertEquals(1, hbarTransfers.get(0).receiver().getAccountNum());

        assertEquals(10, hbarTransfers.get(1).amount());
        assertFalse(hbarTransfers.get(1).isApproval());
        assertNull(hbarTransfers.get(1).receiver());
        assertEquals(2, hbarTransfers.get(1).sender().getAccountNum());
    }

    private void assertFungibleTransfers(final List<FungibleTokenTransfer> fungibleTokenTransfers) {
        assertEquals(10, fungibleTokenTransfers.get(0).amount());
        assertTrue(fungibleTokenTransfers.get(0).isApproval());
        assertNull(fungibleTokenTransfers.get(0).sender());
        assertEquals(3, fungibleTokenTransfers.get(0).getDenomination().getTokenNum());

        assertEquals(10, fungibleTokenTransfers.get(1).amount());
        assertFalse(fungibleTokenTransfers.get(1).isApproval());
        assertNull(fungibleTokenTransfers.get(1).receiver());
        assertEquals(5, fungibleTokenTransfers.get(1).sender().getAccountNum());
        assertEquals(3, fungibleTokenTransfers.get(1).getDenomination().getTokenNum());
    }

    private void assertNftTransfers(final List<NftExchange> nftExchanges1, final List<NftExchange> nftExchanges2) {
        assertEquals(6, nftExchanges1.get(0).getTokenType().getTokenNum());
        assertEquals(2, nftExchanges1.get(0).getSerialNo());
        assertEquals(7, nftExchanges1.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(8, nftExchanges1.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertTrue(nftExchanges1.get(0).isApproval());

        assertEquals(9, nftExchanges2.get(0).getTokenType().getTokenNum());
        assertEquals(3, nftExchanges2.get(0).getSerialNo());
        assertEquals(10, nftExchanges2.get(0).asGrpc().getSenderAccountID().getAccountNum());
        assertEquals(11, nftExchanges2.get(0).asGrpc().getReceiverAccountID().getAccountNum());
        assertFalse(nftExchanges2.get(0).isApproval());
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private boolean existsMock(Address address) {
        return true;
    }
}
