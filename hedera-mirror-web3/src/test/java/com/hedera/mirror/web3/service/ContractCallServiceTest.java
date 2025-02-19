/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_USED_METRIC;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ERROR;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.SPENDER_ALIAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.contracts.execution.MirrorEvmTxProcessor;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.service.utils.BinaryGasEstimator;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.throttle.ThrottleProperties;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.ERCTestContract;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.State;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

class ContractCallServiceTest extends AbstractContractCallServiceTest {

    @Mock
    private Bucket gasLimitBucket;

    @Autowired
    private BinaryGasEstimator binaryGasEstimator;

    @Autowired
    private Store store;

    @Autowired
    private MirrorEvmTxProcessor mirrorEvmTxProcessor;

    @Autowired
    private RecordFileService recordFileService;

    @Mock
    private ThrottleProperties throttleProperties;

    @Autowired
    private TransactionExecutionService transactionExecutionService;

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
        List<Integer> gasUnits = List.of(1, 2);

        return Arrays.stream(CallType.values())
                .filter(callType -> !callType.equals(ERROR))
                .flatMap(callType -> gasLimits.stream().flatMap(gasLimit -> gasUnits.stream()
                        .map(gasUnit -> Arguments.of(callType, gasLimit, gasUnit))));
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

    @Override
    @BeforeEach
    protected void setup() {
        super.setup();
        given(throttleProperties.getGasLimitRefundPercent()).willReturn(100f);
        given(throttleProperties.getGasUnit()).willReturn(1);
    }

    @Test
    void callWithoutDataToAddressWithNoBytecodeReturnsEmptyResult() {
        // Given
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
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
            mirrorNodeEvmProperties.setModularizedServices(true);
            Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

            postConstructMethod.setAccessible(true); // Make the method accessible
            postConstructMethod.invoke(state);

            final Map<String, String> propertiesMap = new HashMap<>();
            propertiesMap.put("contracts.maxRefundPercentOfGasLimit", "100");
            propertiesMap.put("contracts.maxGasPerSec", "15000000");
            mirrorNodeEvmProperties.setProperties(propertiesMap);

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

    @Test
    void estimateGasWithoutReceiver() {
        // Given
        final var serviceParametersEthCall =
                getContractExecutionParameters(Bytes.fromHexString(HEX_PREFIX), Address.ZERO, ETH_CALL);
        final var actualGasUsed = gasUsedAfterExecution(serviceParametersEthCall);
        final var serviceParametersEstimateGas =
                getContractExecutionParameters(Bytes.fromHexString(HEX_PREFIX), Address.ZERO, ETH_ESTIMATE_GAS);

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

    @Test
    void transferFunds() {
        // Given
        final var senderEntity = accountPersist();
        final var receiverEntity = accountPersist();
        final var senderAddress = getAliasAddressFromEntity(senderEntity);
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
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
        final var accountEntity = accountPersist();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_getAccountBalance(
                        getAliasAddressFromEntity(accountEntity).toHexString())
                .send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(accountEntity.getBalance()));
        assertGasLimit(ETH_CALL, TRANSACTION_GAS_LIMIT);
        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void balanceCallToSystemAccountReturnsZero() throws Exception {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);
        final var systemAccountEntity = systemAccountPersist();
        final var systemAccountAddress = EntityIdUtils.asHexedEvmAddress(
                new Id(systemAccountEntity.getShard(), systemAccountEntity.getRealm(), systemAccountEntity.getNum()));
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
        final var systemAccountEntity = systemAccountPersist();
        final var systemAccountAddress =
                Bytes.wrap(systemAccountEntity.getEvmAddress()).toHexString();
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        meterRegistry.clear();

        // When
        final var result = contract.call_getAccountBalance(systemAccountAddress).send();

        // Then
        assertThat(result).isEqualTo(BigInteger.valueOf(systemAccountEntity.getBalance()));
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
            mirrorNodeEvmProperties.setModularizedServices(true);
            Method postConstructMethod = Arrays.stream(MirrorNodeState.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(PostConstruct.class))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("@PostConstruct method not found"));

            postConstructMethod.setAccessible(true); // Make the method accessible
            postConstructMethod.invoke(state);

            final Map<String, String> propertiesMap = new HashMap<>();
            propertiesMap.put("contracts.maxRefundPercentOfGasLimit", "100");
            propertiesMap.put("contracts.maxGasPerSec", "15000000");
            mirrorNodeEvmProperties.setProperties(propertiesMap);
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
    void ethCallWithValueAndNotExistingSenderAlias() {
        // Given
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var notExistingSenderAlias = Address.fromHexString("0x6b175474e89094c44da98b954eedeac495271d0f");
        final var serviceParameters =
                getContractExecutionParametersWithValue(Bytes.EMPTY, notExistingSenderAlias, receiverAddress, 10L);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
        assertGasLimit(serviceParameters);
    }

    @Test
    void invalidFunctionSig() {
        // Given
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ERROR);
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
        assertGasUsedIsPositive(gasUsedBeforeExecution, ERROR);
    }

