// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.spi.workflows.HandleException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;

public abstract class AbstractActionTracer implements ActionSidecarContentTracer {

    // Measured serialized overhead of one contract action
    private static final int ACTION_BYTES = 40;

    @Override
    public void tracePreExecution(@NonNull final MessageFrame frame) {
        if (isBalanceOperation(frame)) {
            ContractCallContext.get().setBalanceCall(true);
        }
    }

    @Override
    public void tracePostExecution(@NonNull final MessageFrame frame, @NonNull final OperationResult operationResult) {
        switch (frame.getState()) {
            case CODE_EXECUTING -> {}
            case CODE_SUSPENDED -> addActionInput(frame.getMessageFrameStack().peek());
            default -> addActionResult(frame);
        }

        // Reset the balance call flag after BALANCE opcode completes
        if (isBalanceOperation(frame)) {
            ContractCallContext.get().setBalanceCall(false);
        }
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
    public void traceOriginAction(@NonNull final MessageFrame frame) {
        ContractCallContext.get().setTraceDataBytes(0);
        addActionInput(frame);
    }

    @Override
    public void sanitizeTracedActions(@NonNull final MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull final MessageFrame frame, @NonNull final ContractActionType type) {
        addActionResult(frame);
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    // A child creation also externalizes its initcode as bytecode
    private static void addActionInput(final MessageFrame frame) {
        final var initcodeBytes = frame.getType() == CONTRACT_CREATION && frame.getDepth() > 0
                ? frame.getCode().getSize()
                : 0;
        addTraceDataBytes(frame, ACTION_BYTES + frame.getInputData().size() + initcodeBytes);
    }

    // A halted frame externalizes only its halt reason and may have no output
    private static void addActionResult(final MessageFrame frame) {
        addTraceDataBytes(
                frame,
                switch (frame.getState()) {
                    case REVERT -> frame.getRevertReason().map(Bytes::size).orElse(0);
                    case EXCEPTIONAL_HALT, COMPLETED_FAILED -> 0;
                    default -> frame.getOutputData().size();
                });
    }

    // Like the consensus node, fail with INSUFFICIENT_GAS once the trace data exceeds the limit. The consensus node
    // halts the remaining frames when a HandleException is thrown during execution.
    private static void addTraceDataBytes(final MessageFrame frame, final long bytes) {
        final var context = ContractCallContext.get();
        context.setTraceDataBytes(context.getTraceDataBytes() + bytes);
        if (context.getTraceDataBytes() > contractsConfigOf(frame).maxSerializedTraceDataBytes()) {
            throw new HandleException(INSUFFICIENT_GAS);
        }
    }

    private static boolean isBalanceOperation(final MessageFrame frame) {
        return frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName());
    }
}
