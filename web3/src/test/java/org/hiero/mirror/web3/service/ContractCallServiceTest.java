// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.ALLOW_LONG_ZERO_ADDRESSES;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static org.hiero.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static org.hiero.mirror.web3.service.ContractCallService.GAS_USED_METRIC;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.exception.BlockNumberOutOfRangeException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.utils.BinaryGasEstimator;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.ERCTestContract;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.State;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

@RequiredArgsConstructor
class ContractCallServiceTest extends AbstractContractCallServiceTest {

    private final BinaryGasEstimator binaryGasEstimator;
    private final Store store;
    private final MirrorEvmTxProcessor mirrorEvmTxProcessor;
    private final RecordFileService recordFileService;
    private final ThrottleProperties throttleProperties;
    private final TransactionExecutionService transactionExecutionService;

    @MockitoBean
    private ThrottleManager throttleManager;

    private static Stream<BlockType> provideBlockTypes() {
        return Stream.of(
                BlockType.EARLIEST,
                BlockType.of("safe"),
                BlockType.of("pending"),
                BlockType.of("finalized"),
                BlockType.LATEST);
    }

    private static Stream<Arguments> provideCustomBlockTypes() {
        return Stream.of(
                Arguments.of(BlockType.of("0x1"), "0x", false),
                Arguments.of(
                        BlockType.of("0x100"),
                        "0x0000000000000000000000000000000000000000000000000000000000000004",
                        true));
    }

    private static Stream<Arguments> ercPrecompileCallTypeArgumentsProvider() {
        List<Long> gasLimits = List.of(15_000_000L, 34_000L);

        return Arrays.stream(CallType.values())
                .flatMap(callType -> gasLimits.stream().map(gasLimit -> Arguments.of(callType, gasLimit)));
    }

    private static String toHexWith64LeadingZeros(final Long value) {
        final String result;
        final var paddedHexString = String.format("%064x", value);
        result = "0x" + paddedHexString;
        return result;
    }

    private static Stream<Arguments> provideParametersForErcPrecompileExceptionalHalt() {
        return Stream.of(Arguments.of(CallType.ETH_CALL, 1), Arguments.of(CallType.ETH_ESTIMATE_GAS, 2));
    }

    @BeforeEach
    void setUp() {}

