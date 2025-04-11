// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.HRC632Contract;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ContractCallHASSystemContractTest extends AbstractContractCallServiceTest {

    private static final long DEFAULT_ALLOWANCE_AMOUNT = 100L;

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
}
