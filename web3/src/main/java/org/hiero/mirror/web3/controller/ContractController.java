// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.ContractSimulateService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.SimulateResponse;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@RestController
class ContractController {

    private final ContractExecutionService contractExecutionService;
    private final ContractSimulateService contractSimulateService;
    private final EvmProperties evmProperties;
    private final Web3Properties web3Properties;
    private final ThrottleManager throttleManager;

    @PostMapping(value = "/call")
    ContractCallResponse call(@RequestBody @Valid ContractCallRequest request, HttpServletResponse response) {
        try {
            throttleManager.throttle(request);
            validateContractMaxGasLimit(request);

            final var params = constructServiceParameters(request);

            if (!params.getStateOverrides().isEmpty() && !web3Properties.isEnableStateOverrides()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
            }

            final var result = contractExecutionService.processCall(params);
            return new ContractCallResponse(result);
        } catch (InvalidParametersException e) {
            // The validation failed, but no processing occurred so restore the consumed tokens.
            throttleManager.restore(request.getGas());
            throw e;
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

    @PostMapping(value = "/simulate")
    SimulateResponse simulate(@RequestBody @Valid SimulateRequest request) {
        // Ordered before throttling: tokens consumed for a rejected request are never restored.
        if (!web3Properties.isEnableSimulate()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulate is not supported.");
        }
        if (hasStateOverrides(request) && !web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }
        validateSimulateMaxGasLimit(request);

        throttleManager.throttleSimulateRequest(request.totalGas());
        return contractSimulateService.simulate(request);
    }

    private boolean hasStateOverrides(SimulateRequest request) {
        return request.getBlockStateCalls().stream()
                .anyMatch(blockCalls -> !blockCalls.getStateOverrides().isEmpty());
    }

    private void validateSimulateMaxGasLimit(SimulateRequest request) {
        for (final var blockCalls : request.getBlockStateCalls()) {
            for (final var call : blockCalls.getCalls()) {
                if (call.getGas() > evmProperties.getMaxGasLimit()) {
                    throw new InvalidParametersException(
                            "gas field must be less than or equal to %d".formatted(evmProperties.getMaxGasLimit()));
                }
            }
        }
    }
}
