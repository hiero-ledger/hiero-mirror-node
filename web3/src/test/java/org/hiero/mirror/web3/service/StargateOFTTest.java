// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.math.BigInteger;
import org.hiero.mirror.web3.web3j.generated.StargateOFT;
import org.junit.jupiter.api.Test;

public class StargateOFTTest extends AbstractContractCallServiceTest {

    @Test
    void test() {
        final var contract = testWeb3jService.deploy(StargateOFT::deploy);
        final var sendParam = new StargateOFT.SendParam(
                BigInteger.valueOf(8L),
                treasuryAddress.getBytes(),
                BigInteger.ONE,
                BigInteger.ZERO,
                new byte[] {1, 2, 3, 4},
                new byte[] {1, 2, 3, 4},
                new byte[] {1, 2, 3, 4});
        final var messagingFee = new StargateOFT.MessagingFee(BigInteger.ONE, BigInteger.ONE);
        final var result = contract.send_send(sendParam, messagingFee, treasuryAddress, BigInteger.ONE);

        System.out.println(result);
    }
}
