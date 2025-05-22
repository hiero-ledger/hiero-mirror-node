// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.FUNGIBLE_SUCCESS_RESULT_WITH_10_SUPPLY;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleSuccessResultWithLongMaxValueSupply;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.MintPrecompile.getMintWrapper;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SystemContractAbis;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.MintLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This class is a modified copy of MintPrecompileTest from hedera-services repo.
 *
 * Differences with the original:
 *  1. Replaces Stores and Ledgers with {@link Store}
 *  2. Removed unnecessary tests
 */
@ExtendWith(MockitoExtension.class)
class MintPrecompileTest {
    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes ZERO_FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
            "0x278e0b88000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
            "0x278e0b88000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes FUNGIBLE_MINT_INPUT_WITH_MAX_AMOUNT = Bytes.fromHexString(
            "0x278e0b88000000000000000000000000000000000000000000000000000000000000043e0000000000000000000000000000000000000000000000007fffffffffffffff00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes FUNGIBLE_MINT_INPUT_V2 = Bytes.fromHexString(
            "0xe0f4059a000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes ZERO_FUNGIBLE_MINT_INPUT_V2 = Bytes.fromHexString(
            "0xe0f4059a000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes NON_FUNGIBLE_MINT_INPUT = Bytes.fromHexString(
            "0x278e0b88000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");
    private static final Bytes NON_FUNGIBLE_MINT_INPUT_V2 = Bytes.fromHexString(
            "0xe0f4059a000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");

    private static final Bytes RETURN_NON_FUNGIBLE_MINT_FOR_3_TOKENS =
            Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000016"
                    + "00000000000000000000000000000000000000000000000000000000000000000"
                    + "00000000000000000000000000000000000000000000000000000000000006"
                    + "00000000000000000000000000000000000000000000000000000000000000003"
                    + "0000000000000000000000000000000000000000000000000000000000000001"
                    + "0000000000000000000000000000000000000000000000000000000000000002"
                    + "0000000000000000000000000000000000000000000000000000000000000003");
    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenMint(TokenMintTransactionBody.newBuilder().setToken(HTSTestsUtil.token));

    @Mock
    private Id id;

    @Mock
    private com.hedera.services.store.models.Account account;

    @Mock
    private Token token;

    @Mock
    private Token updatedToken;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private TokenModificationResult tokenModificationResult;

    @Mock
    private CommonProperties commonProperties;

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Store store;

    @Mock
    private OptionValidator optionValidator;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private PrecompileContext precompileContext;

    private MintLogic mintLogic;
    private HTSPrecompiledContract subject;
    private MintPrecompile mintPrecompile;
    private PrecompileMapper precompileMapper;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(
                HederaFunctionality.TokenMint, Map.of(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        syntheticTxnFactory = new SyntheticTxnFactory();
        optionValidator = new ContextOptionValidator(evmProperties);
        mintLogic = new MintLogic(optionValidator);
        encoder = new EncodingFacade();
        mintPrecompile = new MintPrecompile(precompilePricingUtils, encoder, syntheticTxnFactory, mintLogic);
        precompileMapper = new PrecompileMapper(Set.of(mintPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @Test
    void nftMintHappyPathWorks() {
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        prepareStoreForNftMint();
        givenPricingUtilsContext();

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);

        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(mintPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(RETURN_NON_FUNGIBLE_MINT_FOR_3_TOKENS, result);
    }

    @Test
    void fungibleMintHappyPathWorks() {
        final Bytes pretendArguments = givenFungibleFrameContext();
        prepareStoreForFungibleMint(10L);
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);

        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(mintPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);
        // then:
        assertEquals(FUNGIBLE_SUCCESS_RESULT_WITH_10_SUPPLY, result);
    }

    @Test
    void fungibleMintForMaxAmountWorks() {
        // given:
        prepareStoreForFungibleMint(Long.MAX_VALUE);
        givenPricingUtilsContext();
        givenFrameContext();
        final Bytes pretendArguments = FUNGIBLE_MINT_INPUT_WITH_MAX_AMOUNT;
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(mintPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);
        // then:
        assertEquals(fungibleSuccessResultWithLongMaxValueSupply, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForMintToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var txnBody = TransactionBody.newBuilder().setTokenMint(TokenMintTransactionBody.newBuilder());

        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(mintPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(FUNGIBLE_MINT_INPUT, a -> a, precompileContext);
        final long result = subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, txnBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }
    //
    @Test
    void decodeFungibleMintInput() {
        final var decodedInput = getMintWrapper(FUNGIBLE_MINT_INPUT, SystemContractAbis.MINT_TOKEN_V1);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(15, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleMintInputV2() {
        final var decodedInput = getMintWrapper(FUNGIBLE_MINT_INPUT_V2, SystemContractAbis.MINT_TOKEN_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(15, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleMintInput() {
        final var decodedInput = getMintWrapper(NON_FUNGIBLE_MINT_INPUT, SystemContractAbis.MINT_TOKEN_V1);
        final var metadata1 = ByteString.copyFrom("NFT metadata test1".getBytes());
        final var metadata2 = ByteString.copyFrom("NFT metadata test2".getBytes());
        final List<ByteString> metadata = Arrays.asList(metadata1, metadata2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    @Test
    void decodeFungibleMintZeroInputV2() {
        final var decodedInput = getMintWrapper(ZERO_FUNGIBLE_MINT_INPUT_V2, SystemContractAbis.MINT_TOKEN_V2);
        final List<ByteString> metadata = new ArrayList<>();

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleMintZeroInput() {
        final var decodedInput = getMintWrapper(ZERO_FUNGIBLE_MINT_INPUT, SystemContractAbis.MINT_TOKEN_V1);
        final var metadata = new ArrayList<>();

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleMintInputV2() {
        final var decodedInput = getMintWrapper(NON_FUNGIBLE_MINT_INPUT_V2, SystemContractAbis.MINT_TOKEN_V2);
        final var metadata1 = ByteString.copyFrom("NFT metadata test1".getBytes());
        final var metadata2 = ByteString.copyFrom("NFT metadata test2".getBytes());
        final List<ByteString> metadata = Arrays.asList(metadata1, metadata2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    private Bytes givenNonFungibleFrameContext() {
        givenFrameContext();
        return NON_FUNGIBLE_MINT_INPUT;
    }

    private Bytes givenFungibleFrameContext() {
        givenFrameContext();
        return FUNGIBLE_MINT_INPUT;
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(30000000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
    }

    private void prepareStoreForNftMint() {
        final var uniqueTokens = new ArrayList<UniqueToken>();
        final var uniqueToken1 = Mockito.mock(UniqueToken.class);
        final var uniqueToken2 = Mockito.mock(UniqueToken.class);
        final var uniqueToken3 = Mockito.mock(UniqueToken.class);
        when(uniqueToken1.getSerialNumber()).thenReturn(1L);
        when(uniqueToken2.getSerialNumber()).thenReturn(2L);
        when(uniqueToken3.getSerialNumber()).thenReturn(3L);
        uniqueTokens.add(uniqueToken1);
        uniqueTokens.add(uniqueToken2);
        uniqueTokens.add(uniqueToken3);

        final var tokenAddress = EntityIdUtils.asTypedEvmAddress(HTSTestsUtil.token);
        final var treasuryAddress = senderAddress;
        when(token.getTreasury()).thenReturn(account);
        when(token.getId()).thenReturn(id);
        when(id.asEvmAddress()).thenReturn(tokenAddress);
        when(account.getAccountAddress()).thenReturn(treasuryAddress);
        when(token.mint(any(), any(), any())).thenReturn(tokenModificationResult);
        when(tokenModificationResult.token()).thenReturn(updatedToken);
        when(updatedToken.mintedUniqueTokens()).thenReturn(uniqueTokens);
        when(updatedToken.getType()).thenReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        when(store.getTokenRelationship(new TokenRelationshipKey(tokenAddress, treasuryAddress), OnMissing.THROW))
                .thenReturn(tokenRelationship);
    }

    private void prepareStoreForFungibleMint(final long newTotalSupply) {
        final var uniqueTokens = new ArrayList<UniqueToken>();
        final var uniqueToken1 = Mockito.mock(UniqueToken.class);
        uniqueTokens.add(uniqueToken1);

        final var tokenAddress = EntityIdUtils.asTypedEvmAddress(HTSTestsUtil.token);
        final var treasuryAddress = senderAddress;
        when(token.getTreasury()).thenReturn(account);
        when(token.getId()).thenReturn(id);
        when(id.asEvmAddress()).thenReturn(tokenAddress);
        when(account.getAccountAddress()).thenReturn(treasuryAddress);
        when(token.mint(any(), any(), any())).thenReturn(tokenModificationResult);
        when(tokenModificationResult.token()).thenReturn(updatedToken);
        when(updatedToken.getTotalSupply()).thenReturn(newTotalSupply);
        when(updatedToken.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        when(store.getTokenRelationship(new TokenRelationshipKey(tokenAddress, treasuryAddress), OnMissing.THROW))
                .thenReturn(tokenRelationship);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }
}