    @Test
    void transferNegative() {
        // Given
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
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

    @Test
    void transferExceedsBalance() {
        // Given
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
        final var senderEntity = accountPersist();
        final var senderAddress = getAliasAddressFromEntity(senderEntity);
        final var value = senderEntity.getBalance() + 5L;
        final var serviceParameters =
                getContractExecutionParametersWithValue(Bytes.EMPTY, senderAddress, receiverAddress, value);
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(INSUFFICIENT_PAYER_BALANCE.name());
        } else {
            assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(
                            "Cannot remove %s wei from account, balance is only %s",
                            toHexWith64LeadingZeros(value), toHexWith64LeadingZeros(senderEntity.getBalance()));
        }
        assertGasLimit(serviceParameters);
    }

    @Test
    void transferThruContract() throws Exception {
        // Given
        final var receiverEntity = accountPersist();
        final var receiverAddress = getAliasAddressFromEntity(receiverEntity);
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

    @Test
    void hollowAccountCreationWorks() {
        // Given
        final var value = 10L;
        final var hollowAccountAlias = domainBuilder.evmAddress();
        final var senderEntity = accountPersist();
        final var senderAddress = getAliasAddressFromEntity(senderEntity);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        testWeb3jService.setSender(senderAddress.toHexString());

        // When
        final var functionCall = contract.send_transferHbarsToAddress(
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
        final var senderEntity = accountPersist();
        final var senderAddress = getAliasAddressFromEntity(senderEntity);
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var serviceParameters = testWeb3jService.serviceParametersForTopLevelContractCreate(
                contract.getContractBinary(), ETH_ESTIMATE_GAS, senderAddress);
        final var actualGas = 175242L;
        domainBuilder
                .entity()
                .customize(e -> e.id(801L)
                        .num(801L)
                        .createdTimestamp(genesisRecordFile.getConsensusStart())
                        .timestampRange(Range.atLeast(genesisRecordFile.getConsensusStart())))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(800L)
                        .num(800L)
                        .createdTimestamp(genesisRecordFile.getConsensusStart())
                        .timestampRange(Range.atLeast(genesisRecordFile.getConsensusStart())))
                .persist();

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
        final var actualGas = 175242L;

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
    void ercPrecompileExceptionalHaltReturnsExpectedGasToBucket(final CallType callType, final int gasUnit) {
        // Given
        final var token = tokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getCreatedTimestamp());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());

        final var functionCall = contract.send_approve(
                toAddress(token.getId()).toHexString(), SPENDER_ALIAS.toHexString(), BigInteger.valueOf(2));

        final var serviceParameters = getContractExecutionParametersWithValue(
                Bytes.fromHexString(functionCall.encodeFunctionCall()), Address.ZERO, Address.ZERO, callType, 100L);

        given(throttleProperties.getGasUnit()).willReturn(gasUnit);

        final long expectedUsedGasByThrottle = (long)
                (Math.floorDiv(TRANSACTION_GAS_LIMIT, gasUnit) * throttleProperties.getGasLimitRefundPercent() / 100f);

        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileContractRevertReturnsExpectedGasToBucket(
            final CallType callType, final long gasLimit, final int gasUnit) {
        // Given
        final var payer = accountEntityWithEvmAddressPersist();
        accountBalancePersist(payer, payer.getBalance());
        testWeb3jService.setSender(toAddress(payer.toEntityId()).toHexString());

        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.call_nameNonStatic(Address.ZERO.toHexString());
        given(throttleProperties.getGasUnit()).willReturn(gasUnit);

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (Math.floorDiv(gasLimit, gasUnit) * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle =
                Math.min(Math.floorDiv(gasLimit - expectedGasUsed, gasUnit), gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
    }

    @ParameterizedTest
    @MethodSource("ercPrecompileCallTypeArgumentsProvider")
    void ercPrecompileSuccessReturnsExpectedGasToBucket(
            final CallType callType, final long gasLimit, final int gasUnit) {
        // Given
        final var token = tokenPersist();
        final var contract = testWeb3jService.deploy(ERCTestContract::deploy);
        final var functionCall = contract.call_name(toAddress(token.getId()).toHexString());
        given(throttleProperties.getGasUnit()).willReturn(gasUnit);

        final var serviceParameters = getContractExecutionParameters(functionCall, contract, callType, gasLimit);
        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);
        final var gasLimitToRestoreBaseline =
                (long) (Math.floorDiv(gasLimit, gasUnit) * throttleProperties.getGasLimitRefundPercent() / 100f);
        final var expectedUsedGasByThrottle =
                Math.min(Math.floorDiv(gasLimit - expectedGasUsed, gasUnit), gasLimitToRestoreBaseline);
        final var contractCallServiceWithMockedGasLimitBucket = new ContractExecutionService(
                meterRegistry,
                binaryGasEstimator,
                store,
                mirrorEvmTxProcessor,
                recordFileService,
                throttleProperties,
                gasLimitBucket,
                mirrorNodeEvmProperties,
                transactionExecutionService);

        // When
        try {
            contractCallServiceWithMockedGasLimitBucket.processCall(serviceParameters);
        } catch (MirrorEvmTransactionException e) {
            // Ignore as this is not what we want to verify here.
        }

        // Then
        verify(gasLimitBucket).addTokens(expectedUsedGasByThrottle);
        verify(gasLimitBucket, times(1)).addTokens(anyLong());
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
                .isStatic(false)
                .receiver(receiverAddress)
                .sender(new HederaEvmAccount(senderAddress))
                .value(value)
                .build();
    }

    private Entity accountPersist() {
        return domainBuilder.entity().persist();
    }

    private Entity systemAccountPersist() {
        final var systemAccountEntityId = EntityId.of(700);

        return domainBuilder
                .entity()
                .customize(e -> e.id(systemAccountEntityId.getId())
                        .num(systemAccountEntityId.getNum())
                        .alias(toEvmAddress(systemAccountEntityId))
                        .balance(20000L))
                .persist();
    }

    private Entity tokenPersist() {
        final var tokenEntity =
                domainBuilder.entity().customize(e -> e.type(TOKEN)).persist();

        domainBuilder.token().customize(t -> t.tokenId(tokenEntity.getId())).persist();

        return tokenEntity;
    }

    protected Address getAliasAddressFromEntity(final Entity entity) {
        return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
    }

    @Nested
    class EVM46Validation {

        private static final Address NON_EXISTING_ADDRESS = toAddress(123456789);

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
            final var serviceParameters =
                    getContractExecutionParametersWithValue(Bytes.EMPTY, NON_EXISTING_ADDRESS, 1L);

            // When
            final var result = contractExecutionService.processCall(serviceParameters);

            // Then
            assertThat(result).isEqualTo(HEX_PREFIX);
            assertGasLimit(serviceParameters);
        }
    }
}
