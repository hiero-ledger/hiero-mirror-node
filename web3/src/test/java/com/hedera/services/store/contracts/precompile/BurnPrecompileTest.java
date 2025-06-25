// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.BurnPrecompile.getBurnWrapper;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SystemContractAbis;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txn.token.BurnLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
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
 * This class is a modified copy of BurnPrecompileTest from hedera-services repo.
 *
 * Differences with the original:
 *  1. Replaces Stores and Ledgers with {@link Store}
 *  2. Removed unnecessary tests
 */
@ExtendWith(MockitoExtension.class)
class BurnPrecompileTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;

    private static final String ABI_ID_BURN_TOKEN = "0x" + Integer.toHexString(AbiConstants.ABI_ID_BURN_TOKEN);
    private static final String ABI_ID_BURN_TOKEN_V2 = "0x" + Integer.toHexString(AbiConstants.ABI_ID_BURN_TOKEN_V2);
    private static final long TOKEN_ID_TO_BURN = token2.getTokenNum();
    private static final int NUMBERS_OF_TOKENS_TO_BURN = 33;
    private static final int SERIAL_NUMBERS_DATA = 96;
    private static final int EMPTY_ARGUMENT = 0;

    private static final Bytes FUNGIBLE_BURN_INPUT_V1 = Bytes.fromHexString(ABI_ID_BURN_TOKEN
            + convertToPaddedHex(TOKEN_ID_TO_BURN)
            + convertToPaddedHex(NUMBERS_OF_TOKENS_TO_BURN)
            + convertToPaddedHex(SERIAL_NUMBERS_DATA)
            + convertToPaddedHex(EMPTY_ARGUMENT));

    private static final int ZERO_NUMBERS_OF_TOKENS_TO_BURN = 0;
    private static final Bytes ZERO_FUNGIBLE_BURN = Bytes.fromHexString(ABI_ID_BURN_TOKEN
            + convertToPaddedHex(TOKEN_ID_TO_BURN)
            + convertToPaddedHex(ZERO_NUMBERS_OF_TOKENS_TO_BURN)
            + convertToPaddedHex(SERIAL_NUMBERS_DATA)
            + convertToPaddedHex(EMPTY_ARGUMENT));

    private static final Bytes FUNGIBLE_BURN_INPUT_V2 = Bytes.fromHexString(ABI_ID_BURN_TOKEN_V2
            + convertToPaddedHex(TOKEN_ID_TO_BURN)
            + convertToPaddedHex(NUMBERS_OF_TOKENS_TO_BURN)
            + convertToPaddedHex(SERIAL_NUMBERS_DATA)
            + convertToPaddedHex(EMPTY_ARGUMENT));

    private static final int TOKEN_ID_1_TO_BURN = 1;
    private static final int TOKEN_ID_2_TO_BURN = 2;
    private static final Bytes NON_FUNGIBLE_BURN_INPUT_V1 = Bytes.fromHexString(ABI_ID_BURN_TOKEN
            + convertToPaddedHex(TOKEN_ID_TO_BURN)
            + convertToPaddedHex(EMPTY_ARGUMENT)
            + convertToPaddedHex(SERIAL_NUMBERS_DATA)
            + convertToPaddedHex(TOKEN_ID_1_TO_BURN)
            + convertToPaddedHex(TOKEN_ID_2_TO_BURN));

    private static final Bytes NON_FUNGIBLE_BURN_INPUT_V2 = Bytes.fromHexString(ABI_ID_BURN_TOKEN_V2
            + convertToPaddedHex(TOKEN_ID_TO_BURN)
            + convertToPaddedHex(EMPTY_ARGUMENT)
            + convertToPaddedHex(SERIAL_NUMBERS_DATA)
            + convertToPaddedHex(TOKEN_ID_1_TO_BURN)
            + convertToPaddedHex(TOKEN_ID_2_TO_BURN));

    private static final String SUCCESS_RESPONSE_CODE = convertToPaddedHex(ResponseCodeEnum.SUCCESS.getNumber());

    private static final Bytes ACCOUNT_DELETED_FAIL_RESPONSE = Bytes.fromHexString("0x"
            + convertToPaddedHex(ResponseCodeEnum.ACCOUNT_DELETED.getNumber())
            + convertToPaddedHex(EMPTY_ARGUMENT));

    private static final Bytes RETURN_FUNGIBLE_BURN =
            Bytes.fromHexString("0x" + SUCCESS_RESPONSE_CODE + convertToPaddedHex(EMPTY_ARGUMENT));

    private static final Bytes RETURN_NON_FUNGIBLE_BURN =
            Bytes.fromHexString("0x" + SUCCESS_RESPONSE_CODE + convertToPaddedHex(EMPTY_ARGUMENT));

    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenBurn(TokenBurnTransactionBody.newBuilder().setToken(HTSTestsUtil.token2));

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
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private PrecompileContext precompileContext;

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
    private ContextOptionValidator contextOptionValidator;

    @Mock
    private TokenAccessor tokenAccessor;

    private HTSPrecompiledContract subject;
    private BurnPrecompile burnPrecompile;
    private PrecompileMapper precompileMapper;
    private BurnLogic burnLogic;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(
                HederaFunctionality.TokenBurn, Map.of(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        syntheticTxnFactory = new SyntheticTxnFactory();
        contextOptionValidator = new ContextOptionValidator(evmProperties);
        burnLogic = new BurnLogic(contextOptionValidator);
        encoder = new EncodingFacade();
        burnPrecompile = new BurnPrecompile(precompilePricingUtils, encoder, syntheticTxnFactory, burnLogic);
        precompileMapper = new PrecompileMapper(Set.of(burnPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @Test
    void nftBurnHappyPathWorks() {
        // given:
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        prepareStoreForNftBurn();
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
        given(precompileContext.getPrecompile()).willReturn(burnPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(RETURN_NON_FUNGIBLE_BURN, result);
    }

    @Test
    void fungibleBurnHappyPathWorks() {
        // given:
        final Bytes pretendArguments = givenFungibleFrameContext();
        prepareStoreForFungibleBurn(token2);
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
        given(precompileContext.getPrecompile()).willReturn(burnPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        final var txnBody = TransactionBody.newBuilder()
                .setTokenBurn(
                        TokenBurnTransactionBody.newBuilder().setToken(token2).setAmount(33L));

        given(precompileContext.getTransactionBody()).willReturn(txnBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, txnBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(RETURN_FUNGIBLE_BURN, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForBurnToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var txnBody = TransactionBody.newBuilder().setTokenBurn(TokenBurnTransactionBody.newBuilder());

        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(burnPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(FUNGIBLE_BURN_INPUT_V1, a -> a, precompileContext);
        final long result = subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, txnBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungibleBurnInput() {
        final var decodedInput = getBurnWrapper(FUNGIBLE_BURN_INPUT_V1, SystemContractAbis.BURN_TOKEN_V1);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(33, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleBurnInputV2() {
        final var decodedInput = getBurnWrapper(FUNGIBLE_BURN_INPUT_V2, SystemContractAbis.BURN_TOKEN_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(33, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleBurnInput() {
        final var decodedInput = getBurnWrapper(NON_FUNGIBLE_BURN_INPUT_V1, SystemContractAbis.BURN_TOKEN_V1);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(List.of(2L), decodedInput.serialNos());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    @Test
    void decodeNonFungibleBurnInputV2() {
        final var decodedInput = getBurnWrapper(NON_FUNGIBLE_BURN_INPUT_V2, SystemContractAbis.BURN_TOKEN_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(List.of(2L), decodedInput.serialNos());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    @Test
    void failResponse() {
        final var decodedInput = burnPrecompile.getFailureResultFor(ResponseCodeEnum.ACCOUNT_DELETED);
        assertEquals(ACCOUNT_DELETED_FAIL_RESPONSE, decodedInput);
    }

    @Test
    void burnZeroTokens() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> getBurnWrapper(ZERO_FUNGIBLE_BURN, SystemContractAbis.BURN_TOKEN_V1));
        assertEquals(BurnPrecompile.ILLEGAL_AMOUNT_TO_BURN, exception.getMessage());
    }

    private Bytes givenNonFungibleFrameContext() {
        givenFrameContext();
        return NON_FUNGIBLE_BURN_INPUT_V1;
    }

    private Bytes givenFungibleFrameContext() {
        givenFrameContext();
        return FUNGIBLE_BURN_INPUT_V1;
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(30000000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
    }

    private void prepareStoreForNftBurn() {
        final var uniqueTokens = new ArrayList<UniqueToken>();
        final var uniqueToken1 = Mockito.mock(UniqueToken.class);
        final var uniqueToken2 = Mockito.mock(UniqueToken.class);
        when(uniqueToken1.getSerialNumber()).thenReturn((long) TOKEN_ID_1_TO_BURN);
        when(uniqueToken2.getSerialNumber()).thenReturn((long) TOKEN_ID_2_TO_BURN);
        uniqueTokens.add(uniqueToken1);
        uniqueTokens.add(uniqueToken2);

        final var tokenID = token2;
        final var tokenAddress = Id.fromGrpcToken(tokenID).asEvmAddress();
        final var treasuryAddress = senderAddress;
        when(token.getTreasury()).thenReturn(account);
        when(token.getId()).thenReturn(Id.fromGrpcToken(tokenID));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);
        when(account.getAccountAddress()).thenReturn(treasuryAddress);
        when(updatedToken.getType()).thenReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        when(tokenModificationResult.token()).thenReturn(updatedToken);
        when(updatedToken.removedUniqueTokens()).thenReturn(uniqueTokens);
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        when(store.getTokenRelationship(new TokenRelationshipKey(tokenAddress, treasuryAddress), OnMissing.THROW))
                .thenReturn(tokenRelationship);
        when(token.burn(tokenRelationship, EMPTY_ARGUMENT)).thenReturn(tokenModificationResult);
    }

    private void prepareStoreForFungibleBurn(final TokenID tokenID) {
        final var tokenAddress = Id.fromGrpcToken(tokenID).asEvmAddress();
        final var treasuryAddress = senderAddress;
        when(token.getTreasury()).thenReturn(account);
        when(token.getId()).thenReturn(Id.fromGrpcToken(tokenID));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);
        when(account.getAccountAddress()).thenReturn(treasuryAddress);
        when(tokenModificationResult.token()).thenReturn(updatedToken);
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        when(store.getTokenRelationship(new TokenRelationshipKey(tokenAddress, treasuryAddress), OnMissing.THROW))
                .thenReturn(tokenRelationship);
        when(token.burn(tokenRelationship, NUMBERS_OF_TOKENS_TO_BURN)).thenReturn(tokenModificationResult);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }

    private static String convertToPaddedHex(int number) {
        return StringUtils.leftPad(Integer.toHexString(number), 64, "0");
    }

    private static String convertToPaddedHex(long number) {
        return StringUtils.leftPad(Long.toHexString(number), 64, "0");
    }
}
