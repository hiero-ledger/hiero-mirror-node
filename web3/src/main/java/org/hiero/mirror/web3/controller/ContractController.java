// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.BlockOverrideAdvance;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.ThrottleException;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.ContractDebugService;
import org.hiero.mirror.web3.service.ContractExecutionService;
import org.hiero.mirror.web3.service.RecordFileService;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.TraceBundle;
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

    private final ActionsProperties actionsProperties;
    private final ContractDebugService contractDebugService;
    private final ContractExecutionService contractExecutionService;
    private final EvmProperties evmProperties;
    private final RecordFileService recordFileService;
    private final ThrottleManager throttleManager;
    private final TransactionRepository transactionRepository;
    private final Web3Properties web3Properties;

    @PostMapping(value = "/call")
    ContractCallResponse call(@RequestBody @Valid ContractCallRequest request) {
        validateContractMaxGasLimit(request);
        validateBlockOverrides(request.getBlockOverride());
        final var params = constructServiceParameters(request);
        validateStateOverrides(params.getStateOverrides());

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
    List<ActionResponse> actions(
            @RequestBody @Valid ActionTraceRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        if (!web3Properties.getApi(ACTIONS).isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        }
        validateTraceCallMany(request.getBundles());

        GzipEncoding.validate(acceptEncoding);
        for (final var bundle : request.getBundles()) {
            validateBlockOverrides(bundle.getBlockOverride());
            rejectNestedStateOverrides(bundle.getStateOverrides(), "bundle");
            for (final var transaction : bundle.getTransactions()) {
                rejectEstimate(transaction);
                rejectTransactionBlockOverride(transaction);
                rejectNestedStateOverrides(transaction.getStateOverrides(), "transaction");
                validateContractMaxGasLimit(transaction.getGas());
            }
        }
        validateConsecutiveBlockOverrides(request.getBundles());

        final var traceCall = toTraceCall(request);
        validateTrace(traceCall);
        throttleActions(request);
        return contractDebugService.processTraceCall(traceCall);
    }

    /**
     * Charges each nested transaction with the same limits eth_call uses. A later rejection returns the request tokens
     * and the gas already taken.
     */
    private void throttleActions(final ActionTraceRequest request) {
        final var throttled = new ArrayList<ContractCallRequest>();
        try {
            for (final var bundle : request.getBundles()) {
                for (final var transaction : bundle.getTransactions()) {
                    throttleManager.throttle(transaction);
                    throttled.add(transaction);
                }
            }
        } catch (final ThrottleException exception) {
            for (final var transaction : throttled) {
                throttleManager.restoreRequest();
                throttleManager.restore(transaction.getGas());
            }
            throw exception;
        }
    }

    private TraceCall toTraceCall(final ActionTraceRequest request) {
        final var bundles = new ArrayList<TraceBundle>(request.getBundles().size());
        final var block = request.getBlock();
        for (final var bundle : request.getBundles()) {
            final var transactions = new ArrayList<ContractExecutionParameters>(
                    bundle.getTransactions().size());
            for (final var transaction : bundle.getTransactions()) {
                transactions.add(constructServiceParameters(transaction, block));
            }
            bundles.add(TraceBundle.builder()
                    .blockOverride(bundle.getBlockOverride())
                    .transactions(transactions)
                    .build());
        }
        final var transactionIndex = request.getTransactionIndex();
        return TraceCall.builder()
                .block(request.getBlock())
                .bundles(bundles)
                .onlyTopCall(request.isOnlyTopCall())
                .stateOverrides(request.getStateOverrides())
                .timeout(resolveTimeout(request.getTimeout()))
                .transactionIndex(transactionIndex == null ? -1 : transactionIndex)
                .build();
    }

    private Duration resolveTimeout(final @Nullable String timeout) {
        final var apiTimeout = web3Properties.getApi(ACTIONS).getResponse().getTimeout();
        final var maxTimeout = actionsProperties.getMaxTimeout();
        final var effective = apiTimeout.compareTo(maxTimeout) > 0 ? maxTimeout : apiTimeout;
        if (timeout == null || timeout.isBlank()) {
            return effective;
        }
        try {
            final var parsed = DurationStyle.detectAndParse(timeout.trim());
            if (parsed.compareTo(Duration.ofMillis(1)) < 0) {
                throw new InvalidParametersException("Invalid timeout: " + timeout);
            }
            return parsed.compareTo(effective) < 0 ? parsed : effective;
        } catch (IllegalArgumentException _) {
            throw new InvalidParametersException("Invalid timeout: " + timeout);
        }
    }

    private ContractExecutionParameters constructServiceParameters(final ContractCallRequest request) {
        return constructServiceParameters(request, request.getBlock());
    }

    private ContractExecutionParameters constructServiceParameters(
            final ContractCallRequest request, final BlockType block) {
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
                .blockOverride(request.getBlockOverride())
                .callData(hexToBytes(data))
                .callType(callType)
                .gas(request.getGas())
                .gasPrice(request.getGasPrice())
                .isEstimate(request.isEstimate())
                .isStatic(false)
                .receiver(receiver)
                .sender(fromAddress)
                .stateOverrides(request.getStateOverrides())
                .value(request.getValue())
                .build();
    }

    /**
     * Later bundles must not move the EVM block backward. A bundle without {@code block_override} inherits the previous
     * context and advances a set number by 1 and a set timestamp by 2 seconds. An explicit override that omits the block
     * number keeps that number and increases it by 1. An explicit override that omits the timestamp keeps that
     * timestamp and increases it by 2 seconds. A set number or timestamp is rejected when it is older than the
     * inherited value.
     */
    private static void validateConsecutiveBlockOverrides(final List<ActionTraceBundle> bundles) {
        Long previousNumber = null;
        Long previousTime = null;
        for (int bundleIndex = 0; bundleIndex < bundles.size(); bundleIndex++) {
            final var next = BlockOverrideAdvance.next(
                    previousNumber, previousTime, bundles.get(bundleIndex).getBlockOverride(), bundleIndex);
            previousNumber = next.number();
            previousTime = next.timeSeconds();
        }
    }

    private void validateBlockOverrides(final @Nullable BlockOverride blockOverride) {
        if (blockOverride != null && !web3Properties.isEnableBlockOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Block overrides are not supported.");
        }
    }

    private void validateStateOverrides(final List<StateOverride> stateOverrides) {
        if (!stateOverrides.isEmpty()) {
            requireStateOverridesEnabled();
        }
    }

    /**
     * Rejects state overrides when they are disabled, a bad {@code transaction_index}, or a block cursor older than the
     * bound record file before any rate token is taken. The first bundle's omitted header fields come from that record
     * file's block number and consensus start.
     */
    private void validateTrace(final TraceCall traceCall) {
        validateStateOverrides(traceCall.getStateOverrides());
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

    private void requireStateOverridesEnabled() {
        if (!web3Properties.isEnableStateOverrides()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State overrides are not supported.");
        }
    }

    private static void rejectEstimate(final ContractCallRequest transaction) {
        if (transaction.isEstimate()) {
            throw new InvalidParametersException("Estimate is not supported for tracing.");
        }
    }

    /** Trace state overrides are allowed only at the request level, not on a bundle or a transaction. */
    private static void rejectNestedStateOverrides(final List<StateOverride> stateOverrides, final String level) {
        if (!stateOverrides.isEmpty()) {
            throw new InvalidParametersException(
                    "state_overrides must be set on the request, not on a %s.".formatted(level));
        }
    }

    /** A trace block override is allowed only on a bundle, so every transaction in the bundle shares it. */
    private static void rejectTransactionBlockOverride(final ContractCallRequest transaction) {
        if (transaction.getBlockOverride() != null) {
            throw new InvalidParametersException("block_override must be set on the bundle, not on a transaction.");
        }
    }

    private void validateContractMaxGasLimit(final ContractCallRequest request) {
        validateContractMaxGasLimit(request.getGas());
    }

    private void validateContractMaxGasLimit(final long gas) {
        if (gas > evmProperties.getMaxGasLimit()) {
            throw new InvalidParametersException(
                    "gas field must be less than or equal to %d".formatted(evmProperties.getMaxGasLimit()));
        }
    }
}
