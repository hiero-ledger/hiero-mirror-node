// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.validation.Valid;
import java.time.Duration;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.GzipEncoding;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@RestController
class ContractController {

    private final ContractExecutionService contractExecutionService;
    private final ContractDebugService contractDebugService;
    private final EvmProperties evmProperties;
    private final TracerProperties tracerProperties;
    private final Web3Properties web3Properties;
    private final ThrottleManager throttleManager;

    @PostMapping(value = "/call")
    ContractCallResponse call(@RequestBody @Valid ContractCallRequest request) {
        validateContractMaxGasLimit(request);
        final var params = constructServiceParameters(request);

        if (!params.getStateOverrides().isEmpty() && !web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }

        throttleManager.throttle(request);
        try {
            final var result = contractExecutionService.processCall(params);
            return new ContractCallResponse(result);
        } catch (IllegalArgumentException | InvalidParametersException e) {
            // Processing did not complete, so restore the consumed tokens.
            throttleManager.restore(request.getGas());
            throw e;
        }
    }

    @PostMapping(value = "/call/actions")
    ActionResponse actions(
            @RequestBody @Valid ActionTraceRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        if (!web3Properties.isApiEnabled(ACTIONS)) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }

        GzipEncoding.require(acceptEncoding);
        validateActionsRequest(request);
        final var resolvedTimeout = resolveTimeout(request.getTimeout());
        validateContractMaxGasLimit(request);

        if (!request.getStateOverrides().isEmpty() && !web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }

        throttleManager.throttleTraceRequest(request);
        try {
            final var params = constructServiceParameters(request);
            return contractDebugService.processTraceCall(
                    new TraceRequest(params, request.isOnlyTopCall(), resolvedTimeout));
        } catch (IllegalArgumentException | InvalidParametersException e) {
            throttleManager.restore(request.getGas());
            throw e;
        }
    }

    private void validateActionsRequest(final ActionTraceRequest request) {
        if (request.isEstimate()) {
            throw new InvalidParametersException("estimate is not supported for action trace calls");
        }
    }

    private Duration resolveTimeout(final @Nullable String timeout) {
        final var apiTimeout = web3Properties.getRequestTimeout(ACTIONS);
        final var maxTimeout = tracerProperties.getMaxTimeout();
        var effective = apiTimeout.compareTo(maxTimeout) > 0 ? maxTimeout : apiTimeout;
        if (timeout == null || timeout.isBlank()) {
            return effective;
        }
        try {
            final var parsed = DurationStyle.detectAndParse(timeout.trim());
            if (parsed.isNegative() || parsed.isZero()) {
                throw new InvalidParametersException("Invalid timeout: " + timeout);
            }
            return parsed.compareTo(effective) < 0 ? parsed : effective;
        } catch (IllegalArgumentException e) {
            throw new InvalidParametersException("Invalid timeout: " + timeout);
        }
    }

    private ContractExecutionParameters constructServiceParameters(ContractCallRequest request) {
        final var fromAddress = request.getFrom() != null ? Address.fromHexString(request.getFrom()) : Address.ZERO;

        Address receiver;

        /*In case of an empty "to" field, we set a default value of the zero address
        to avoid any potential NullPointerExceptions throughout the process.*/
        if (request.getTo() == null || request.getTo().isEmpty()) {
            receiver = Address.ZERO;
        } else {
            receiver = Address.fromHexString(request.getTo());
        }

        String data;
        try {
            data = request.getData() != null ? request.getData() : HEX_PREFIX;
        } catch (final Exception e) {
            throw new InvalidParametersException(
                    "data field '%s' contains invalid odd length characters".formatted(request.getData()));
        }

        final var isStaticCall = false;
        final var callType = request.isEstimate() ? ETH_ESTIMATE_GAS : ETH_CALL;
        final var block = request.getBlock();

        return ContractExecutionParameters.builder()
                .block(block)
                .callData(hexToBytes(data))
                .callType(callType)
                .gas(request.getGas())
                .gasPrice(request.getGasPrice())
                .isEstimate(request.isEstimate())
                .isStatic(isStaticCall)
                .receiver(receiver)
                .sender(fromAddress)
                .stateOverrides(request.getStateOverrides())
                .value(request.getValue())
                .build();
    }

    private void validateContractMaxGasLimit(ContractCallRequest request) {
        if (request.getGas() > evmProperties.getMaxGasLimit()) {
            throw new InvalidParametersException(
                    "gas field must be less than or equal to %d".formatted(evmProperties.getMaxGasLimit()));
        }
    }
}
