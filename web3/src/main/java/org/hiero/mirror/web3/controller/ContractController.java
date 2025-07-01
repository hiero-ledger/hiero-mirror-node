// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.utils.Constants.MODULARIZED_HEADER;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@RestController
class ContractController {

    private final ContractExecutionService contractExecutionService;
    private final MirrorNodeEvmProperties evmProperties;
    private final ThrottleManager throttleManager;

    @PostMapping(value = "/call")
    ContractCallResponse call(
            @RequestBody @Valid ContractCallRequest request,
            @RequestHeader(value = MODULARIZED_HEADER, required = false) String isModularizedHeader,
            HttpServletResponse response) {
        try {
            throttleManager.throttle(request);
            validateContractMaxGasLimit(request);

            final var params = constructServiceParameters(request, isModularizedHeader);
            response.addHeader(MODULARIZED_HEADER, String.valueOf(params.isModularized()));
            final var result = contractExecutionService.processCall(params);
            return new ContractCallResponse(result);
        } catch (InvalidParametersException e) {
            // The validation failed, but no processing occurred so restore the consumed tokens.
            throttleManager.restore(request.getGas());
            throw e;
        }
    }

    private ContractExecutionParameters constructServiceParameters(
            ContractCallRequest request, final String isModularizedHeader) {
        final var fromAddress = request.getFrom() != null ? Address.fromHexString(request.getFrom()) : Address.ZERO;
        final var sender = new HederaEvmAccount(fromAddress);

        Address receiver;

        /*In case of an empty "to" field, we set a default value of the zero address
        to avoid any potential NullPointerExceptions throughout the process.*/
        if (request.getTo() == null || request.getTo().isEmpty()) {
            receiver = Address.ZERO;
        } else {
            receiver = Address.fromHexString(request.getTo());
        }
        Bytes data;
        try {
            data = request.getData() != null ? Bytes.fromHexString(request.getData()) : Bytes.EMPTY;
        } catch (Exception e) {
            throw new InvalidParametersException(
                    "data field '%s' contains invalid odd length characters".formatted(request.getData()));
        }
        final var isStaticCall = false;
        final var callType = request.isEstimate() ? ETH_ESTIMATE_GAS : ETH_CALL;
        final var block = request.getBlock();

        boolean isModularized = evmProperties.directTrafficThroughTransactionExecutionService();

        // Temporary workaround to ensure modularized services are fully available when enabled.
        // This prevents flakiness in acceptance tests, as directTrafficThroughTransactionExecutionService()
        // can distribute traffic between the old and new logic.
        if (isModularizedHeader != null && evmProperties.isModularizedServices()) {
            isModularized = Boolean.parseBoolean(isModularizedHeader);
        }

        if (request.getModularized() != null) {
            isModularized = request.getModularized();
        }

        return ContractExecutionParameters.builder()
                .block(block)
                .callData(data)
                .callType(callType)
                .gas(request.getGas())
                .isEstimate(request.isEstimate())
                .isModularized(isModularized)
                .isStatic(isStaticCall)
                .receiver(receiver)
                .sender(sender)
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
