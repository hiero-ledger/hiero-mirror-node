// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.OpcodeService;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
@ConditionalOnProperty(prefix = "hiero.mirror.web3.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    private final OpcodeService opcodeService;
    private final ThrottleManager throttleManager;
    private final EvmProperties evmProperties;

    /**
     * <p>
     * Returns a result containing detailed information for the transaction execution, including all values from the
     * {@code stack}, {@code memory} and {@code storage} and the entire trace of opcodes that were executed during the
     * replay.
     * </p>
     * <p>
     * Note that to provide the output, the transaction needs to be re-executed on the EVM, which may take a significant
     * amount of time to complete if stack and memory information is requested.
     * </p>
     *
     * @param transactionIdOrHash The transaction ID or hash
     * @param stack               Include stack information
     * @param memory              Include memory information
     * @param storage             Include storage information
     * @return {@link OpcodesResponse} containing the result of the transaction execution
     */
    @GetMapping(value = "/{transactionIdOrHash}/opcodes")
    OpcodesResponse getContractOpcodes(
            @PathVariable TransactionIdOrHashParameter transactionIdOrHash,
            @RequestParam(required = false, defaultValue = "true") boolean stack,
            @RequestParam(required = false, defaultValue = "false") boolean memory,
            @RequestParam(required = false, defaultValue = "false") boolean storage,
            HttpServletResponse response) {
        throttleManager.throttleOpcodeRequest();

        final var options = new OpcodeTracerOptions(stack, memory, storage);
        return opcodeService.processOpcodeCall(transactionIdOrHash, options);
    }
}
