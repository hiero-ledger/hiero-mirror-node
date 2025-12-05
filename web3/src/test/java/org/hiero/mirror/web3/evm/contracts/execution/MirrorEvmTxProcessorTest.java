// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_46;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Account;
import jakarta.inject.Provider;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmWorldState;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MirrorEvmTxProcessorTest {

    private static final int MAX_STACK_SIZE = 1024;
    private static final String EVM_ADDRESS = "0x6b175474e89094c44da98b954eedeac495271d0f";
    private static final String FUNCTION_HASH = "0x8070450f";
    private final Address sender = Address.ALTBN128_ADD;
    private final HederaEvmAccount receiver = new HederaEvmAccount(Address.ALTBN128_MUL);
    private final Address receiverAddress = receiver.canonicalAddress();
    private final Address nativePrecompileAddress = Address.SHA256;
    private final Address invalidNativePrecompileAddress = Address.BLS12_G2ADD;
    private HederaEvmAccount senderWithAlias;

    @Mock
    private PricesAndFeesProvider pricesAndFeesProvider;

    @Mock
    private HederaEvmWorldState worldState;

    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private Set<Operation> operations;

    @Mock
    private HederaEvmWorldState.Updater updater;

    @Mock
    private HederaEvmStackedWorldStateUpdater stackedUpdater;

    @Mock
    private MirrorEvmContractAliases hederaEvmContractAliases;

    @Mock
    private HederaBlockValues hederaBlockValues;

    @Mock
    private HederaEvmBlocks blockMetaSource;

    @Mock
    private MirrorOperationTracer mirrorOperationTracer;

    @Mock
    private StoreImpl store;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private CodeFactory codeFactory;

    private MirrorEvmTxProcessorImpl mirrorEvmTxProcessor;

    static Stream<Arguments> provideIsEstimateParameters() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }

    @BeforeEach
    void setup() {
        setupGasCalculator();
        setupSenderWithAlias();
        final var operationRegistry = new OperationRegistry();
        MainnetEVMs.registerShanghaiOperations(operationRegistry, gasCalculator, BigInteger.ZERO);

        final var operationRegistryCancun = new OperationRegistry();
        MainnetEVMs.registerCancunOperations(operationRegistryCancun, gasCalculator, BigInteger.ZERO);

        operations.forEach(operationRegistry::put);
        final var v30 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.LONDON);
        final var v34 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.PARIS);
        final var v38 = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.SHANGHAI);
        final var v50 =
                new EVM(operationRegistryCancun, gasCalculator, EvmConfiguration.DEFAULT, EvmSpecVersion.CANCUN);

        final Map<SemanticVersion, Provider<MessageCallProcessor>> mcps = Map.of(
                EVM_VERSION_0_30,
                () -> new MessageCallProcessor(v30, new PrecompileContractRegistry()),
                EVM_VERSION_0_34,
                () -> new MessageCallProcessor(v34, new PrecompileContractRegistry()),
                EVM_VERSION_0_38,
                () -> new MessageCallProcessor(v38, new PrecompileContractRegistry()),
                EVM_VERSION_0_46,
                () -> new MessageCallProcessor(v38, new PrecompileContractRegistry()),
                EVM_VERSION_0_50,
                () -> new MessageCallProcessor(v50, new PrecompileContractRegistry()),
                EVM_VERSION,
                () -> new MessageCallProcessor(v50, new PrecompileContractRegistry()));
        Map<SemanticVersion, Provider<ContractCreationProcessor>> processorsMap = Map.of(
                EVM_VERSION_0_30, () -> new ContractCreationProcessor(v30, true, List.of(), 1),
                EVM_VERSION_0_34, () -> new ContractCreationProcessor(v34, true, List.of(), 1),
                EVM_VERSION_0_38, () -> new ContractCreationProcessor(v38, true, List.of(), 1),
                EVM_VERSION_0_46, () -> new ContractCreationProcessor(v38, true, List.of(), 1),
                EVM_VERSION_0_50, () -> new ContractCreationProcessor(v50, true, List.of(), 1),
                EVM_VERSION, () -> new ContractCreationProcessor(v50, true, List.of(), 1));

        mirrorEvmTxProcessor = new MirrorEvmTxProcessorImpl(
                worldState,
                pricesAndFeesProvider,
                evmProperties,
                gasCalculator,
                mcps,
                processorsMap,
                blockMetaSource,
                hederaEvmContractAliases,
                new AbstractCodeCache(10, hederaEvmEntityAccess),
                Map.of(TracerType.OPERATION, () -> mirrorOperationTracer),
                store,
                new EntityAddressSequencer(commonProperties),
                tokenAccessor,
                codeFactory);
    }

    @ParameterizedTest
    @MethodSource("provideIsEstimateParameters")
    void assertSuccessExecution(boolean isEstimate) {
        givenValidMockWithoutGetOrCreate();
        when(evmProperties.getSemanticEvmVersion()).thenReturn(EVM_VERSION_0_34);
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(pricesAndFeesProvider.currentGasPrice(any(), any())).willReturn(10L);
        given(store.getAccount(sender, OnMissing.DONT_THROW)).willReturn(Account.getDummySenderAccount(sender));

        final var params = ContractExecutionParameters.builder()
                .sender(sender)
                .receiver(receiver.canonicalAddress())
                .gas(33_333L)
                .gasPrice(0L)
                .value(1234L)
                .callData(Bytes.EMPTY)
                .isStatic(true)
                .isEstimate(isEstimate)
                .build();
        final var result = ContractCallContext.run(ignored -> mirrorEvmTxProcessor.execute(params, params.getGas()));

        assertThat(result)
                .isNotNull()
                .returns(true, HederaEvmTransactionProcessingResult::isSuccessful)
                .returns(receiver.canonicalAddress(), r -> r.getRecipient().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assertSuccessExecutionWithNotExistingSenderAlias(boolean isEstimate) {
        givenValidMockWithoutGetOrCreate();
        when(evmProperties.getSemanticEvmVersion()).thenReturn(EVM_VERSION);
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(pricesAndFeesProvider.currentGasPrice(any(), any())).willReturn(10L);
        given(store.getAccount(sender, OnMissing.DONT_THROW)).willReturn(Account.getEmptyAccount());

        final var params = ContractExecutionParameters.builder()
                .sender(senderWithAlias.canonicalAddress())
                .receiver(receiver.canonicalAddress())
                .gas(33_333L)
                .gasPrice(0L)
                .value(1234L)
                .callData(Bytes.EMPTY)
                .isStatic(true)
                .isEstimate(isEstimate)
                .build();
        final var result = ContractCallContext.run(ignored -> mirrorEvmTxProcessor.execute(params, params.getGas()));

        assertThat(result)
                .isNotNull()
                .returns(true, HederaEvmTransactionProcessingResult::isSuccessful)
                .returns(receiver.canonicalAddress(), r -> r.getRecipient().orElseThrow());
    }

    @Test
    void missingCodeThrowsException() {
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);

        final MessageFrame.Builder protoFrame = MessageFrame.builder()
                .worldUpdater(updater)
                .initialGas(1L)
                .originator(sender)
                .gasPrice(Wei.ZERO)
                .sender(sender)
                .value(Wei.ONE)
                .apparentValue(Wei.ONE)
                .blockValues(hederaBlockValues)
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup((ignored, gas) -> Hash.EMPTY);

        assertThatExceptionOfType(MirrorEvmTransactionException.class)
                .isThrownBy(() -> mirrorEvmTxProcessor.buildInitialFrame(
                        protoFrame, receiverAddress, Bytes.fromHexString(FUNCTION_HASH), 0L, codeFactory));
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        given(hederaEvmContractAliases.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(hederaEvmContractAliases.isMirror(receiverAddress)).willReturn(true);
        final long GAS_LIMIT = 300_000L;
        final Wei oneWei = Wei.of(1L);
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(sender)
                .gasPrice(Wei.ZERO)
                .sender(sender)
                .value(oneWei)
                .apparentValue(oneWei)
                .blockValues(mock(BlockValues.class))
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup((ignored, gas) -> Hash.EMPTY);
        // when:
        final MessageFrame buildMessageFrame = mirrorEvmTxProcessor.buildInitialFrame(
                commonInitialFrame, receiver.canonicalAddress(), Bytes.EMPTY, 0L, codeFactory);

        // expect:
        assertThat(sender).isEqualTo(buildMessageFrame.getSenderAddress());
        assertThat(oneWei).isEqualTo(buildMessageFrame.getApparentValue());
    }

    @Test
    void nativePrecompileCallSucceeds() {
        final var validPrecompilePayload = Bytes.fromHexString("0xFF");
        // setup:
        given(hederaEvmContractAliases.resolveForEvm(nativePrecompileAddress)).willReturn(nativePrecompileAddress);
        given(hederaEvmContractAliases.isMirror(nativePrecompileAddress)).willReturn(true);
        given(hederaEvmContractAliases.isNativePrecompileAddress(nativePrecompileAddress))
                .willReturn(true);

        final long GAS_LIMIT = 300_000L;
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(sender)
                .gasPrice(Wei.ZERO)
                .sender(sender)
                .value(Wei.ZERO)
                .apparentValue(Wei.ZERO)
                .blockValues(mock(BlockValues.class))
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup((ignored, gas) -> Hash.EMPTY);

        // when:
        final MessageFrame buildMessageFrame = mirrorEvmTxProcessor.buildInitialFrame(
                commonInitialFrame, nativePrecompileAddress, validPrecompilePayload, 0L, codeFactory);

        assertThat(sender).isEqualTo(buildMessageFrame.getSenderAddress());
        assertThat(buildMessageFrame.getApparentValue()).isEqualTo(Wei.ZERO);
        assertThat(nativePrecompileAddress).isEqualTo(buildMessageFrame.getRecipientAddress());
    }

    @Test
    void invalidNativePrecompileCallFails() {
        final var validPrecompilePayload = Bytes.fromHexString("0xFF");
        // setup:
        given(hederaEvmContractAliases.resolveForEvm(invalidNativePrecompileAddress))
                .willReturn(invalidNativePrecompileAddress);
        given(hederaEvmContractAliases.isMirror(invalidNativePrecompileAddress)).willReturn(true);
        given(hederaEvmContractAliases.isNativePrecompileAddress(invalidNativePrecompileAddress))
                .willReturn(false);

        final long GAS_LIMIT = 300_000L;
        final MessageFrame.Builder commonInitialFrame = MessageFrame.builder()
                .maxStackSize(MAX_STACK_SIZE)
                .worldUpdater(mock(WorldUpdater.class))
                .initialGas(GAS_LIMIT)
                .originator(sender)
                .gasPrice(Wei.ZERO)
                .sender(sender)
                .value(Wei.ZERO)
                .apparentValue(Wei.ZERO)
                .blockValues(mock(BlockValues.class))
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .blockHashLookup((ignored, gas) -> Hash.EMPTY);

        // when:
        assertThatExceptionOfType(MirrorEvmTransactionException.class)
                .isThrownBy(() -> mirrorEvmTxProcessor.buildInitialFrame(
                        commonInitialFrame, invalidNativePrecompileAddress, validPrecompilePayload, 0L, codeFactory));
    }

    private void givenValidMockWithoutGetOrCreate() {
        given(worldState.updater()).willReturn(updater);
        given(updater.updater()).willReturn(stackedUpdater);
        given(evmProperties.fundingAccountAddress()).willReturn(Address.ALTBN128_PAIRING);

        final var mutableAccount = mock(MutableAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false, 0L)).willReturn((long) 0);

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getSenderAccount(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(mutableAccount);

        given(blockMetaSource.blockValuesOf(anyLong())).willReturn(hederaBlockValues);
    }

    private void setupGasCalculator() {
        given(gasCalculator.getVeryLowTierGasCost()).willReturn(3L);
        given(gasCalculator.getLowTierGasCost()).willReturn(5L);
        given(gasCalculator.getMidTierGasCost()).willReturn(8L);
        given(gasCalculator.getBaseTierGasCost()).willReturn(2L);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(160L);
        given(gasCalculator.getColdSloadCost()).willReturn(2100L);
        given(gasCalculator.getSloadOperationGasCost()).willReturn(0L);
        given(gasCalculator.getHighTierGasCost()).willReturn(10L);
        given(gasCalculator.getJumpDestOperationGasCost()).willReturn(1L);
        given(gasCalculator.getZeroTierGasCost()).willReturn(0L);
    }

    private void setupSenderWithAlias() {
        senderWithAlias = new HederaEvmAccount(Address.ALTBN128_ADD);
        senderWithAlias.setAlias(ByteString.copyFrom(EVM_ADDRESS.getBytes()));
    }
}
