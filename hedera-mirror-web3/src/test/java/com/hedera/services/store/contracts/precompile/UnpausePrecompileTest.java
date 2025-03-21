// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UNPAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile.decodeUnpause;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.common.PrecompileContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.UnpauseLogic;
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
class UnpausePrecompileTest {
    private final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private UnpauseLogic unpauseLogic;

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
    private static final Bytes FUNGIBLE_UNPAUSE_INPUT =
            Bytes.fromHexString("0x3b3bff0f0000000000000000000000000000000000000000000000000000000000000441");

    private static final Bytes NON_FUNGIBLE_UNPAUSE_INPUT =
            Bytes.fromHexString("0x3b3bff0f0000000000000000000000000000000000000000000000000000000000000449");

    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenUnpause(TokenUnpauseTransactionBody.newBuilder().setToken(FUNGIBLE));

    private HTSPrecompiledContract subject;
    private MockedStatic<UnpausePrecompile> staticUnpausePrecompile;

    private UnpausePrecompile unpausePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(HederaFunctionality.TokenUnpause, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        unpausePrecompile = new UnpausePrecompile(precompilePricingUtils, syntheticTxnFactory, unpauseLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(unpausePrecompile));
        staticUnpausePrecompile = Mockito.mockStatic(UnpausePrecompile.class);

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @AfterEach
    void closeMocks() {
        staticUnpausePrecompile.close();
    }

    @Test
    void unpauseHappyPathWorks() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFungibleFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.getStore()).willReturn(store);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(unpauseLogic.validateSyntax(any())).willReturn(OK);
        given(unpausePrecompile.body(pretendArguments, aliasResolver, bodyParams))
                .willReturn(transactionBody);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(unpausePrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
        verify(unpauseLogic).unpause(fungibleId, store);
    }

    @Test
    void gasRequirementReturnsCorrectValueForUnpauseFungibleToken() {
        // given
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));
        staticUnpausePrecompile.when(() -> decodeUnpause(pretendArguments)).thenReturn(fungibleUnpause);
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(unpausePrecompile);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a, precompileContext);
        final long result =
                subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungibleUnpauseInput() {
        staticUnpausePrecompile
                .when(() -> decodeUnpause(FUNGIBLE_UNPAUSE_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeUnpause(FUNGIBLE_UNPAUSE_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    @Test
    void decodeNonFungibleUnpauseInput() {
        staticUnpausePrecompile
                .when(() -> decodeUnpause(NON_FUNGIBLE_UNPAUSE_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeUnpause(NON_FUNGIBLE_UNPAUSE_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenFungibleFrameContext() {
        givenFrameContext();
        staticUnpausePrecompile.when(() -> decodeUnpause(pretendArguments)).thenReturn(fungibleUnpause);
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
