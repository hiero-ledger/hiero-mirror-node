// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.RevokeKycPrecompile.decodeRevokeTokenKyc;
import static java.util.function.UnaryOperator.identity;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.store.contracts.precompile.impl.RevokeKycPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txn.token.RevokeKycLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeKycPrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes REVOKE_TOKEN_KYC_INPUT = Bytes.fromHexString(
            "0xaf99c63300000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");

    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenRevokeKyc(
                    TokenRevokeKycTransactionBody.newBuilder().setToken(token).setAccount(account));

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private MessageFrame frame;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private Store store;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private PrecompileContext precompileContext;

    private HTSPrecompiledContract subject;
    private PrecompileMapper precompileMapper;
    private RevokeKycLogic revokeKycLogic;
    private RevokeKycPrecompile revokeKycPrecompile;
    private SyntheticTxnFactory syntheticTxnFactory;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(
                HederaFunctionality.TokenRevokeKycFromAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        syntheticTxnFactory = new SyntheticTxnFactory();
        revokeKycLogic = new RevokeKycLogic();
        revokeKycPrecompile = new RevokeKycPrecompile(revokeKycLogic, syntheticTxnFactory, precompilePricingUtils);
        precompileMapper = new PrecompileMapper(Set.of(revokeKycPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @Test
    void RevokeKyc() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.getStore()).willReturn(store);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(revokeKycPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(REVOKE_TOKEN_KYC_INPUT, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForRevokeKyc() {
        // given
        givenMinimalFrameContext();
        givenPricingUtilsContext();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(DEFAULT_GAS_PRICE);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(revokeKycPrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(REVOKE_TOKEN_KYC_INPUT, a -> a, precompileContext);
        final var result = subject.getPrecompile(frame).getGasRequirement(TEST_CONSENSUS_TIME, transactionBody, sender);
        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeRevokeTokenKycInput() {
        final var decodedInput = decodeRevokeTokenKyc(REVOKE_TOKEN_KYC_INPUT, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
    }

    private void givenFrameContext() {
        givenMinimalFrameContext();
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }
}
