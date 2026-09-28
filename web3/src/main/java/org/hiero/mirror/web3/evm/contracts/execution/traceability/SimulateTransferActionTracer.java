// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.jspecify.annotations.NonNull;

// Does not cover SELFDESTRUCT beneficiary transfers, which complete via a different frame lifecycle - a known gap.
@Named
public class SimulateTransferActionTracer implements ActionSidecarContentTracer {
    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    @Override
    public void sanitizeTracedActions(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void traceContextEnter(@NonNull final MessageFrame frame) {
        final var context = ContractCallContext.get();
        if (context.isTraceTransfers()) {
            context.getTransferFrameStarts().push(context.getCapturedTransfers().size());
        }
    }

    // Besu only marks a frame COMPLETED_SUCCESS/FAILED after its last operation, so this is the first point where a
    // frame's value transfer is known to have stuck - including frames without code, which never execute an operation.
    @Override
    public void traceContextExit(@NonNull final MessageFrame frame) {
        final var context = ContractCallContext.get();
        if (!context.isTraceTransfers() || context.getTransferFrameStarts().isEmpty()) {
            return;
        }

        final int frameStart = context.getTransferFrameStarts().pop();
        final var transfers = context.getCapturedTransfers();
        if (frame.getState() != MessageFrame.State.COMPLETED_SUCCESS) {
            // A failed frame rolls back its own transfer and every transfer made by its sub-calls.
            transfers.subList(frameStart, transfers.size()).clear();
        } else if (!frame.getValue().isZero()) {
            // Inserted ahead of the sub-calls' transfers, which were captured first because they exited first.
            transfers.add(
                    frameStart,
                    new CapturedTransfer(frame.getSenderAddress(), frame.getRecipientAddress(), frame.getValue()));
        }
    }

    @Override
    public void traceNotExecuting(MessageFrame child) {
        // NO-OP
    }

    @Override
    public void traceOriginAction(@NonNull final MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePerOpcode(MessageFrame frame, long gas, ExceptionalHaltReason halt, Operation op) {
        // NO-OP
    }

    @Override
    public void tracePostExecution(
            final @NonNull MessageFrame frame, final Operation.@NonNull OperationResult operationResult) {
        // NO-OP
    }

    @Override
    public void tracePreExecution(final @NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        // NO-OP
    }

    @Override
    public void traceSuspended(MessageFrame parent, MessageFrame child, CallOperationType opCall) {
        // NO-OP
    }
}
