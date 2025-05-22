// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_PAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.PausePrecompile.decodePause;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.PauseLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PausePrecompileTest {
    private final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private PauseLogic pauseLogic;

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
    private BodyParams bodyParams;

    @Mock
    private UnaryOperator<byte[]> aliasResolver;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private PrecompileContext precompileContext;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes FUNGIBLE_PAUSE_INPUT =
            Bytes.fromHexString("0x7c41ad2c000000000000000000000000000000000000000000000000000000000000043d");
    private static final Bytes NON_FUNGIBLE_PAUSE_INPUT =
            Bytes.fromHexString("0x7c41ad2c0000000000000000000000000000000000000000000000000000000000000445");

    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenPause(TokenPauseTransactionBody.newBuilder().setToken(FUNGIBLE));

    private HTSPrecompiledContract subject;
    private MockedStatic<PausePrecompile> staticPausePrecompile;
    private PausePrecompile pausePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(HederaFunctionality.TokenPause, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        pausePrecompile = new PausePrecompile(precompilePricingUtils, syntheticTxnFactory, pauseLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(pausePrecompile));
        staticPausePrecompile = Mockito.mockStatic(PausePrecompile.class);

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @AfterEach
    void closeMocks() {
        staticPausePrecompile.close();
    }

    @Test
    void pauseHappyPathWorks() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFungibleFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.getStore()).willReturn(store);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(pauseLogic.validateSyntax(any())).willReturn(OK);
        given(pausePrecompile.body(pretendArguments, aliasResolver, bodyParams)).willReturn(transactionBody);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(pausePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
        verify(pauseLogic).pause(fungibleId, store);
    }

    @Test
    void gasRequirementReturnsCorrectValueForPauseFungibleToken() {
        // given
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));
        staticPausePrecompile.when(() -> decodePause(pretendArguments)).thenReturn(fungiblePause);
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(pausePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a, precompileContext);
        final long result =
                subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungiblePauseInput() {
        staticPausePrecompile.when(() -> decodePause(FUNGIBLE_PAUSE_INPUT)).thenCallRealMethod();
        final var decodedInput = decodePause(FUNGIBLE_PAUSE_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    @Test
    void decodeNonFungiblePauseInput() {
        staticPausePrecompile.when(() -> decodePause(NON_FUNGIBLE_PAUSE_INPUT)).thenCallRealMethod();
        final var decodedInput = decodePause(NON_FUNGIBLE_PAUSE_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenFungibleFrameContext() {
        givenFrameContext();
        staticPausePrecompile.when(() -> decodePause(pretendArguments)).thenReturn(fungiblePause);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(30000000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
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
