// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.web3.web3j.generated.StargateOFT;
import org.junit.jupiter.api.Test;

public class StargateOFTTest extends AbstractContractCallServiceTest {

    @Test
    void test() {
        final var contract = testWeb3jService.deploy(StargateOFT::deploy);
    }
}
