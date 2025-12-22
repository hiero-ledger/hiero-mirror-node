// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HRC632Contract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractCallAliasTests extends AbstractContractCallServiceTest {

    private final Address nonExistingEvmAddress = Address.fromHexString("0x0123456789012345678901234567890123456789");

    @Test
    void isValidAliasStandardAccount() throws Exception {
        // Given
        final var account = accountEntityPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(account));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(account));
        // Then
        assertThat(result.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void isValidAliasWithAliasAccount() throws Exception {
        // Given
        final var accountWithEvmAddress = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        // Then
        assertThat(result.send()).isTrue();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void isValidAliasWithNonExistingLongZeroAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(Address.ZERO.toHexString());
        final var functionCall = contract.send_isValidAliasCall(Address.ZERO.toHexString());
        // Then
        assertThat(result.send()).isFalse();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void isValidAliasNonExistingEvmAddress() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(nonExistingEvmAddress.toString());
        final var functionCall = contract.send_isValidAliasCall(nonExistingEvmAddress.toString());

        // Then
        assertThat(result.send()).isFalse();
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void getAliasWithLongZeroAddress() throws Exception {
        // Given
        final var accountEntity = accountEntityWithEvmAddressPersist();
        final var addressAlias = getAliasAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.send_getEvmAddressAliasCall(getAddressFromEntity(accountEntity));
        // Then
        final var result = contract.call_getEvmAddressAliasCall(getAddressFromEntity(accountEntity))
                .send();
        assertThat(result.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
        assertThat(result.component2()).isEqualTo(addressAlias.toHexString());
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void getAliasWithNonExistingEvmAddress() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_getEvmAddressAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAliasWithNonExistingLongZeroAddress() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getEvmAddressAliasCall(Address.ZERO.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAccountAddressWithAlias() throws Exception {
        // Given
        final var accountEntity = accountEntityWithEvmAddressPersist();
        final var addressAlias = getAliasAddressFromEntity(accountEntity);
        final var accountLongZeroAddress = getAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.send_getHederaAccountNumAliasCall(addressAlias.toString());
        // Then
        final var result = contract.call_getHederaAccountNumAliasCall(addressAlias.toString())
                .send();
        assertThat(result.component1()).isEqualTo(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.getNumber()));
        assertThat(result.component2()).isEqualTo(accountLongZeroAddress);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void getAccountAddressWithNonExistingEvmAddress() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }

    @Test
    void getAccountAddressWithNonExistingLongZeroAddress() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(Address.ZERO.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());
    }
}
