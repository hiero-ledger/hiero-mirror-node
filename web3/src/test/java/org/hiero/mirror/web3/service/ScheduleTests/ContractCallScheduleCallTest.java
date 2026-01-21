// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.config.data.ContractsConfig;
import java.math.BigInteger;
import java.time.Instant;
import org.hiero.mirror.web3.web3j.generated.HIP1215Contract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class ContractCallScheduleCallTest extends AbstractContractCallScheduleTest {

    private static final BigInteger EXPIRY_SHIFT = BigInteger.valueOf(1800);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(2_000_000L);

    private boolean isSystemCallEnabled() {
        return evmProperties
                .getVersionedConfiguration()
                .getConfigData(ContractsConfig.class)
                .systemContractScheduleCallEnabled();
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testScheduleCall() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_scheduleCallExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallExample(EXPIRY_SHIFT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testScheduleCallWithPayer() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();

        // When
        final var sendFunction = contract.send_scheduleCallWithPayerExample(getAddressFromEntity(payer), EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallWithPayerExample(getAddressFromEntity(payer), EXPIRY_SHIFT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testExecuteCallOnPayerSignature() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();

        // When
        final var sendFunction =
                contract.send_executeCallOnPayerSignatureExample(getAddressFromEntity(payer), EXPIRY_SHIFT);
        final var callFunction =
                contract.call_executeCallOnPayerSignatureExample(getAddressFromEntity(payer), EXPIRY_SHIFT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testDeleteSchedule() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();
        final var sender = accountEntityPersist();
        final var receiver = accountEntityPersist();
        final var scheduleEntity = scheduleEntityPersist();
        schedulePersist(scheduleEntity, payer, buildDefaultScheduleTransactionBody(sender, receiver));

        // When
        final var sendFunction = contract.send_deleteScheduleExample(getAddressFromEntity(scheduleEntity));
        final var callFunction = contract.call_deleteScheduleExample(getAddressFromEntity(scheduleEntity));

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        assertThat(callFunctionResult).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testDeleteScheduleThroughFacade() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();
        final var sender = accountEntityPersist();
        final var receiver = accountEntityPersist();
        final var scheduleEntity = scheduleEntityPersist();
        schedulePersist(scheduleEntity, payer, buildDefaultScheduleTransactionBody(sender, receiver));

        // When
        final var sendFunction = contract.send_deleteScheduleProxyExample(getAddressFromEntity(scheduleEntity));
        final var callFunction = contract.call_deleteScheduleProxyExample(getAddressFromEntity(scheduleEntity));

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        assertThat(callFunctionResult).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testHasScheduleCapacity() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_hasScheduleCapacityExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_hasScheduleCapacityExample(EXPIRY_SHIFT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        assertThat(callFunctionResult).isTrue();
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testHasScheduleCapacityProxy() throws Exception {
        // Given
        final var expirySecond = BigInteger.valueOf(Instant.now().getEpochSecond() + 20_000L);
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_hasScheduleCapacityProxy(expirySecond, GAS_LIMIT);
        final var callFunction = contract.call_hasScheduleCapacityProxy(expirySecond, GAS_LIMIT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        assertThat(callFunctionResult).isTrue();
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testScheduleCallWithCapacityCheckAndDelete() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_scheduleCallWithCapacityCheckAndDeleteExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallWithCapacityCheckAndDeleteExample(EXPIRY_SHIFT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }

    @EnabledIf("isSystemCallEnabled")
    @Test
    void testScheduleCallWithDefaultCallData() throws Exception {
        // Given
        final var expirySecond = BigInteger.valueOf(Instant.now().getEpochSecond() + 20_000L);
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction =
                contract.send_scheduleCallWithDefaultCallData(expirySecond, GAS_LIMIT, BigInteger.ZERO);
        final var callFunction = contract.call_scheduleCallWithDefaultCallData(expirySecond, GAS_LIMIT);

        // Then
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }
}