    @Test
    void callWithoutDataToAddressWithNoBytecodeReturnsEmptyResult() {
        // Given
        final var receiver = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiver);
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, receiverAddress);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void pureCall() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        // When
        final var result = contract.call_multiplySimpleNumbers().send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(4L));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    // This test will be removed in the future. Needed only for test coverage right now.
    @Test
    void pureCallModularizedServices() throws Exception {
        // Given
        final var modularizedServicesFlag = mirrorNodeEvmProperties.isModularizedServices();
        final var backupProperties = mirrorNodeEvmProperties.getProperties();

        try {
            activateModularizedFlagAndInitializeState();

            final var contract = testWeb3jService.deploy(EthCall::deploy);
            meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

            // When
            contract.call_multiplySimpleNumbers().send();

            // Then
            // Restore changed property values.
        } finally {
            mirrorNodeEvmProperties.setModularizedServices(modularizedServicesFlag);
            mirrorNodeEvmProperties.setProperties(backupProperties);
        }
    }

    @ParameterizedTest
    @MethodSource("provideBlockTypes")
    void pureCallWithBlock(BlockType blockType) throws Exception {
        // Given
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        // When
        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockType.number())));
            testWeb3jService.setBlockType(blockType);
            if (mirrorNodeEvmProperties.isModularizedServices()) {
                assertThatThrownBy(functionCall::send)
                        .isInstanceOf(MirrorEvmTransactionException.class)
                        .hasMessage(INVALID_CONTRACT_ID.name());
            } else {
                assertThatThrownBy(functionCall::send)
                        .isInstanceOf(MirrorEvmTransactionException.class)
                        .hasMessage(INVALID_TRANSACTION.name());
            }
        } else {
            assertThat(functionCall.send()).isEqualTo(BigInteger.valueOf(4L));
            assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
        }

        // Then
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("provideCustomBlockTypes")
    void pureCallWithCustomBlock(BlockType blockType, String expectedResponse, boolean checkGas) throws Exception {
        // we need entities present before the block timestamp of the custom block because we won't find them
        // when searching against the custom block timestamp
        // Given
        domainBuilder
                .recordFile()
                .customize(recordFileBuilder -> recordFileBuilder.index(blockType.number()))
                .persist();

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric

        // Then
        if (blockType.number() < EVM_V_34_BLOCK) { // Before the block the data did not exist yet
            contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockType.number())));
            testWeb3jService.setBlockType(blockType);
            if (mirrorNodeEvmProperties.isModularizedServices()) {
                assertThatThrownBy(functionCall::send)
                        .isInstanceOf(MirrorEvmTransactionException.class)
                        .hasMessage(INVALID_CONTRACT_ID.name());
            } else {
                assertThatThrownBy(functionCall::send)
                        .isInstanceOf(MirrorEvmTransactionException.class)
                        .hasMessage(INVALID_TRANSACTION.name());
            }

        } else {
            // Then
            assertThat(functionCall.send()).isEqualTo(new BigInteger(expectedResponse.substring(2), 16));

            if (checkGas) {
                assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
            }
        }

        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void pureCallWithOutOfRangeCustomBlockThrowsException() {
        // Given
        final var invalidBlock = BlockType.of("0x2540BE3FF");
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        meterRegistry.clear(); // Clear it as the contract deploy increases the gas limit metric
        contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(invalidBlock.number())));
        testWeb3jService.setBlockType(invalidBlock);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(BlockNumberOutOfRangeException.class)
                .hasMessage(UNKNOWN_BLOCK_NUMBER);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void estimateGasForPureCall() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);

        // When
        final var functionCall = contract.send_multiplySimpleNumbers();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    /**
     * If we make a contract call with the zero address (0x0) set as the recipient in the contractExecutionParameters,
     * Hedera treats this as a contract creation request rather than a function call. The contract is then initialized
     * using the contractCallData, which should contain the compiled bytecode of the contract. If contractCallData is
     * empty (0x0), the contract will be deployed without any functions(no fallback function as well, which is called
     * when the contract is called without specifying a function or when non-existent function is specified.) in its
     * bytecode. Respectively, any call to the contract will fail with CONTRACT_BYTECODE_EMPTY, indicating that the
     * contract exists, but does not have any executable logic.
     */
    @Test
    void estimateGasWithoutReceiver() {
        final var payer = accountEntityPersist();
        final var receiverAddress = Address.ZERO;

        final var contract = testWeb3jService.deployWithoutPersist(ERCTestContract::deploy);
        final var contractCallData = Bytes.fromHexString(contract.getContractBinary());

        final var serviceParametersEthCall = getContractExecutionParameters(
                contractCallData,
                toAddress(payer.toEntityId()),
                receiverAddress,
                0L,
                ETH_CALL,
                mirrorNodeEvmProperties.isModularizedServices());

        final var actualGasUsed = gasUsedAfterExecution(serviceParametersEthCall);
        final var serviceParametersEstimateGas = getContractExecutionParameters(
                contractCallData,
                toAddress(payer.toEntityId()),
                receiverAddress,
                0L,
                ETH_ESTIMATE_GAS,
                mirrorNodeEvmProperties.isModularizedServices());

        // When
        final var result = contractExecutionService.processCall(serviceParametersEstimateGas);
        final var estimatedGasUsed = longValueOf.applyAsLong(result);

        // Then
        assertThat(isWithinExpectedGasRange(estimatedGasUsed, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGasUsed, actualGasUsed)
                .isTrue();
    }

    @Test
    void viewCall() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_returnStorageData().send();

        // Then
        assertThat(result).isEqualTo("test");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void estimateGasForViewCall() {
        // Given
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());
        final var contract = testWeb3jService.deploy(EthCall::deploy);

        // When
        final var functionCall = contract.send_returnStorageData();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void transferFunds(boolean longZeroAddressAllowed) {
        // Given
        final var sender = accountEntityWithEvmAddressPersist();
        final var receiver = accountEntityWithEvmAddressPersist();
        final var senderAddress = getAliasAddressFromEntity(sender);

        Address receiverAddress;
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        if (longZeroAddressAllowed) {
            receiverAddress = Address.fromHexString(getAddressFromEntity(receiver));
        } else {
            receiverAddress = getAliasAddressFromEntity(receiver);
        }

        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, receiverAddress, senderAddress, 7L);

        // Then
        assertDoesNotThrow(() -> contractExecutionService.processCall(serviceParameters));
        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToNonSystemAccount() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var account = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_getAccountBalance(
                        getAliasAddressFromEntity(account).toHexString())
                .send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(account.getBalance()));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountReturnsZero() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var systemAccount = systemAccountEntityWithEvmAddressPersist();
        final var systemAccountAddress = EntityIdUtils.asHexedEvmAddress(
                new Id(systemAccount.getShard(), systemAccount.getRealm(), systemAccount.getNum()));
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_getAccountBalance(systemAccountAddress).send();

        // Then
        assertThat(result).isEqualTo(BigInteger.ZERO);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountViaAliasReturnsBalance() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var systemAccount = systemAccountEntityWithEvmAddressPersist();
        final var systemAccountAddress =
                Bytes.wrap(systemAccount.getEvmAddress()).toHexString();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_getAccountBalance(systemAccountAddress).send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(systemAccount.getBalance()));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToContractReturnsBalance() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result =
                contract.call_getAccountBalance(contract.getContractAddress()).send();

        // Then
        assertThat(result.longValue()).isNotZero();
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void estimateGasForBalanceCallToContract() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var functionCall = contract.send_getAccountBalance(contract.getContractAddress());

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
        assertGasLimit(ETH_ESTIMATE_GAS, TRANSACTION_GAS_LIMIT);
    }

    // This test will be removed in the future. Needed only for test coverage right now.
    @Test
    void estimateGasForBalanceCallToContractModularizedServices() throws Exception {
        // Given
        final var modularizedServicesFlag = mirrorNodeEvmProperties.isModularizedServices();
        final var backupProperties = mirrorNodeEvmProperties.getProperties();

        try {
            activateModularizedFlagAndInitializeState();
            final var contract = testWeb3jService.deploy(EthCall::deploy);
            meterRegistry.clear();

            // When
            final var functionCall = contract.send_getAccountBalance(contract.getContractAddress());

            // Then
            verifyEthCallAndEstimateGas(functionCall, contract);
        } finally {
            mirrorNodeEvmProperties.setModularizedServices(modularizedServicesFlag);
            mirrorNodeEvmProperties.setProperties(backupProperties);
        }
    }

    @Test
    void testRevertDetailMessage() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var functionCall = contract.send_testRevert();

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Custom revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void nonExistingFunctionCallWithFallback() {
        // Given
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        meterRegistry.clear();
        final var serviceParameters = getContractExecutionParameters(
                Bytes.fromHexString("0x12345678"), Address.fromHexString(contract.getContractAddress()));

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
    }

    @Test
    void ethCallWithValueAndSenderWithoutAlias() {
        // Given
        final var receiverEntity = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var payer = accountEntityPersist(); // Account without alias

        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.EMPTY, toAddress(payer.toEntityId()), receiverAddress, 10L);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
    }

    @Test
    void ethCallWithValueAndNotExistingSenderAddress() {
        // Given
        final var receiverEntity = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var notExistingAccountAddress = toAddress(domainBuilder.entityId());
        final var serviceParameters =
                getContractExecutionParametersWithValue(Bytes.EMPTY, notExistingAccountAddress, receiverAddress, 10L);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(PAYER_ACCOUNT_NOT_FOUND.name());
        } else {
            final var result = contractExecutionService.processCall(serviceParameters);
            assertThat(result).isEqualTo(HEX_PREFIX);
        }

        assertGasLimit(serviceParameters);
    }

    @Test
    void ethCallWithValueAndSenderWithoutKey() {
        // Given
        final var receiverEntity = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var senderEntity = accountEntityPersistCustomizable(e -> e.key(null));
        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.EMPTY, toAddress(senderEntity.toEntityId()), receiverAddress, 10L);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
    }

    @Test
    void ethCallWithValueAndSenderContractFails() {
        // Given
        final var receiverEntity = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var contractAddress = toAddress(accountEntityPersistCustomizable(e -> e.type(EntityType.CONTRACT))
                .toEntityId());
        final var serviceParameters =
                getContractExecutionParametersWithValue(Bytes.EMPTY, contractAddress, receiverAddress, 10L);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(PAYER_ACCOUNT_NOT_FOUND.name());
        } else {
            final var result = contractExecutionService.processCall(serviceParameters);
            assertThat(result).isEqualTo(HEX_PREFIX);
        }

        assertGasLimit(serviceParameters);
    }

    @Test
    void invalidFunctionSig() {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        final var wrongFunctionSignature = "0x12345678";
        final var serviceParameters = getContractExecutionParameters(
                Bytes.fromHexString(wrongFunctionSignature), Address.fromHexString(contract.getContractAddress()));

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("data", HEX_PREFIX);

        assertGasLimit(serviceParameters);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void transferNegative() {
        // Given
        final var receiver = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiver);
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.EMPTY, toAddress(payer.toEntityId()), receiverAddress, -5L);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_NEGATIVE_VALUE.name());
        } else {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage("Argument must be positive");
        }
        assertGasLimit(serviceParameters);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void transferExceedsBalance(boolean overridePayerBalance) {
        // Given
        mirrorNodeEvmProperties.setOverridePayerBalanceValidation(overridePayerBalance);
        final var receiver = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiver);
        final var senderEntity = accountEntityWithEvmAddressPersist();
        final var senderAddress = getAliasAddressFromEntity(senderEntity);
        final var value = senderEntity.getBalance() + 5L;
        final var serviceParameters =
                getContractExecutionParametersWithValue(Bytes.EMPTY, senderAddress, receiverAddress, value);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            if (mirrorNodeEvmProperties.isOverridePayerBalanceValidation()) {
                assertThat(contractExecutionService.processCall(serviceParameters))
                        .isEqualTo(HEX_PREFIX);
            } else {
                assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                        .isInstanceOf(MirrorEvmTransactionException.class)
                        .hasMessage(INSUFFICIENT_PAYER_BALANCE.name());
            }
        } else {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(
                            "Cannot remove %s wei from account, balance is only %s",
                            toHexWith64LeadingZeros(value), toHexWith64LeadingZeros(senderEntity.getBalance()));
        }
        assertGasLimit(serviceParameters);
        mirrorNodeEvmProperties.setOverridePayerBalanceValidation(false);
    }

    @Test
    void transferThruContract() throws Exception {
        // Given
        final var receiver = accountEntityWithEvmAddressPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiver);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        meterRegistry.clear();
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());
        // When
        contract.send_transferHbarsToAddress(receiverAddress.toHexString(), BigInteger.TEN)
                .send();
        // Then
        assertThat(testWeb3jService.getTransactionResult()).isEqualTo(HEX_PREFIX);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    /**
     * Testing that sending HBAR to a randomly generated 20-byte EVM address, that is not mapped to an existing
     * accountId will result in hollow account creation.
     */
    @Test
    void hollowAccountCreationWorks() {
        // Given
        final var value = 10L;
        final var hollowAccountAlias = domainBuilder.evmAddress();
        final var sender = accountEntityWithEvmAddressPersist();
        final var senderAddress = getAliasAddressFromEntity(sender);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        testWeb3jService.setSender(senderAddress.toHexString());

        // When
        final var functionCall = contract.send_createHollowAccount(
                Bytes.wrap(hollowAccountAlias).toHexString(), BigInteger.valueOf(value));

        // Then
        verifyEthCallAndEstimateGasWithValue(functionCall, contract, senderAddress, value);
    }

    @Test
    void estimateGasForStateChangeCall() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);

        // When
        final var functionCall = contract.send_writeToStorageSlot("test2", BigInteger.ZERO);

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void estimateGasForCreate2ContractDeploy() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);

        // When
        final var functionCall = contract.send_deployViaCreate2();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void estimateGasForDirectCreateContractDeploy() {
        // Given
        final var sender = accountEntityWithEvmAddressPersist();
        final var senderAddress = getAliasAddressFromEntity(sender);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, senderAddress);
        final var actualGas = 1413995L;

        // When
        final var result = contractExecutionService.processCall(serviceParameters);
        final var estimatedGas = longValueOf.applyAsLong(result);

        // Then
        assertThat(isWithinExpectedGasRange(estimatedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGas, actualGas)
                .isTrue();
    }

    @Test
    void estimateGasForDirectCreateContractDeployWithMissingSender() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, Address.ZERO);
        final var actualGas = 1413995L;

        // When
        final var result = contractExecutionService.processCall(serviceParameters);
        final var estimatedGas = longValueOf.applyAsLong(result);

        // Then
        assertThat(isWithinExpectedGasRange(estimatedGas, actualGas))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimatedGas, actualGas)
                .isTrue();
    }

    @Test
    void ethCallForContractDeploy() {
        // Given
        final var contract = testWeb3jService.deployWithoutPersist(EthCall::deploy);
        meterRegistry.clear();
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_CALL, Address.ZERO);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertGasLimit(serviceParameters);
        assertThat(result)
                .isEqualTo(Bytes.wrap(testWeb3jService.getContractRuntime()).toHexString());
    }

    @Test
    void nestedContractStateChangesWork() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var stateContract = testWeb3jService.deploy(State::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_nestedCall("testState", stateContract.getContractAddress())
                .send();

        // Then
        assertThat(result).isEqualTo("testState");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void contractCreationWorks() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_deployContract("state").send();

        // Then
        // "state" is set in the State contract and the State contract updates the state of the parent to the
        // concatenation of the string "state" twice, resulting in this value
        assertThat(result).isEqualTo("statestate");
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
    }

    @Test
    void contractCreationWorksWithIncreasedMaxSignedTxnSize() {
        // Given
        testWeb3jService.setUseContractCallDeploy(true);

        // When
        // The size of the EthCall contract is bigger than 6 KB which is the default max size of init bytecode
        // than can be deployed directly without uploading the contract as a file and then making a separate
        // contract create transaction with a file ID. So, if this deploy works, we have verified that the
        // property for increased maxSignedTxnSize works correctly.
        var result = testWeb3jService.deploy(EthCall::deploy);

        // Then
        assertThat(result.getContractBinary()).isEqualTo(EthCall.BINARY);
    }

    @Test
    void stateChangeWorksWithDynamicEthCall() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();
        final var newState = "newState";

        // When
        final var result = contract.call_writeToStorageSlot(newState).send();

        // Then
        assertThat(result).isEqualTo(newState);
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @ParameterizedTest
    @MethodSource("provideParametersForErcPrecompileExceptionalHalt")
    void ercPrecompileExceptionalHaltReturnsExpectedGasToBucket(final CallType callType) {
        // Given
        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());

        final var functionCall = contract.send_approve(
                toAddress(token.getTokenId()).toHexString(), getAliasFromEntity(payer), BigInteger.valueOf(2));

        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.fromHexString(functionCall.encodeFunctionCall()), Address.ZERO, Address.ZERO, callType, 100L);

        final long expectedUsedGasByThrottle =
                (long) (TRANSACTION_GAS_LIMIT * throttleProperties.getGasLimitRefundPercent() / 100f);

        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                throttleManager,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(throttleManager, times(1)).restore(expectedUsedGasByThrottle);
        verify(throttleManager).restore(anyLong());
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileContractRevertReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        // Given
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getBalance());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.call_nameNonStatic(Address.ZERO.toHexString());

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline = (long) (gasLimit * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                throttleManager,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(throttleManager, times(2)).restore(expectedUsedGasByThrottle);
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileSuccessReturnsExpectedGasToBucket(final CallType callType, final long gasLimit) {
        // Given
        final var token = fungibleTokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall =
                contract.call_name(toAddress(token.getTokenId()).toHexString());

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline = (long) (gasLimit * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle = Math.min(gasLimit - expectedGasUsed, gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                throttleManager,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(throttleManager, times(2)).restore(expectedUsedGasByThrottle);
    }

    @ParameterizedTest
    @CsvSource({
        "0000000000000000000000000000000000000167",
        "0000000000000000000000000000000000000168",
        "0000000000000000000000000000000000000169"
    })
    void callSystemPrecompileWithEmptyData(final String addressHex) {
        // Given
        final var address = Address.fromHexString(addressHex);
        final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, address);

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    private double getGasUsedBeforeExecution(final CallType callType) {
        final var callCounter = meterRegistry.find(GAS_USED_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(final double gasUsedBeforeExecution, final CallType callType) {
        final var counter = meterRegistry.find(GAS_USED_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        final var gasConsumed = counter.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }

    private void assertGasLimit(ContractExecutionParameters parameters) {
        assertGasLimit(parameters.getCallType(), parameters.getGas());
    }

    private void assertGasLimit(final CallType callType, final long gasLimit) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(gasLimit);
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiverAddress) {
        return getContractExecutionParameters(data, receiverAddress, ETH_CALL);
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final Bytes data, final Address receiverAddress, final CallType callType) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(callType == ETH_ESTIMATE_GAS)
                .isModularized(mirrorNodeEvmProperties.isModularizedServices())
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(Address.ZERO))
                .value(0L)
                .build();
    }

    private ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<?> functionCall,
            final Contract contract,
            final CallType callType,
            final long gasLimit) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(callType)
                .gas(gasLimit)
                .isEstimate(false)
                .isModularized(mirrorNodeEvmProperties.isModularizedServices())
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(new HederaEvmAccount(Address.ZERO))
                .value(0L)
                .build();
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data, final Address receiverAddress, final long value) {
        return getContractExecutionParametersWithValue(data, Address.ZERO, receiverAddress, value);
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data, final Address senderAddress, final Address receiverAddress, final long value) {
        return getContractExecutionParametersWithValue(data, senderAddress, receiverAddress, ETH_CALL, value);
    }

    private ContractExecutionParameters getContractExecutionParametersWithValue(
            final Bytes data,
            final Address senderAddress,
            final Address receiverAddress,
            final CallType callType,
            final long value) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(data)
                .callType(callType)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isModularized(mirrorNodeEvmProperties.isModularizedServices())
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(senderAddress))
                .value(value)
                .build();
    }

    private Entity systemAccountEntityWithEvmAddressPersist() {
        final var systemAccountEntityId = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), 700);

        return domainBuilder
                .entity()
                .customize(e -> e.id(systemAccountEntityId.getId())
                        .num(systemAccountEntityId.getNum())
                        .alias(toEvmAddress(systemAccountEntityId))
                        .balance(20000L))
                .persist();
    }

    @Nested
    class EVM46Validation {

        private static final Address NON_EXISTING_ADDRESS =
                Address.fromHexString("0xa7d9ddbe1f17865597fbd27ec712455208b6b76d");

        @Test
        void callToNonExistingContract() {
            // Given
            final var serviceParameters = getContractExecutionParameters(Bytes.EMPTY, NON_EXISTING_ADDRESS);

            // When
            final var result = contractExecutionService.processCall(serviceParameters);

            // Then
            assertThat(result).isEqualTo(HEX_PREFIX);
            assertGasLimit(serviceParameters);
        }

        @Test
        void transferToNonExistingContract() {
            // Given
            final var payer = accountEntityWithEvmAddressPersist();

            // The NON_EXISTING_ADDRESS should be a valid EVM alias key(Ethereum-style address derived from an ECDSA
            // public key), otherwise INVALID_ALIAS_KEY could be thrown
            final var serviceParameters = getContractExecutionParametersWithValue(
                    Bytes.EMPTY, getAliasAddressFromEntity(payer), NON_EXISTING_ADDRESS, 1L);

            // When
            final var result = contractExecutionService.processCall(serviceParameters);

            // Then
            assertThat(result).isEqualTo(HEX_PREFIX);
            assertGasLimit(serviceParameters);
        }

        @Test
        void testDirectTrafficThroughTransactionExecutionService() {
            MirrorNodeEvmProperties spyEvmProperties = spy(mirrorNodeEvmProperties);

            when(spyEvmProperties.isModularizedServices()).thenReturn(true);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(1.0);
            assertThat(spyEvmProperties.directTrafficThroughTransactionExecutionService())
                    .isTrue();

            when(spyEvmProperties.isModularizedServices()).thenReturn(true);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(0.0);
            assertThat(spyEvmProperties.directTrafficThroughTransactionExecutionService())
                    .isFalse();

            when(spyEvmProperties.isModularizedServices()).thenReturn(false);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(1.0);
            assertThat(spyEvmProperties.directTrafficThroughTransactionExecutionService())
                    .isFalse();

            when(spyEvmProperties.isModularizedServices()).thenReturn(false);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(0.0);
            assertThat(spyEvmProperties.directTrafficThroughTransactionExecutionService())
                    .isFalse();
        }

        @Test
        void shouldCallTransactionExecutionService() throws MirrorEvmTransactionException {
            final long estimatedGas = 1000L;
            MirrorNodeEvmProperties spyEvmProperties = spy(mirrorNodeEvmProperties);
            TransactionExecutionService txnExecutionService = mock(TransactionExecutionService.class);
            MirrorEvmTxProcessor mirrorEvmTxProcessor = mock(MirrorEvmTxProcessor.class);

            ContractCallService contractCallService = new ContractCallService(
                    mirrorEvmTxProcessor, null, null, null, null, null, spyEvmProperties, txnExecutionService) {};

            when(spyEvmProperties.isModularizedServices()).thenReturn(true);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(1.0);
            var params = ContractExecutionParameters.builder()
                    .isModularized(spyEvmProperties.directTrafficThroughTransactionExecutionService())
                    .build();
            when(txnExecutionService.execute(params, estimatedGas))
                    .thenReturn(HederaEvmTransactionProcessingResult.successful(
                            List.of(), 100, 0, 0, Bytes.EMPTY, Address.ZERO));

            contractCallService.doProcessCall(params, estimatedGas, true);

            verify(txnExecutionService, times(1)).execute(any(), anyLong());
            verify(mirrorEvmTxProcessor, never()).execute(any(), anyLong());
        }

        @ParameterizedTest
        @CsvSource({"true, 0.0", "false, 1.0", "false, 0.0"})
        void shouldNotCallTransactionExecutionService(boolean isModularizedServices, double trafficShare)
                throws MirrorEvmTransactionException {
            final long estimatedGas = 1000L;
            MirrorNodeEvmProperties spyEvmProperties = spy(mirrorNodeEvmProperties);
            TransactionExecutionService txnExecutionService = mock(TransactionExecutionService.class);
            MirrorEvmTxProcessor mirrorEvmTxProcessor = mock(MirrorEvmTxProcessor.class);
            CallServiceParameters params = mock(CallServiceParameters.class);

            ContractCallService contractCallService = new ContractCallService(
                    mirrorEvmTxProcessor, null, null, null, null, null, spyEvmProperties, txnExecutionService) {};

            when(spyEvmProperties.isModularizedServices()).thenReturn(isModularizedServices);
            when(spyEvmProperties.getModularizedTrafficPercent()).thenReturn(trafficShare);
            var result =
                    HederaEvmTransactionProcessingResult.successful(List.of(), 100, 0, 0, Bytes.EMPTY, Address.ZERO);
            if (isModularizedServices && trafficShare == 1.0) {
                when(txnExecutionService.execute(params, estimatedGas)).thenReturn(result);
            } else {
                when(mirrorEvmTxProcessor.execute(params, estimatedGas)).thenReturn(result);
            }

            contractCallService.doProcessCall(params, estimatedGas, true);

            verify(txnExecutionService, never()).execute(any(), anyLong());
            verify(mirrorEvmTxProcessor, times(1)).execute(any(), anyLong());
        }
    }
}
