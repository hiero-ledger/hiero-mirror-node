// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.viewmodel.ActionTraceBundle;
import org.hiero.mirror.web3.viewmodel.ActionTraceRequest;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.ContractCallResponse;
import org.hiero.mirror.web3.viewmodel.StateOverride;
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

    private final ActionsProperties actionsProperties;
    private final ContractDebugService contractDebugService;
    private final ContractExecutionService contractExecutionService;
    private final EvmProperties evmProperties;
    private final ThrottleManager throttleManager;
    private final Web3Properties web3Properties;

    @PostMapping(value = "/call/actions")
    List<ActionResponse> actions(
            @RequestBody @Valid final ActionTraceRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        if (!web3Properties.getApi(ACTIONS).isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }
        validateTraceCallMany(request.getBundles());
        GzipEncoding.validate(acceptEncoding);
        validateStateOverrides(request.getStateOverrides());

        final var traceCall = toTraceCall(request);
        throttleActions(request);
        return ContractCallContext.run(ctx -> {
            try {
                return contractDebugService.processTraceCall(traceCall, ctx);
            } finally {
                throttleManager.restore(ctx.getRefundableGas());
            }
        });
    }

    @PostMapping(value = "/call")
    ContractCallResponse call(@RequestBody @Valid final ContractCallRequest request) {
        validateContractMaxGasLimit(request);
        validateBlockOverrides(request.getBlockOverride());
        final var params = constructServiceParameters(request, request.getBlock(), request.getBlockOverride());
        validateStateOverrides(params.getStateOverrides());

        throttleManager.throttle(request);
        return ContractCallContext.run(ctx -> {
            // Until the EVM runs, none of the reserved gas has been used, so all of it is refundable.
            ctx.setRefundableGas(request.getGas());
            try {
                return new ContractCallResponse(contractExecutionService.processCall(params, ctx));
            } finally {
                throttleManager.restore(ctx.getRefundableGas());
            }
        });
    }

    private ContractExecutionParameters constructServiceParameters(
            final ContractCallRequest request, final BlockType block, final BlockOverride blockOverride) {
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

        final var callType = request.isEstimate() ? ETH_ESTIMATE_GAS : ETH_CALL;
        return ContractExecutionParameters.builder()
                .block(block)
                .blockOverride(blockOverride)
                .callData(hexToBytes(data))
                .callType(callType)
                .gas(request.getGas())
                .gasPrice(request.getGasPrice())
                .isEstimate(request.isEstimate())
                .receiver(receiver)
                .sender(fromAddress)
                .stateOverrides(request.getStateOverrides())
                .value(request.getValue())
                .build();
    }

    /** Trace state overrides are allowed only at the request level, not on a bundle or a transaction. */
    private static void rejectNestedStateOverrides(final List<StateOverride> stateOverrides, final String level) {
        if (!stateOverrides.isEmpty()) {
            throw new InvalidParametersException(
                    "state_overrides must be set on the request, not on a %s.".formatted(level));
        }
    }

    /**
     * Charges each transaction with the same limits eth_call uses. A rejection returns the request tokens and the gas
     * taken by the transactions before it.
     */
    private void throttleActions(final ActionTraceRequest request) {
        int throttledCount = 0;
        long throttledGas = 0L;
        try {
            for (final var bundle : request.getBundles()) {
                for (final var transaction : bundle.getTransactions()) {
                    throttleManager.throttle(transaction);
                    throttledCount++;
                    throttledGas += transaction.getGas();
                }
            }
        } catch (final ThrottleException exception) {
            for (int i = 0; i < throttledCount; i++) {
                throttleManager.restoreRequest();
            }
            if (throttledGas > 0) {
                throttleManager.restore(throttledGas);
            }
            throw exception;
        }
    }

    /**
     * Validates every transaction and flattens the bundles into one list in request order. Each transaction carries the
     * block override of its bundle.
     */
    private TraceCall toTraceCall(final ActionTraceRequest request) {
        final var block = request.getBlock();
        final var transactions = new ArrayList<ContractExecutionParameters>(MAX_TRACE_TRANSACTIONS);
        for (final var bundle : request.getBundles()) {
            final var blockOverride = bundle.getBlockOverride();
            validateBlockOverrides(blockOverride);
            rejectNestedStateOverrides(bundle.getStateOverrides(), "bundle");
            for (final var transaction : bundle.getTransactions()) {
                validateTraceTransaction(transaction);
                transactions.add(constructServiceParameters(transaction, block, blockOverride));
            }
        }
        return TraceCall.builder()
                .block(block)
                .onlyTopCall(request.isOnlyTopCall())
                .stateOverrides(request.getStateOverrides())
                .transactionIndex(request.getTransactionIndex())
                .transactions(transactions)
                .build();
    }

    private void validateBlockOverrides(final BlockOverride blockOverride) {
        if (!blockOverride.isEmpty() && !web3Properties.isEnableBlockOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block overrides are not supported.");
        }
    }

    private void validateContractMaxGasLimit(final ContractCallRequest request) {
        if (request.getGas() > evmProperties.getMaxGasLimit()) {
            throw new InvalidParametersException(
                    "gas field must be less than or equal to %d".formatted(evmProperties.getMaxGasLimit()));
        }
    }

    private void validateStateOverrides(final List<StateOverride> stateOverrides) {
        if (!stateOverrides.isEmpty() && !web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }
    }

    /** More than one bundle, or more than one transaction in a bundle, is a {@code debug_traceCallMany} request. */
    private void validateTraceCallMany(final List<ActionTraceBundle> bundles) {
        if (actionsProperties.isTraceMultipleTransactions()) {
            return;
        }
        if (bundles.size() > 1 || bundles.getFirst().getTransactions().size() > 1) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "debug_traceCallMany is not supported.");
        }
    }

    /**
     * A trace runs every transaction in the request block, with the request state overrides and the bundle block
     * override, so a transaction cannot set its own overrides. Estimation is not traced.
     */
    private void validateTraceTransaction(final ContractCallRequest transaction) {
        if (transaction.isEstimate()) {
            throw new InvalidParametersException("Estimate is not supported for tracing.");
        }
        if (!transaction.getBlockOverride().isEmpty()) {
            throw new InvalidParametersException("block_override must be set on the bundle, not on a transaction.");
        }
        rejectNestedStateOverrides(transaction.getStateOverrides(), "transaction");
        validateContractMaxGasLimit(transaction);
    }
}
