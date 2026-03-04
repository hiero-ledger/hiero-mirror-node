// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.service.OpcodeService;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
@ConditionalOnProperty(prefix = "hiero.mirror.web3.opcode.tracer", name = "enabled", havingValue = "true")
class OpcodesController {

    static final String MISSING_GZIP_HEADER_MESSAGE = "Accept-Encoding: gzip header is required";

    private final OpcodeService opcodeService;
    private final ThrottleManager throttleManager;

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
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING) String acceptEncoding) {
        validateAcceptEncodingHeader(acceptEncoding);
        throttleManager.throttleOpcodeRequest();

        final var options = new OpcodeTracerOptions(stack, memory, storage);
        return opcodeService.processOpcodeCall(transactionIdOrHash, options);
    }

    /**
     * Validates if the "Accept-Encoding" header contains "gzip". This is necessary because the response
     * from this endpoint is huge and without compression this will result in big network latency.
     * @param acceptEncodingHeader the passed "Accept-Encoding" header from the request
     */
    private void validateAcceptEncodingHeader(String acceptEncodingHeader) {
        if (acceptEncodingHeader == null || !acceptEncodingHeader.toLowerCase().contains("gzip")) {
            throw HttpClientErrorException.create(
                    MISSING_GZIP_HEADER_MESSAGE,
                    HttpStatus.NOT_ACCEPTABLE,
                    HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                    null, // headers
                    null, // body
                    null // charset
                    );
        }
    }
}
