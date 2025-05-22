// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile.decodeWipeNFT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static java.util.function.UnaryOperator.identity;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txn.token.WipeLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeNonFungiblePrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes NON_FUNGIBLE_WIPE_INPUT = Bytes.fromHexString(
            "0xf7f38e2600000000000000000000000000000000000000000000000000000000000006b000000000000000000000000000000000000000000000000000000000000006ae000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001");
    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                    .setToken(nonFungible)
                    .setAccount(HTSTestsUtil.contract));

    @InjectMocks
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private AccessorFactory accessorFactory;

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
    private Store store;

    @Mock
    private Token token;

    @Mock
    private Token updatedToken;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private Account updatedAccount;

    @Mock
    private TokenModificationResult tokenModificationResult;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private PrecompileContext precompileContext;

    private HTSPrecompiledContract subject;
    private WipeNonFungiblePrecompile wipePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(
                HederaFunctionality.TokenAccountWipe, Map.of(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        SyntheticTxnFactory syntheticTxnFactory = new SyntheticTxnFactory();
        WipeLogic wipeLogic = new WipeLogic(evmProperties);
        wipePrecompile = new WipeNonFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
        PrecompileMapper precompileMapper = new PrecompileMapper(Set.of(wipePrecompile));
        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @Test
    void nftWipeHappyPathWorks() {
        givenNonfungibleFrameContext();
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
        given(precompileContext.getPrecompile()).willReturn(wipePrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(NON_FUNGIBLE_WIPE_INPUT, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForWipeNonFungibleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(wipePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(NON_FUNGIBLE_WIPE_INPUT, a -> a, precompileContext);
        final long result =
                subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeNonFungibleWipeInput() {
        final var decodedInput = decodeWipeNFT(NON_FUNGIBLE_WIPE_INPUT, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertEquals(-1, decodedInput.amount());
        assertEquals(1, decodedInput.serialNumbers().size());
        assertEquals(1, decodedInput.serialNumbers().get(0));
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    private void givenNonfungibleFrameContext() {
        givenFrameContext();
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(30000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);
        given(store.getToken(any(), any())).willReturn(token);
        given(store.loadUniqueTokens(any(), anyList())).willReturn(token);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(token.wipe(any(), anyList())).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(updatedToken);
        given(tokenModificationResult.tokenRelationship()).willReturn(tokenRelationship);
        given(tokenRelationship.getAccount()).willReturn(updatedAccount);
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
