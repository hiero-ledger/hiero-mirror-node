// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenUpdateExpiryInfoWrapper;
import static com.hedera.services.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile.getTokenUpdateExpiryInfoWrapper;
import static java.util.function.UnaryOperator.identity;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.store.contracts.precompile.impl.SystemContractAbis;
import com.hedera.services.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Deque;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTokenExpiryInfoPrecompileTest {

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private PrecompileMapper precompileMapper;

    @Mock
    private ContextOptionValidator optionValidator;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private TokenUpdateLogic tokenUpdateLogic;

    @Mock
    private Store store;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private PrecompileContext precompileContext;

    private final TransactionBody.Builder transactionBody =
            TransactionBody.newBuilder().setTokenUpdate(TokenUpdateTransactionBody.newBuilder());

    public static final Bytes UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT = Bytes.fromHexString(
            "0x593d6e8200000000000000000000000000000000000000000000000000000000000008d300000000000000000000000000000000000000000000000000000000bbf7edc700000000000000000000000000000000000000000000000000000000000008d000000000000000000000000000000000000000000000000000000000002820a8");
    public static final Bytes UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2 = Bytes.fromHexString(
            "0xd27be6cd00000000000000000000000000000000000000000000000000000000000008d3000000000000000000000000000000000000000000000000000000000bf7edc700000000000000000000000000000000000000000000000000000000000008d00000000000000000000000000000000000000000000000000000000000000008");

    private HTSPrecompiledContract subject;
    private UpdateTokenExpiryInfoPrecompile updateTokenExpiryInfoPrecompile;
    private MockedStatic<UpdateTokenExpiryInfoPrecompile> staticUpdateTokenExpiryInfoPrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        staticUpdateTokenExpiryInfoPrecompile = mockStatic(UpdateTokenExpiryInfoPrecompile.class);

        optionValidator = new ContextOptionValidator(evmProperties);
        updateTokenExpiryInfoPrecompile = new UpdateTokenExpiryInfoPrecompile(
                tokenUpdateLogic, evmProperties, optionValidator, syntheticTxnFactory, precompilePricingUtils);

        precompileMapper = new PrecompileMapper(Set.of(updateTokenExpiryInfoPrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @AfterEach
    void closeMocks() {
        if (!staticUpdateTokenExpiryInfoPrecompile.isClosed()) {
            staticUpdateTokenExpiryInfoPrecompile.close();
        }
    }

    @Test
    void updateTokenExpiryInfoHappyPath() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        givenFrameContext();
        givenMinimalContextForSuccessfulCall();
        givenUpdateTokenContext();

        given(syntheticTxnFactory.createTokenUpdateExpiryInfo(tokenUpdateExpiryInfoWrapper))
                .willReturn(TransactionBody.newBuilder().setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));

        // givenPricingUtilsContext
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(updateTokenExpiryInfoPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void updateTokenExpiryInfoV2HappyPath() {
        // given
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        givenMinimalContextForSuccessfulCall();
        givenUpdateTokenContextV2();

        given(syntheticTxnFactory.createTokenUpdateExpiryInfo(tokenUpdateExpiryInfoWrapper))
                .willReturn(TransactionBody.newBuilder().setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));

        // givenPricingUtilsContext
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(updateTokenExpiryInfoPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2, a -> a, precompileContext);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successResult, result);
    }

    @Test
    void decodeUpdateExpiryInfoForTokenInput() {
        staticUpdateTokenExpiryInfoPrecompile.close();
        final var decodedInput = getTokenUpdateExpiryInfoWrapper(
                UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT, identity(), SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V1);

        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertTrue(decodedInput.expiry().second() > 0);
        assertTrue(decodedInput.expiry().autoRenewAccount().getAccountNum() > 0);
        assertTrue(decodedInput.expiry().autoRenewPeriod() > 0);
    }

    @Test
    void decodeUpdateExpiryInfoV2ForTokenInput() {
        staticUpdateTokenExpiryInfoPrecompile.close();
        final var decodedInput = getTokenUpdateExpiryInfoWrapper(
                UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2, identity(), SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V2);

        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertTrue(decodedInput.expiry().second() > 0);
        assertTrue(decodedInput.expiry().autoRenewAccount().getAccountNum() > 0);
        assertTrue(decodedInput.expiry().autoRenewPeriod() > 0);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenUpdateTokenContext() {
        given(frame.getInputData()).willReturn(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT);
        given(tokenUpdateLogic.validate(any())).willReturn(ResponseCodeEnum.OK);
        staticUpdateTokenExpiryInfoPrecompile
                .when(() -> getTokenUpdateExpiryInfoWrapper(
                        any(), any(), eq(SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V1)))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
    }

    private void givenUpdateTokenContextV2() {
        given(frame.getInputData()).willReturn(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2);
        given(tokenUpdateLogic.validate(any())).willReturn(ResponseCodeEnum.OK);
        staticUpdateTokenExpiryInfoPrecompile
                .when(() -> getTokenUpdateExpiryInfoWrapper(
                        any(), any(), eq(SystemContractAbis.UPDATE_TOKEN_EXPIRY_INFO_V2)))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
    }
}
