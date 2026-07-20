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
    public void tracePostExecution(
            final @NonNull MessageFrame frame, final Operation.@NonNull OperationResult operationResult) {
        if (!ContractCallContext.get().isTraceTransfers()) {
            return;
        }

        // Depth 0's transfer is captured in traceOriginAction instead - a frame with no bytecode never reaches here.
        if (frame.getDepth() > 0
                && frame.getState() == MessageFrame.State.COMPLETED_SUCCESS
                && !frame.getValue().isZero()) {
            capture(frame);
        }
    }

    @Override
    public void traceOriginAction(@NonNull final MessageFrame frame) {
        if (!ContractCallContext.get().isTraceTransfers()) {
            return;
        }

        if (!frame.getValue().isZero()) {
            capture(frame);
        }
    }

    private static void capture(final MessageFrame frame) {
        ContractCallContext.get()
                .getCapturedTransfers()
                .add(new CapturedTransfer(frame.getSenderAddress(), frame.getRecipientAddress(), frame.getValue()));
    }

    @Override
    public void tracePreExecution(final @NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePerOpcode(MessageFrame frame, long gas, ExceptionalHaltReason halt, Operation op) {
        // NO-OP
    }

    @Override
    public void traceSuspended(MessageFrame parent, MessageFrame child, CallOperationType opCall) {
        // NO-OP
    }

    @Override
    public void traceNotExecuting(MessageFrame child) {
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
    public List<ContractAction> contractActions() {
        return List.of();
    }
}
