// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.config.ThrottleConfiguration.RATE_LIMIT_BUCKET;

import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.OpcodeService;
import io.github.bucket4j.Bucket;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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
@ConditionalOnProperty(prefix = "hedera.mirror.web3.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    private final OpcodeService opcodeService;

    @Qualifier(RATE_LIMIT_BUCKET)
    private final Bucket rateLimitBucket;

    private final MirrorNodeEvmProperties evmProperties;

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
            @RequestParam(required = false, defaultValue = "false") boolean storage) {
        if (!rateLimitBucket.tryConsume(1)) {
            throw new RateLimitException("Requests per second rate limit exceeded.");
        }

        final var options = new OpcodeTracerOptions(stack, memory, storage);
        boolean isModularized = evmProperties.directTrafficThroughTransactionExecutionService();
        return opcodeService.processOpcodeCall(transactionIdOrHash, options, isModularized);
    }
}
