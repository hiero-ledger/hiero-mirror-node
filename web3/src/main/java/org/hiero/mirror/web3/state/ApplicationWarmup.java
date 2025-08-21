// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.service.ContractCallService;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@CustomLog
@Named
@RequiredArgsConstructor
final class ApplicationWarmup {

    @Qualifier("contractExecutionService")
    private final ContractCallService contractCallService;

    private final MirrorNodeEvmProperties evmProperties;

    /**
     * Call simple read only contract function to load web3
     * resources before k8s readiness elapses. Method is async to
     * run it in a separate thread and not block the main one by
     * potentially waiting for the contract call to complete.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCall() {
        if (evmProperties.isModularizedServices()) {
            final var evmVersions = evmProperties.getEvmVersions();
            evmVersions.descendingMap().entrySet().stream().forEach(entry -> {
                final var blockType =
                        new BlockType(String.valueOf(entry.getValue().minor()), entry.getKey());
                try {
                    ContractExecutionParameters contractExecutionParameters = getContractExecutionParameters(blockType);
                    contractCallService.callContract(contractExecutionParameters);
                } catch (Exception e) {
                    log.warn("Warmup call for block {} failed: {}", blockType, e.getMessage());
                }
            });
        }
    }

    /**
     * Prepare contract execution parameters to call simple read-only function
     * (isToken() of HTS precompile in this case) using the system treasury account as sender
     */
    private ContractExecutionParameters getContractExecutionParameters(BlockType blockType) {
        final Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString("0x0000000000000000000000000000000000000167");
        final Bytes IS_TOKEN_CALL_DATA =
                Bytes.fromHexString("0x997b63220000000000000000000000000000000000000000000000000000000000000000");

        return ContractExecutionParameters.builder()
                .block(blockType)
                .callData(IS_TOKEN_CALL_DATA)
                .callType(CallType.ETH_CALL)
                .gas(1_000_000L)
                .isEstimate(false)
                .isModularized(true)
                .isStatic(true)
                .receiver(HTS_PRECOMPILE_ADDRESS)
                .sender(new HederaEvmAccount(Address.fromHexString("0x0000000000000000000000000000000000000002")))
                .build();
    }
}
