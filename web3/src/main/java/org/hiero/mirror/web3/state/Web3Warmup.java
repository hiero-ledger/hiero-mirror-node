// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.service.ContractCallService;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Named
public class Web3Warmup {

    private final ContractCallService contractCallService;

    public Web3Warmup(@Qualifier("contractExecutionService") ContractCallService contractCallService) {
        this.contractCallService = contractCallService;
    }

    /**
     * Call simple read only contract function to load web3
     * resources before k8s readiness elapses
     */
    @EventListener(ApplicationReadyEvent.class)
    public void triggerResourceLoadingByFirstContractCallAfterStartup() {
        ContractExecutionParameters contractExecutionParameters = getContractExecutionParameters();
        contractCallService.callContract(contractExecutionParameters);
    }

    /**
     * Prepare contract execution parameters to call simple read-only function
     * (isToken() of HTS precompile in this case) using the system treasury account as sender
     */
    protected ContractExecutionParameters getContractExecutionParameters() {
        final Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000167");
        final Bytes IS_TOKEN_CALL_DATA =
                Bytes.fromHexString("0x997b63220000000000000000000000000000000000000000000000000000000000000000");
        final Address SENDER_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000002");

        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(IS_TOKEN_CALL_DATA)
                .callType(CallType.ETH_CALL)
                .gas(15_000_000L)
                .isEstimate(false)
                .isModularized(true)
                .isStatic(true)
                .receiver(HTS_PRECOMPILE_ADDRESS)
                .sender(new HederaEvmAccount(SENDER_ADDRESS))
                .build();
    }
}
