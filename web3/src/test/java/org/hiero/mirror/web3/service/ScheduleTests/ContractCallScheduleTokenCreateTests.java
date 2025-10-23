// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HIP756Contract;
import org.junit.jupiter.api.Test;

/**
 * This test class validates the correct results for schedule create token transactions via smart contract calls.
 */
class ContractCallScheduleTokenCreateTests extends AbstractContractCallScheduleTest {

    @Test
    void scheduleCreateFungibleTokenTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();

        final var sendFunction = contract.send_scheduleCreateFT(
                getAddressFromEntity(autoRenew), getAddressFromEntity(treasury), DEFAULT_TINYBAR_VALUE);
        final var callFunction =
                contract.call_scheduleCreateFT(getAddressFromEntity(autoRenew), getAddressFromEntity(treasury));
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
    void scheduleCreateFungibleTokenWithDesignatedPayerTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();
        final var designatedPayer = accountEntityPersist();

        final var sendFunction = contract.send_scheduleCreateFTWithDesignatedPayer(
                getAddressFromEntity(autoRenew),
                getAddressFromEntity(treasury),
                getAddressFromEntity(designatedPayer),
                DEFAULT_TINYBAR_VALUE);
        final var callFunction = contract.call_scheduleCreateFTWithDesignatedPayer(
                getAddressFromEntity(autoRenew), getAddressFromEntity(treasury), getAddressFromEntity(designatedPayer));
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
    void scheduleCreateNonFungibleTokenTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();

        final var sendFunction = contract.send_scheduleCreateNFT(
                getAddressFromEntity(autoRenew), getAddressFromEntity(treasury), DEFAULT_TINYBAR_VALUE);
        final var callFunction =
                contract.call_scheduleCreateNFT(getAddressFromEntity(autoRenew), getAddressFromEntity(treasury));
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
    void scheduleCreateNonFungibleTokenWithDesignatedPayerTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();
        final var designatedPayer = accountEntityPersist();

        final var sendFunction = contract.send_scheduleCreateNFTWithDesignatedPayer(
                getAddressFromEntity(autoRenew),
                getAddressFromEntity(treasury),
                getAddressFromEntity(designatedPayer),
                DEFAULT_TINYBAR_VALUE);
        final var callFunction = contract.call_scheduleCreateNFTWithDesignatedPayer(
                getAddressFromEntity(autoRenew), getAddressFromEntity(treasury), getAddressFromEntity(designatedPayer));
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
    void scheduleUpdateTokenTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury.toEntityId());

        final var sendFunction = contract.send_scheduleUpdateTreasuryAndAutoRenewAcc(
                toAddress(token.getTokenId()).toHexString(),
                getAddressFromEntity(treasury),
                getAddressFromEntity(autoRenew),
                token.getName(),
                token.getSymbol(),
                new String(token.getMetadata(), StandardCharsets.UTF_8));
        final var callFunction = contract.call_scheduleUpdateTreasuryAndAutoRenewAcc(
                toAddress(token.getTokenId()).toHexString(),
                getAddressFromEntity(treasury),
                getAddressFromEntity(autoRenew),
                token.getName(),
                token.getName(),
                new String(token.getMetadata(), StandardCharsets.UTF_8));
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
    void scheduleUpdateTokenDesignatedPayerTest() throws Exception {
        final var contract = testWeb3jService.deploy(HIP756Contract::deploy);
        final var treasury = accountEntityPersist();
        final var autoRenew = accountEntityPersist();
        final var token = fungibleTokenPersistWithTreasuryAccount(treasury.toEntityId());
        final var designatedPayer = accountEntityPersist();

        final var sendFunction = contract.send_scheduleUpdateTreasuryAndAutoRenewAccWithDesignatedPayer(
                toAddress(token.getTokenId()).toHexString(),
                getAddressFromEntity(treasury),
                getAddressFromEntity(autoRenew),
                token.getName(),
                token.getName(),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                getAddressFromEntity(designatedPayer));
        final var callFunction = contract.call_scheduleUpdateTreasuryAndAutoRenewAccWithDesignatedPayer(
                toAddress(token.getTokenId()).toHexString(),
                getAddressFromEntity(treasury),
                getAddressFromEntity(autoRenew),
                token.getName(),
                token.getName(),
                new String(token.getMetadata(), StandardCharsets.UTF_8),
                getAddressFromEntity(designatedPayer));
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
