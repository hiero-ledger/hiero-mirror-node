// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;

import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Named
@CustomLog
public class OpcodeActionTracer extends AbstractOpcodeTracer implements ActionSidecarContentTracer {

    @Getter
    private final Map<Address, HederaSystemContract> systemContracts = new ConcurrentHashMap<>();

    @Override
    public void tracePreExecution(@NonNull final MessageFrame frame) {
        if (frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName())) {
            ContractCallContext.get().setBalanceCall(true);
        }
    }

    @Override
    public void tracePostExecution(@NonNull final MessageFrame frame, @NonNull final OperationResult operationResult) {
        final var context = ContractCallContext.get();

        final var options = context.getOpcodeTracerOptions();
        final var memory = captureMemory(frame, options);
        final var stack = captureStack(frame, options);
        final var storage = captureStorage(frame, options);

        context.addOpcodes(
                createOpcode(frame, operationResult.getGasCost(), frame.getRevertReason(), stack, memory, storage));
    }

    @Override
    public void tracePrecompileCall(
            @NonNull final MessageFrame frame, final long gasRequirement, @Nullable final Bytes output) {
        // NO-OP
    }

    @Override
    public void traceContextEnter(@NonNull final MessageFrame frame) {
        // Starting processing a newly created nested MessageFrame, we should set the remainingGas to match the newly
        // allocated gas for the new frame
        ContractCallContext.get().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceContextReEnter(@NonNull final MessageFrame frame) {
        // Returning to the parent MessageFrame, we should reset the gas to reflect the existing remaining gas of
        // the parent frame
        ContractCallContext.get().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        // Setting the initial remaining gas on the start of the initial parent frame
        ContractCallContext.get().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void sanitizeTracedActions(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        final var context = ContractCallContext.get();
        final var gasCost = context.getGasRemaining() - frame.getRemainingGas();

        final var revertReason = isCallToSystemContracts(frame, systemContracts)
                ? getRevertReasonFromContractActions(context)
                : frame.getRevertReason();

        context.addOpcodes(createOpcode(
                frame,
                gasCost,
                revertReason,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));

        context.setGasRemaining(frame.getRemainingGas());
    }

    private Opcode createOpcode(
            final MessageFrame frame,
            final long gasCost,
            final Optional<Bytes> revertReason,
            final List<Bytes> stack,
            final List<Bytes> memory,
            final Map<Bytes, Bytes> storage) {
        return Opcode.builder()
                .pc(frame.getPC())
                .op(
                        frame.getCurrentOperation() != null
                                ? frame.getCurrentOperation().getName()
                                : StringUtils.EMPTY)
                .gas(frame.getRemainingGas())
                .gasCost(gasCost)
                .depth(frame.getDepth())
                .stack(stack)
                .memory(memory)
                .storage(storage)
                .reason(revertReason.map(Bytes::toString).orElse(null))
                .build();
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            var worldUpdater = frame.getWorldUpdater();
            while (worldUpdater.parentUpdater().isPresent()) {
                worldUpdater = worldUpdater.parentUpdater().get();
            }

            if (!(worldUpdater instanceof RootProxyWorldUpdater rootProxyWorldUpdater)) {
                // The storage updates are kept only in the RootProxyWorldUpdater.
                // If we don't have one -> something unexpected happened and an attempt to
                // get the storage changes from a ProxyWorldUpdater would result in a
                // NullPointerException, so in this case just return an empty map.
                return Map.of();
            }
            final var updates = rootProxyWorldUpdater
                    .getEvmFrameState()
                    .getTxStorageUsage(true)
                    .accesses();
            return updates.stream()
                    .flatMap(storageAccesses ->
                            storageAccesses.accesses().stream()) // Properly flatten the nested structure
                    .collect(Collectors.toMap(
                            e -> Bytes.wrap(e.key().toArray()),
                            e -> Bytes.wrap(
                                    e.writtenValue() != null
                                            ? e.writtenValue().toArray()
                                            : e.value().toArray()),
                            (v1, v2) -> v1, // in case of duplicates, keep the first value
                            TreeMap::new));

        } catch (final ModificationNotAllowedException e) {
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    public void setSystemContracts(final Map<Address, HederaSystemContract> systemContracts) {
        if (systemContracts != null) {
            this.systemContracts.putAll(systemContracts);
        }
    }

    private boolean isCallToSystemContracts(
            final MessageFrame frame, final Map<Address, HederaSystemContract> systemContracts) {
        final var recipientAddress = frame.getRecipientAddress();
        return systemContracts.containsKey(recipientAddress);
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }
}
