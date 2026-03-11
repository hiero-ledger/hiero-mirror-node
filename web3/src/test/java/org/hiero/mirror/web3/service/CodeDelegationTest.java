// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.Function;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.abi.TypeDecoder;

@RequiredArgsConstructor
final class CodeDelegationTest extends AbstractContractCallServiceTest {

    @Test
    void callToAccountWithCodeDelegationExecutesTargetContract() {
        // Given - deploy a contract that has a pure function multiplySimpleNumbers() returning 4
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        // Create an account entity with delegation address pointing to the deployed contract
        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        // Encode the call data for multiplySimpleNumbers()
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - multiplySimpleNumbers returns 2 * 2 = 4
        assertThat(result).isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000004");
    }

    @Test
    void callToAccountWithCodeDelegationReturnsStorageData() {
        // Given - deploy a contract with a view function returnStorageData() returning "test"
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var functionCall = contract.call_returnStorageData();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);
        final var decodedResult =
                TypeDecoder.decodeUtf8String(result, 66).getValue(); // offset 66 - 2 for "0x" prefix and 64 for offset
        // Then - returnStorageData returns the string "test", ABI-encoded
        assertThat(decodedResult).isEqualTo("test");
    }

    @Test
    void estimateGasForCallToAccountWithCodeDelegation() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        final var account = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        // When
        final var functionCall = contract.send_multiplySimpleNumbers();

        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void callToAccountWithoutCodeDelegationReturnsEmpty() {
        // Given - an account with no delegation address
        final var account = accountEntityPersistWithCodeDelegation(null);
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToAccountWithZeroDelegationAddressReturnsEmpty() {
        // Given - an account with the zero delegation address (deletion marker)
        final var zeroDelegation = new byte[20];
        final var account = accountEntityPersistWithCodeDelegation(zeroDelegation);
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToNonExistingAccountWithCodeDelegationReturnsEmpty() {
        // Given - an existing account with code delegation to non-existing account
        final var randomAddress = Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

        // Create an account entity with delegation address pointing to the non-existing account
        final var account = accountEntityPersistWithCodeDelegation(randomAddress);
        final var accountAddress = toAddress(account.toEntityId());

        final var senderAccount = accountEntityPersist();
        final var senderAddress = toAddress(senderAccount.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, senderAddress, 1L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @ParameterizedTest
    @CsvSource({
        "0x0000000000000000000000000000000000000004",
        "0x0000000000000000000000000000000000000167",
        "0x0000000000000000000000000000000000000168",
        "0x0000000000000000000000000000000000000169"
    })
    void callToAccountWithDelegationToPrecompileIsNoOp(final String precompileAddress) {
        // Given - an account with delegation address pointing to a system contract / precompile
        final var account = accountEntityPersistWithCodeDelegation(
                Address.fromHexString(precompileAddress).toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());
        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - delegation to a precompile/system contract should result in a no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToSystemAccountWithCodeDelegationReturnsEmpty() {
        // Given - an existing account with code delegation to system account
        final var systemAccountAddress = toAddress(systemEntity.nodeRewardAccount());

        // Create an account entity with delegation address pointing to the system account
        final var account = accountEntityPersistWithCodeDelegation(systemAccountAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(Bytes.EMPTY, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // When
        final var result = contractExecutionService.processCall(serviceParameters);

        // Then - no-op
        assertThat(result).isEqualTo(HEX_PREFIX);
    }

    @Test
    void callToAccountWithCodeDelegationToAnotherAccountWithCodeDelegationFails() {
        // Given - deploy a contract that has a pure function multiplySimpleNumbers()
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        // Create an account entity with delegation address pointing to the deployed contract
        final var accountSecondary = accountEntityPersistWithCodeDelegation(contractAddress.toArrayUnsafe());
        final var accountAddressSecondary = toAddress(accountSecondary.toEntityId());

        // Encode the call data for multiplySimpleNumbers()
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var callData = Bytes.fromHexString(functionCall.encodeFunctionCall());

        // Create an account entity with delegation address pointing to the first account
        final var accountPrimary = accountEntityPersistWithCodeDelegation(accountAddressSecondary.toArrayUnsafe());
        final var accountAddressPrimary = toAddress(accountPrimary.toEntityId());

        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddressPrimary, Address.ZERO, 0L, ETH_CALL);

        // Then - results in literal execution of the delegation indicator (which will fail since 0xef is a banned op
        // code)
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void callToTokenWithCodeDelegationFails() {
        // Given - a fungible token
        final var tokenEntity = tokenEntityPersist();
        final var treasuryAccount = accountEntityPersist();
        final var token = fungibleTokenPersist(tokenEntity, treasuryAccount);
        final var tokenAddress = toAddress(tokenEntity.toEntityId());

        // Create an account entity with delegation address pointing to the token
        final var account = accountEntityPersistWithCodeDelegation(tokenAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var callData = Bytes.of(new Function("getTokenInfo(address)")
                .encodeCallWithArgs(asHeadlongAddress(tokenAddress.toArrayUnsafe()))
                .array());
        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    @Test
    void callToScheduleWithCodeDelegationFails() {
        // Given - a schedule entity
        final var scheduleEntity = scheduleEntityPersist();
        final var payerAccount = accountEntityPersist();
        final var schedule = schedulePersist(scheduleEntity, payerAccount, new byte[] {});
        final var scheduleAddress = toAddress(scheduleEntity.toEntityId());

        // Create an account entity with delegation address pointing to the schedule
        final var account = accountEntityPersistWithCodeDelegation(scheduleAddress.toArrayUnsafe());
        final var accountAddress = toAddress(account.toEntityId());

        final var callData = Bytes.of(new Function("authorizeSchedule(address)")
                .encodeCallWithArgs(asHeadlongAddress(scheduleAddress.toArrayUnsafe()))
                .array());
        final var serviceParameters =
                getContractExecutionParameters(callData, accountAddress, Address.ZERO, 0L, ETH_CALL);

        // Then
        assertThatThrownBy(() -> contractExecutionService.processCall(serviceParameters))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(CONTRACT_EXECUTION_EXCEPTION.name());
    }

    private Entity accountEntityPersistWithCodeDelegation(final byte[] delegationAddress) {
        return domainBuilder
                .entity()
                .customize(e -> e.type(EntityType.ACCOUNT)
                        .evmAddress(null)
                        .alias(null)
                        .balance(DEFAULT_ACCOUNT_BALANCE)
                        .delegationAddress(delegationAddress))
                .persist();
    }
}
