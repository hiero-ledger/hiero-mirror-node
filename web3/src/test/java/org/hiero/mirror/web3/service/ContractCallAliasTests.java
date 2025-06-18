// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.ALLOW_LONG_ZERO_ADDRESSES;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.HRC632Contract;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractCallAliasTests extends AbstractContractCallServiceTest {

    private final Address nonExistingEvmAddress = Address.fromHexString("0x0123456789012345678901234567890123456789");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isValidAliasStandardAccount(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var account = accountEntityPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(account));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(account));
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isValidAliasWithAliasAccount(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var accountWithEvmAddress = accountEntityWithEvmAddressPersist();
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        final var functionCall = contract.send_isValidAliasCall(getAddressFromEntity(accountWithEvmAddress));
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isTrue();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isValidAliasWithNonExistingLongZeroAddress(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(Address.ZERO.toHexString());
        final var functionCall = contract.send_isValidAliasCall(Address.ZERO.toHexString());
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void isValidAliasNonExistingEvmAddress(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_isValidAliasCall(nonExistingEvmAddress.toString());
        final var functionCall = contract.send_isValidAliasCall(nonExistingEvmAddress.toString());
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result.send()).isFalse();
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAliasWithLongZeroAddress(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var accountEntity = accountEntityWithEvmAddressPersist();
        final var addressAlias = getAliasAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.send_getEvmAddressAliasCall(getAddressFromEntity(accountEntity));
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
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

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAliasWithNonExistingEvmAddress(boolean longZeroAddressAllowed) {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var result = contract.call_getEvmAddressAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, result::send);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAliasWithNonExistingLongZeroAddress(boolean longZeroAddressAllowed) {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getEvmAddressAliasCall(Address.ZERO.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAccountAddressWithAlias(boolean longZeroAddressAllowed) throws Exception {
        // Given
        final var accountEntity = accountEntityWithEvmAddressPersist();
        final var addressAlias = getAliasAddressFromEntity(accountEntity);
        final var accountLongZeroAddress = getAddressFromEntity(accountEntity);
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.send_getHederaAccountNumAliasCall(addressAlias.toString());
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
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

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAccountAddressWithNonExistingEvmAddress(boolean longZeroAddressAllowed) {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(nonExistingEvmAddress.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getAccountAddressWithNonExistingLongZeroAddress(boolean longZeroAddressAllowed) {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        // When
        final var functionCall = contract.call_getHederaAccountNumAliasCall(Address.ZERO.toString());
        final var exception = assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(longZeroAddressAllowed));
        // Then
        assertThat(exception.getMessage()).isEqualTo(CONTRACT_REVERT_EXECUTED.protoName());

        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(false));
    }
}
