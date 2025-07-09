// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

@Named
@CustomLog
public class OpcodeActionTracer extends AbstractOpcodeTracer implements ActionSidecarContentTracer {

    @Override
    public void tracePostExecution(@NonNull final MessageFrame frame, @NonNull final OperationResult operationResult) {
        final var context = ContractCallContext.get();

        final var options = context.getOpcodeTracerOptions();
        final var memory = captureMemory(frame, options);
        final var stack = captureStack(frame, options);
        final var storage = captureStorage(frame, options);
        final var opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(frame.getCurrentOperation().getName())
                .gas(frame.getRemainingGas())
                .gasCost(operationResult.getGasCost())
                .depth(frame.getDepth())
                .stack(stack)
                .memory(memory)
                .storage(storage)
                .reason(frame.getRevertReason().map(Bytes::toString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    @Override
    public void tracePrecompileCall(
            @NonNull final MessageFrame frame, final long gasRequirement, @Nullable final Bytes output) {
        final var context = ContractCallContext.get();
        final var revertReason = isCallToSystemContracts(frame, systemContracts)
                ? getRevertReasonFromContractActions(context)
                : frame.getRevertReason();

        final var opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(
                        frame.getCurrentOperation() != null
                                ? frame.getCurrentOperation().getName()
                                : StringUtils.EMPTY)
                .gas(frame.getRemainingGas())
                .gasCost(output != null && !output.isEmpty() ? gasRequirement : 0L)
                .depth(frame.getDepth())
                .stack(Collections.emptyList())
                .memory(Collections.emptyList())
                .storage(Collections.emptyMap())
                .reason(revertReason.map(Bytes::toHexString).orElse(null))
                .build();
        context.addOpcodes(opcode);
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            final var updates = ((ProxyWorldUpdater) frame.getWorldUpdater()).pendingStorageUpdates();
            return updates.stream()
                    .flatMap(storageAccesses ->
                            storageAccesses.accesses().stream()) // Properly flatten the nested structure
                    .collect(Collectors.toMap(
                            e -> Bytes.wrap(e.key().toArray()),
                            e -> Bytes.wrap(e.value().toArray()),
                            (v1, v2) -> v1, // in case of duplicates, keep the first value
                            TreeMap::new));

        } catch (final ModificationNotAllowedException e) {
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void sanitizeTracedActions(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        // NO-OP
    }

    @Override
    public ContractActions contractActions() {
        return null;
    }
}
