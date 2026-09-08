// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.validation.Valid;
import java.time.Duration;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.TracerResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.GzipEncoding;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hyperledger.besu.datatypes.Address;
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

        throttleManager.throttleTraceRequest();

        final var result = contractExecutionService.processCall(params);
        return new ContractCallResponse(result);
    }

    @PostMapping(value = "/call/debug")
    TracerResponse trace(
            @RequestBody @Valid ContractCallRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) String acceptEncoding) {
        if (!tracerProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }

        GzipEncoding.require(acceptEncoding);
        final var timeout = validateTraceRequest(request);
        validateContractMaxGasLimit(request);

        if (!request.getStateOverrides().isEmpty() && !web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }

        throttleManager.throttleTraceRequest();

        try {
            final var params = constructServiceParameters(request);
            final var tracerConfig = request.getTracerConfig();
            final var onlyTopCall = tracerConfig != null && tracerConfig.onlyTopCall();
            final var traceRequest = new TraceRequest(params, onlyTopCall, timeout);

            return contractDebugService.processTraceCall(traceRequest);
        } catch (InvalidParametersException e) {
            // The validation failed, but no processing occurred so restore the consumed tokens.
            throttleManager.restore(request.getGas());
            throw e;
        }
    }

    private Duration validateTraceRequest(final ContractCallRequest request) {
        if (request.isEstimate()) {
            throw new InvalidParametersException("estimate is not supported for debug trace calls");
        }
        final var tracerConfig = request.getTracerConfig();
        if (tracerConfig == null) {
            return null;
        }
        if (tracerConfig.effectiveTracerType() != TracerType.ACTION) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Tracer is not implemented");
        }
        if (tracerConfig.code()
                || tracerConfig.diff()
                || tracerConfig.memory()
                || tracerConfig.stack()
                || tracerConfig.storage()) {
            throw new InvalidParametersException(
                    "code, diff, memory, stack, and storage are not applicable to callTracer");
        }
        try {
            return tracerConfig.parsedTimeout();
        } catch (IllegalArgumentException e) {
            throw new InvalidParametersException("Invalid timeout: " + tracerConfig.timeout());
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
