// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.HRC632Contract;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class ContractCallAliasTests extends AbstractContractCallServiceTest {

    Address nonExistingLongZeroAddress = Address.wrap("0x0000000000000000000000000000000000000000");
    Address nonExistingEvmAddress = Address.wrap("0x0123456789012345678901234567890123456789");

    @Test
    void isValidAliasStandardAccount() throws Exception {
        // Given
        var account = accountEntityPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(account));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(account));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isValidAliasWithAliasAccount() throws Exception {
        // Given
        var accountWithEvmAddress = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isValidAliasWithNonExistingLongZeroAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(nonExistingLongZeroAddress.toString());
        final var functionCall = contract.send_isValidAliasCall(nonExistingLongZeroAddress.toString());
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void isValidAliasNonExistingEvmAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(nonExistingEvmAddress.toString());
        final var functionCall = contract.send_isValidAliasCall(nonExistingLongZeroAddress.toString());
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void getAliasWithLongZeroAddress() throws Exception {
        // Given
        var accountEntity = accountEntityWithEvmAddressPersist();
        var addressAlias = getAliasAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.send_getEvmAddressAliasCall(getAddressFromEntity(accountEntity));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var result = contract.call_getEvmAddressAliasCall(getAddressFromEntity(accountEntity))
                    .send();
            assertThat(result.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
            assertThat(result.component2()).isEqualTo(addressAlias.toHexString());
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void getAliasWithNonExistingEvmAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_getEvmAddressAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAliasWithNonExistingLongZeroAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getEvmAddressAliasCall(nonExistingLongZeroAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAccountAddressWithAlias() throws Exception {
        // Given
        var accountEntity = accountEntityWithEvmAddressPersist();
        var addressAlias = getAliasAddressFromEntity(accountEntity);
        var accountLongZeroAddress = getAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When

        final var functionCall = contract.send_getHederaAccountNumAliasCall(addressAlias.toString());
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var result = contract.call_getHederaAccountNumAliasCall(addressAlias.toString())
                    .send();
            assertThat(result.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
            assertThat(result.component2()).isEqualTo(accountLongZeroAddress);
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }

    @Test
    void getAccountAddressWithNonExistingEvmAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAccountAddressWithNonExistingLongZeroAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(nonExistingLongZeroAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }
}
