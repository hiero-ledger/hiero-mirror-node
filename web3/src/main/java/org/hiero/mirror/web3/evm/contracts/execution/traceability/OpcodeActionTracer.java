// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;

import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.datatypes.Address;
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

        // Reset the balance call flag after BALANCE opcode completes
        if (frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName())) {
            context.setBalanceCall(false);
        }

        final var options = context.getOpcodeContext();
        final var memory = captureMemory(frame, options);
        final var stack = captureStack(frame, options);
        final var storage = captureStorage(frame, options, context);

        final var revertReasonBytes = frame.getRevertReason().orElse(null);
        context.getOpcodeContext()
                .addOpcodes(createOpcode(
                        frame, operationResult.getGasCost(), revertReasonBytes, null, stack, memory, storage));
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
        ContractCallContext.get().getOpcodeContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceContextReEnter(@NonNull final MessageFrame frame) {
        // Returning to the parent MessageFrame, we should reset the gas to reflect the existing remaining gas of the
        // parent frame
        ContractCallContext.get().getOpcodeContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        // Setting the initial remaining gas on the start of the initial parent frame
        ContractCallContext.get().getOpcodeContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void sanitizeTracedActions(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        final var context = ContractCallContext.get();
        final var gasCost = context.getOpcodeContext().getGasRemaining() - frame.getRemainingGas();

        final var frameRevertReason = frame.getRevertReason().orElse(null);
        final OpcodeTraceEntry entry;
        if (isCallToSystemContracts(frame, systemContracts)) {
            entry = createOpcode(
                    frame,
                    gasCost,
                    null,
                    getRevertReasonFromContractActions(context),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap());
        } else {
            entry = createOpcode(
                    frame,
                    gasCost,
                    frameRevertReason,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap());
        }

        context.getOpcodeContext().addOpcodes(entry);

        context.getOpcodeContext().setGasRemaining(frame.getRemainingGas());
    }

    private OpcodeTraceEntry createOpcode(
            final MessageFrame frame,
            final long gasCost,
            final Bytes frameRevertReason,
            final String systemContractRevertReason,
            final List<Bytes> stack,
            final List<Bytes> memory,
            final Map<Bytes, Bytes> storage) {
        return new OpcodeTraceEntry(
                frame.getPC(),
                frame.getCurrentOperation() != null
                        ? frame.getCurrentOperation().getName()
                        : StringUtils.EMPTY,
                frame.getRemainingGas(),
                gasCost,
                frame.getDepth(),
                stack,
                memory,
                storage,
                frameRevertReason,
                systemContractRevertReason);
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
