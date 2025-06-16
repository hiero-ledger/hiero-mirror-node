// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.config.data.JumboTransactionsConfig;
import org.hiero.mirror.common.exception.MirrorNodeException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.JumboTransaction;
import org.hiero.mirror.web3.web3j.generated.NestedCalls;
import org.junit.jupiter.api.Test;

class ContractCallJumboTransactionTest extends AbstractContractCallServiceTest {

    private static final int KILOBYTE = 1024;

    // Jumbo payload: any payload over 6 KiB and up to 128 KiB
    private static final int JUMBO_PAYLOAD = 64 * KILOBYTE;

    @Test
    void testJumboTransactionHappyPath() {
        // Given
        final var jumboPayload = new byte[JUMBO_PAYLOAD];
        final var contract = testWeb3jService.deploy(JumboTransaction::deploy);
        // When
        final var functionCall = contract.send_consumeLargeCalldata(jumboPayload);
        // Then
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void testJumboContractCreate() {
        // Given
        testWeb3jService.setUseContractCallDeploy(true);
        // When
        // 12 kb deploy
        final var contract = testWeb3jService.deploy(NestedCalls::deploy);
        // Then
        assertThat(contract.getContractAddress()).isNotNull();
    }

    @Test
    void testJumboTransactionOverMaxSize() {
        // Given
        final int maxDataSize = mirrorNodeEvmProperties
                .getVersionedConfiguration()
                .getConfigData(JumboTransactionsConfig.class)
                .ethereumMaxCallDataSize();
        final var jumboPayload = new byte[maxDataSize];
        final var contract = testWeb3jService.deploy(JumboTransaction::deploy);
        // When
        final var functionCall = contract.call_consumeLargeCalldata(jumboPayload);
        // Тhen
        if (mirrorNodeEvmProperties.isModularizedServices()) {
            var exception = assertThrows(MirrorNodeException.class, functionCall::send);
            assertThat(exception.getMessage()).isEqualTo(TRANSACTION_OVERSIZE.protoName());
        } else {
            assertThrows(MirrorEvmTransactionException.class, functionCall::send);
        }
    }
}
