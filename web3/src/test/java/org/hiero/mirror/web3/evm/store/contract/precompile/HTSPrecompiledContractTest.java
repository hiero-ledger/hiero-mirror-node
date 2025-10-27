// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract.precompile;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.common.PrecompileContext.PRECOMPILE_CONTEXT;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_MUL;
import static org.hyperledger.besu.datatypes.Address.BLS12_G1ADD;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.PrecompileContext;
import org.hiero.mirror.web3.evm.exception.PrecompileNotSupportedException;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.StackedStateFrames;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({ContextExtension.class, MockitoExtension.class})
class HTSPrecompiledContractTest {

    private static final Bytes MOCK_PRECOMPILE_FUNCTION_HASH = Bytes.fromHexString("0x00000000");
    private static final Pair<Long, Bytes> FAILURE_RESULT = Pair.of(0L, null);

    @Mock
    private EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private MessageFrame parentMessageFrame;

    @Mock
    private MessageFrame lastFrame;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private TransactionBody.Builder transactionBodyBuilder;

    @Mock
    private PrecompileContext precompileContext;

    @Mock
    private BlockValues blockValues;

    @Mock
    private ViewGasCalculator gasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private ViewExecutor viewExecutor;

    @Mock
    private RedirectViewExecutor redirectViewExecutor;

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private HederaEvmWorldStateTokenAccount account;

    @Mock
    private PrecompilePricingUtils precompilePricingUtils;

    @Mock
    private OptionValidator validator;

    private Deque<MessageFrame> messageFrameStack;
    private Store store;

    @InjectMocks
    private MockPrecompile mockPrecompile;

    private HTSPrecompiledContract subject;

    private PrecompileMapper precompileMapper;

    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @BeforeEach
    void setUp() {
        final var accessors = List.<DatabaseAccessor<Object, ?>>of(
                new BareDatabaseAccessor<Object, Character>() {}, new BareDatabaseAccessor<Object, String>() {});

        final var stackedStateFrames = new StackedStateFrames(accessors);

        store = new StoreImpl(stackedStateFrames, validator);
        messageFrameStack = new ArrayDeque<>();
        messageFrameStack.push(lastFrame);
        messageFrameStack.push(messageFrame);

        var commonProperties = new CommonProperties();
        var systemEntity = new SystemEntity(commonProperties);
        mirrorNodeEvmProperties = new MirrorNodeEvmProperties(commonProperties, systemEntity);
        precompileMapper = new PrecompileMapper(Set.of(mockPrecompile));
        subject = new HTSPrecompiledContract(
                evmInfrastructureFactory,
                mirrorNodeEvmProperties,
                precompileMapper,
                store,
                tokenAccessor,
                precompilePricingUtils);
    }

    @Test
    void returnResultForStaticFrame() {
        // isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(messageFrame.isStatic()).willReturn(true);

        final var expectedResult = Pair.of(1000L, Bytes.EMPTY);
        given(viewExecutor.computeCosted()).willReturn(expectedResult);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForNonStaticFrameAndViewFunction() {
        // isTokenAddress signature
        final var functionHash = Bytes.fromHexString("0x19f37361");

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(viewExecutor.computeCosted()).willReturn(Pair.of(0L, Bytes.EMPTY));
        given(messageFrame.isStatic()).willReturn(false);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, Bytes.EMPTY);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void returnResultForStaticFrameAndErcViewFunction() {
        // name signature
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME, ALTBN128_ADD);

        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(redirectViewExecutor.computeCosted()).willReturn(Pair.of(0L, SUCCESS_RESULT));

        given(messageFrame.isStatic()).willReturn(true);

        final var precompileResult = subject.computeCosted(input, messageFrame, gasCalculator, tokenAccessor);

        assertThat(Pair.of(0L, SUCCESS_RESULT)).isEqualTo(precompileResult);
    }

    @Test
    void nonStaticCallToPrecompileWorks() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(precompileContext.getPrecompile()).willReturn(mockPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBodyBuilder);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, SUCCESS_RESULT);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void unqualifiedDelegateCallFails() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void invalidFeeSentFails() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(ALTBN128_MUL);
        given(lastFrame.getContractAddress()).willReturn(ALTBN128_MUL);
        given(lastFrame.getRecipientAddress()).willReturn(ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(precompileContext.getPrecompile()).willReturn(mockPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBodyBuilder);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.INVALID_FEE_SUBMITTED));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void delegateParentCallToTokenRedirectFails() {
        messageFrameStack.push(parentMessageFrame);

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void callingNonExistingPrecompileHalts() {
        // mock precompile signature
        final var functionHash = Bytes.fromHexString("0x11111111");

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);

        assertThrows(
                PrecompileNotSupportedException.class,
                () -> subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor));
    }

    @Test
    void nullPrecompileResponseHalts() {
        // mock precompile signature
        final var functionHash = Bytes.fromHexString("0x000000000000000000000000000000000000000000000000");

        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ECREC);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.get(BLS12_G1ADD)).willReturn(account);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(precompileContext.getPrecompile()).willReturn(mockPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBodyBuilder);

        final var precompileResult = subject.computeCosted(functionHash, messageFrame, gasCalculator, tokenAccessor);

        assertThat(FAILURE_RESULT).isEqualTo(precompileResult);
    }

    @Test
    void delegateCallWithNoParent() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(lastFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(BLS12_G1ADD);
        given(messageFrame.getSenderAddress()).willReturn(ALTBN128_MUL);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(BLS12_G1ADD)).willReturn(account);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(account.getNonce()).willReturn(-1L);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(precompileContext.getPrecompile()).willReturn(mockPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBodyBuilder);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.SUCCESS));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void invalidTransactionIsHandledProperly() {
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(Address.ZERO);
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getValue()).willReturn(Wei.ZERO);
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(10L);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(precompileContext);
        given(precompileContext.getSenderAddress()).willReturn(contractAddress);
        given(precompileContext.getPrecompile()).willReturn(mockPrecompile);
        given(precompileContext.getTransactionBody()).willReturn(transactionBodyBuilder);

        final var precompileResult =
                subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, EncodingFacade.resultFrom(ResponseCodeEnum.INVALID_ACCOUNT_ID));
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void redirectForErcViewFunctionWorks() {
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME, ALTBN128_ADD);

        given(messageFrame.isStatic()).willReturn(false);
        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(redirectViewExecutor.computeCosted()).willReturn(Pair.of(0L, SUCCESS_RESULT));

        final var precompileResult = subject.computeCosted(input, messageFrame, gasCalculator, tokenAccessor);

        final var expectedResult = Pair.of(0L, SUCCESS_RESULT);
        assertThat(expectedResult).isEqualTo(precompileResult);
    }

    @Test
    void missingPrecompileContextThrowsError() {
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrame.getContractAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getRecipientAddress()).willReturn(ALTBN128_ADD);
        given(messageFrame.getSenderAddress()).willReturn(ALTBN128_MUL);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(messageFrame.getMessageFrameStack()).willReturn(stack);
        given(stack.getLast()).willReturn(lastFrame);
        given(lastFrame.getContextVariable(PRECOMPILE_CONTEXT)).willReturn(null);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.computeCosted(MOCK_PRECOMPILE_FUNCTION_HASH, messageFrame, gasCalculator, tokenAccessor));
    }

    Bytes prerequisitesForRedirect(final int descriptor, final Address tokenAddress) {
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                tokenAddress,
                Bytes.of(Integers.toBytes(descriptor)));
    }

    static class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
        @NonNull
        @Override
        public Optional<V> get(@NonNull final K key, final Optional<Long> timestamp) {
            throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
        }
    }
}
