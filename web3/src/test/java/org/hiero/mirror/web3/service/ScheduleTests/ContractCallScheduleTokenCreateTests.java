// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.ScheduleTests;

import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import java.nio.charset.StandardCharsets;
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
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
        verifyEthCallAndEstimateGas(sendFunction, contract);
        final var callFunctionResult = callFunction.send();
        verifyCallFunctionResult(callFunctionResult);
    }
}
