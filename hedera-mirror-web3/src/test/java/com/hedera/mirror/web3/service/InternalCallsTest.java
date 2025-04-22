// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.generated.InternalCaller;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InternalCallsTest extends AbstractContractCallServiceTest {

    private String nonExistingAddress;

    @BeforeAll
    void configure() {
        var nonExistingEntityId = domainBuilder.entityId();
        this.nonExistingAddress = toAddress(nonExistingEntityId).toHexString();
    }

    @Test
    void callToNonExistingContract() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result = contract.call_callNonExisting(nonExistingAddress).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void callToNonExistingFunction() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result =
                contract.call_callNonExisting(contract.getContractAddress()).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void callToNonExistingFunctionWithValue() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result =
                contract.call_callWithValueTo(contract.getContractAddress()).send();

        assertThat(Bytes.wrap(result.component2())).isEqualTo(Bytes.EMPTY);
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void sendToNonExistingAccount() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var result = contract.call_sendTo(nonExistingAddress).send();

        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            assertThat(result).isEqualTo(Boolean.TRUE);
        } else {
            // In the mod code, there is a check if the address is an alias and in this case it is not.
            assertThat(result).isEqualTo(Boolean.FALSE);
        }
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void transferToNonExistingAccount() throws Exception {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        meterRegistry.clear();
        final var functionCall = contract.send_transferTo(nonExistingAddress);
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            functionCall.send();
            assertThat(testWeb3jService.getTransactionResult()).isEqualTo(HEX_PREFIX);
        } else {
            // In the mod code, there is a check if the address is an alias and in this case it is not.
            assertThatThrownBy(functionCall::send)
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(CONTRACT_REVERT_EXECUTED.name());
        }
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    private void assertGasLimit(final long gasLimit) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> ETH_CALL.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(gasLimit);
    }
}
