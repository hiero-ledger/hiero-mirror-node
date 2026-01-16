// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hiero.mirror.web3.web3j.generated.TestContractDeployer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ContractCallServiceDeployerTest extends AbstractContractCallServiceTest {

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void testDeployMultipleContracts() throws Exception {
        final var contract = testWeb3jService.deploy(TestContractDeployer::deploy);
        final var result = contract.send_deployTestContracts().send();
        assertThat(result).isNotNull();
    }
}
