// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hiero.mirror.common.exception.MirrorNodeException;
import org.hiero.mirror.web3.web3j.generated.JumboTransaction;
import org.junit.jupiter.api.Test;

public class ContractCallJumboTransactionTest extends AbstractContractCallServiceTest {

    private static final int KILOBYTE = 1024;

    // Jumbo payload: any payload over 6 KiB and up to 128 KiB
    private static final int JUMBO_PAYLOAD = 64 * KILOBYTE;
    private static final int OVERSIZED_JUMBO_PAYLOAD = 128 * KILOBYTE;

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
    void testJumboTransactionOverMaxSize() {
        // Given
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            return;
        }
        final var jumboPayload = new byte[OVERSIZED_JUMBO_PAYLOAD];
        final var contract = testWeb3jService.deploy(JumboTransaction::deploy);
        final var functionCall = contract.call_consumeLargeCalldata(jumboPayload);
        // When
        var exception = assertThrows(MirrorNodeException.class, functionCall::send);
        // Тhen
        assertThat(exception.getMessage()).isEqualTo(TRANSACTION_OVERSIZE.protoName());
    }
}
