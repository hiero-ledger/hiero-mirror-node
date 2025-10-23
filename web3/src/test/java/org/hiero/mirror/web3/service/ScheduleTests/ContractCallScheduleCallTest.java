// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import java.math.BigInteger;
import java.time.Instant;
import org.hiero.mirror.web3.evm.store.CachingStateFrame.CacheAccessIncorrectTypeException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HIP1215Contract;
import org.junit.jupiter.api.Test;

class ContractCallScheduleCallTest extends AbstractContractCallScheduleTest {

    private static final BigInteger EXPIRY_SHIFT = BigInteger.valueOf(40);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(2_000_000L);

    @Test
    void testScheduleCall() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_scheduleCallExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallExample(EXPIRY_SHIFT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            verifyCallFunctionResult(callFunctionResult);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void testScheduleCallWithPayer() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();

        // When
        final var sendFunction = contract.send_scheduleCallWithPayerExample(getAddressFromEntity(payer), EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallWithPayerExample(getAddressFromEntity(payer), EXPIRY_SHIFT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            // Currently not working as in the current hedera-app version the ScheduleCallTranslator has a function
            // signature "scheduleCallWithSender"
            // which in hedera-app v0.67 gets renamed to "scheduleCallWithPayer" which is the correct signature
            // according to HIP-1215.
            // Everything is set up according to the HIP and needs to be uncommented once we bump hedera-app.

            //            verifyEthCallAndEstimateGas(sendFunction, contract);
            //            final var callFunctionResult = callFunction.send();
            //            verifyCallFunctionResult(callFunctionResult);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

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
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            // Currently not working as in the current hedera-app version the ScheduleCallTranslator has a function
            // signature "executeCallOnSenderSignature"
            // which in hedera-app v0.67 gets renamed to "executeCallOnPayerSignature" which is the correct signature
            // according to HIP-1215.
            // Everything is set up according to the HIP and needs to be uncommented once we bump hedera-app.

            //            verifyEthCallAndEstimateGas(sendFunction, contract);
            //            final var callFunctionResult = callFunction.send();
            //            verifyCallFunctionResult(callFunctionResult);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void testDeleteSchedule() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();
        final var sender = accountEntityPersist();
        final var receiver = accountEntityPersist();
        final var scheduleEntity = scheduleEntityPersist();
        final var schedule =
                schedulePersist(scheduleEntity, payer, buildDefaultScheduleTransactionBody(sender, receiver));

        // When
        final var sendFunction = contract.send_deleteScheduleExample(getAddressFromEntity(scheduleEntity));
        final var callFunction = contract.call_deleteScheduleExample(getAddressFromEntity(scheduleEntity));

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            assertThat(callFunctionResult).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void testDeleteScheduleThroughFacade() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);
        final var payer = accountEntityPersist();
        final var sender = accountEntityPersist();
        final var receiver = accountEntityPersist();
        final var scheduleEntity = scheduleEntityPersist();
        final var schedule =
                schedulePersist(scheduleEntity, payer, buildDefaultScheduleTransactionBody(sender, receiver));

        // When
        final var sendFunction = contract.send_deleteScheduleProxyExample(getAddressFromEntity(scheduleEntity));
        final var callFunction = contract.call_deleteScheduleProxyExample(getAddressFromEntity(scheduleEntity));

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            assertThat(callFunctionResult).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
        } else {
            final var exception = assertThrows(CacheAccessIncorrectTypeException.class, sendFunction::send);
        }
    }

    @Test
    void testHasScheduleCapacity() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_hasScheduleCapacityExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_hasScheduleCapacityExample(EXPIRY_SHIFT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            assertThat(callFunctionResult).isTrue();
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void testHasScheduleCapacityProxy() throws Exception {
        // Given
        final var expirySecond = BigInteger.valueOf(Instant.now().getEpochSecond() + 20_000L);
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_hasScheduleCapacityProxy(expirySecond, GAS_LIMIT);
        final var callFunction = contract.call_hasScheduleCapacityProxy(expirySecond, GAS_LIMIT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            assertThat(callFunctionResult).isTrue();
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

    @Test
    void testScheduleCallWithCapacityCheckAndDelete() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HIP1215Contract::deploy);

        // When
        final var sendFunction = contract.send_scheduleCallWithCapacityCheckAndDeleteExample(EXPIRY_SHIFT);
        final var callFunction = contract.call_scheduleCallWithCapacityCheckAndDeleteExample(EXPIRY_SHIFT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            verifyCallFunctionResult(callFunctionResult);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }

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
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(sendFunction, contract);
            final var callFunctionResult = callFunction.send();
            verifyCallFunctionResult(callFunctionResult);
        } else {
            final var exception = assertThrows(MirrorEvmTransactionException.class, sendFunction::send);
            assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
        }
    }
}
