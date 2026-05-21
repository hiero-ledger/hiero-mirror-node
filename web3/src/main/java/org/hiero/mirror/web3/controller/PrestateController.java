// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.PrestateResponse;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.service.PrestateService;
import org.hiero.mirror.web3.service.model.PrestateRequest;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@CustomLog
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/results")
public class PrestateController {

    private final PrestateService prestateService;
    private final ThrottleManager throttleManager;
    private final TracerProperties tracerProperties;

    /**
     * <p>
     * Returns a result containing information about all accounts needed for executing a transaction with some basic
     * fields about each account. The endpoint supports a {@code diff} mode where it also returns the delta information
     * about how accounts have changed during the execution.
     * </p>
     * <p>
     * Note that to provide the output, the transaction needs to be re-executed on the EVM several times and this
     * might slow down execution, especially if {@code storage} is enabled.
     * </p>
     *
     * @param transactionIdOrHash The transaction ID or hash
     * @param diff               Include information for account changes after the transaction execution
     * @param code               Include contract bytecode information
     * @param storage             Include storage information
     * @return {@link PrestateResponse} containing the result of the transaction execution
     */
    @GetMapping(value = "/{transactionIdOrHash}/prestate")
    PrestateResponse getContractPrestate(
            @PathVariable TransactionIdOrHashParameter transactionIdOrHash,
            @RequestParam(required = false, defaultValue = "false") boolean diff,
            @RequestParam(required = false, defaultValue = "false") boolean code,
            @RequestParam(required = false, defaultValue = "false") boolean storage) {
        if (tracerProperties.isEnabled()) {
            throttleManager.throttlePrestateRequest();

            final var request = new PrestateRequest(transactionIdOrHash, diff, code, storage);
            return prestateService.processPrestateCall(request);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
