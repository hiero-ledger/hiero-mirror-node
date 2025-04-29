// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.EthCall;
import com.hedera.mirror.web3.web3j.generated.HRC632Contract;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class ContractCallHASSystemContractTest extends AbstractContractCallServiceTest {

    private static final long DEFAULT_ALLOWANCE_AMOUNT = 100L;
    private static final BigInteger APPROVE_AMOUNT = BigInteger.valueOf(10L);

    @Test
    void testHASAllowanceCall() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        domainBuilder
                .cryptoAllowance()
                .customize(a -> a.owner(ownerEntityId.getId())
                        .spender(spenderEntityId.getId())
                        .amount(DEFAULT_ALLOWANCE_AMOUNT))
                .persist();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.call_hbarAllowanceCall(ownerAddress, spenderAddress);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            final var result = functionCall.send();
            assertThat(result.component2()).isEqualTo(BigInteger.valueOf(DEFAULT_ALLOWANCE_AMOUNT));
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        }
    }

    @Test
    void testHASAllowanceCallMissingOwner() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var ownerEntityId = domainBuilder.entity().get().toEntityId();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        domainBuilder
                .cryptoAllowance()
                .customize(a -> a.owner(ownerEntityId.getId())
                        .spender(spenderEntityId.getId())
                        .amount(DEFAULT_ALLOWANCE_AMOUNT))
                .persist();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.call_hbarAllowanceCall(ownerAddress, spenderAddress);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @Test
    void testHASApproveCall() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.send_hbarApproveCall(ownerAddress, spenderAddress, APPROVE_AMOUNT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        }
    }

    @Test
    void testHASApproveFromContractCall() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var contractOwner = testWeb3jService.deployWithoutPersist(EthCall::deploy);
        final var contractRuntimeBytecode = testWeb3jService.getContractRuntime();
        final var contractOwnerEntity =
                persistContract(contractRuntimeBytecode, Address.fromHexString(contractOwner.getContractAddress()));

        final var contractOwnerAddress =
                toAddress(contractOwnerEntity.toEntityId()).toHexString();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.send_hbarApproveCall(contractOwnerAddress, spenderAddress, APPROVE_AMOUNT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            verifyEthCallAndEstimateGas(functionCall, contract);
        } else {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        }
    }

    @Test
    void testHASApproveCallMissingOwner() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var ownerEntityId = domainBuilder.entityId();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.send_hbarApproveCall(ownerAddress, spenderAddress, APPROVE_AMOUNT);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @Test
    void testHASApproveCallMissingSpender() {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var ownerEntityId = accountEntityPersist().toEntityId();
        final var spenderEntityId = domainBuilder.entityId();
        final var ownerAddress = toAddress(ownerEntityId).toHexString();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall = contract.send_hbarApproveCall(ownerAddress, spenderAddress, APPROVE_AMOUNT);

        // Then
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name());
    }

    @Test
    void testHASApproveFailsFromDelegateCall() throws Exception {
        // Given
        final var contract = testWeb3jService.deploy(HRC632Contract::deploy);
        final var contractOwner = testWeb3jService.deployWithoutPersist(EthCall::deploy);
        final var contractOwnerRuntimeBytecode = testWeb3jService.getContractRuntime();
        final var contractOwnerEntity = persistContract(
                contractOwnerRuntimeBytecode, Address.fromHexString(contractOwner.getContractAddress()));

        final var contractOwnerAddress =
                toAddress(contractOwnerEntity.toEntityId()).toHexString();
        final var spenderEntityId = accountEntityPersist().toEntityId();
        final var spenderAddress = toAddress(spenderEntityId).toHexString();

        // When
        final var functionCall =
                contract.send_hbarApproveDelegateCall(contractOwnerAddress, spenderAddress, APPROVE_AMOUNT);

        // Then
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        } else {
            assertThat(functionCall.send().getContractAddress()).isEqualTo("0x");
        }
    }

    private Entity persistContract(final byte[] runtimeBytecode, final Address contractAddress) {
        final var contractEntityId = entityIdFromEvmAddress(contractAddress);
        final var evmAddress = toEvmAddress(contractEntityId);
        final var entity = domainBuilder
                .entity(contractEntityId)
                .customize(e -> e.evmAddress(evmAddress).type(CONTRACT).balance(1500L))
                .persist();
        domainBuilder
                .contract()
                .customize(c -> c.id(contractEntityId.getId()).runtimeBytecode(runtimeBytecode))
                .persist();
        return entity;
    }
}
