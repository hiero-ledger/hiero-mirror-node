// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile.decodeAssociation;
import static com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile.decodeMultipleAssociations;
import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
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
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
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
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This class is a modified copy of AssociatePrecompileTest from hedera-services repo.
 *
 * Differences with the original:
 *  1. Replaces Stores and Ledgers with {@link Store}
 *  2. Removed unnecessary tests
 */
@ExtendWith(MockitoExtension.class)
class AssociatePrecompileTest {

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / HTSTestsUtil.DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes ASSOCIATE_INPUT = Bytes.fromHexString(
            "0x49146bde00000000000000000000000000000000000000000000000000000000000004820000000000000000000000000000000000000000000000000000000000000480");
    private static final Bytes MULTIPLE_ASSOCIATE_INPUT = Bytes.fromHexString(
            "0x2e63879b00000000000000000000000000000000000000000000000000000000000004880000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004860000000000000000000000000000000000000000000000000000000000000486");
    private final TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
            .setTokenAssociate(TokenAssociateTransactionBody.newBuilder()
                    .setAccount(secondSender)
                    .addTokens(HTSTestsUtil.token));

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private MessageFrame frame;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private Store store;

    @Mock
    private EvmInfrastructureFactory infrastructureFactory;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private Account account;

    @Mock
    private Account updatedAccount;

    @Mock
    private Id id;

    @Mock
    private Token token;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private PrecompileContext precompileContext;

    @Mock
    private TokenAccessor tokenAccessor;

    private HTSPrecompiledContract subject;
    private AssociatePrecompile associatePrecompile;
    private AssociateLogic associateLogic;
    private PrecompileMapper precompileMapper;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices =
                new EnumMap<>(HederaFunctionality.class);
        canonicalPrices.put(TokenAssociateToAccount, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);

        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(assetLoader, exchange, feeCalculator, resourceCosts, accessorFactory);

        syntheticTxnFactory = new SyntheticTxnFactory();
        associateLogic = new AssociateLogic(evmProperties);
        associatePrecompile = new AssociatePrecompile(precompilePricingUtils, syntheticTxnFactory, associateLogic);
        precompileMapper = new PrecompileMapper(Set.of(associatePrecompile));

        subject = new HTSPrecompiledContract(
                infrastructureFactory, evmProperties, precompileMapper, store, tokenAccessor, precompilePricingUtils);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithDelegateCallFromParentFrame() {
        givenTokenAssociate();
        givenPricingUtilsContext();

        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(associatePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(ASSOCIATE_INPUT, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
    }

    @Test
    void computeAssociateTokenHappyPathWorksWithoutParentFrame() {
        givenTokenAssociate();

        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);

        givenPricingUtilsContext();

        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(associatePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(ASSOCIATE_INPUT, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
    }

    @Test
    void computeMultiAssociateTokenHappyPathWorks() {
        givenTokenAssociate();

        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.senderAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.getStore()).willReturn(store);

        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(associatePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);
        given(precompileContext.getTransactionBody()).willReturn(transactionBody);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(ASSOCIATE_INPUT, a -> a, precompileContext);
        subject.getPrecompile(frame).getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);
        final var result = subject.computeInternal(frame);

        // then:
        Assertions.assertEquals(HTSTestsUtil.successResult, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateTokens() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.multiAssociateOp.accountId());
        builder.addAllTokens(HTSTestsUtil.multiAssociateOp.tokenIds());
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(associatePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(ASSOCIATE_INPUT, a -> a, precompileContext);
        final long result = subject.getPrecompile(frame)
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForAssociateToken() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(HTSTestsUtil.ASSOCIATE_OP.accountId());
        builder.addAllTokens(HTSTestsUtil.ASSOCIATE_OP.tokenIds());
        given(feeCalculator.computeFee(any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any())).willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getPrecompile()).willReturn(associatePrecompile);
        given(precompileContext.getSenderAddress()).willReturn(senderAddress);

        subject.prepareFields(frame);
        subject.prepareComputation(ASSOCIATE_INPUT, a -> a, precompileContext);
        final long result = subject.getPrecompile(frame)
                .getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME, transactionBody, sender);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeAssociateToken() {
        final var decodedInput = decodeAssociation(ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
    }

    @Test
    void decodeMultipleAssociateToken() {
        final var decodedInput = decodeMultipleAssociations(MULTIPLE_ASSOCIATE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
        assertEquals(2, decodedInput.tokenIds().size());
        assertTrue(decodedInput.tokenIds().get(0).getTokenNum() > 0);
        assertTrue(decodedInput.tokenIds().get(1).getTokenNum() > 0);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
    }

    private void givenTokenAssociate() {
        given(store.getAccount(Address.fromHexString(asHexedEvmAddress(secondSender)), OnMissing.THROW))
                .willReturn(account);
        given(store.getToken(Address.fromHexString(tokenAddress.toHexString()), OnMissing.THROW))
                .willReturn(token);
        given(account.getAccountAddress()).willReturn(Address.fromHexString(asHexedEvmAddress(secondSender)));
        given(account.setNumAssociations(1)).willReturn(updatedAccount);
        given(evmProperties.getMaxTokensPerAccount()).willReturn(1000);
        given(token.getId()).willReturn(id);
        given(id.asEvmAddress()).willReturn(Address.fromHexString(asHexedEvmAddress(secondSender)));
        given(store.hasAssociation(any())).willReturn(false);
    }
}
