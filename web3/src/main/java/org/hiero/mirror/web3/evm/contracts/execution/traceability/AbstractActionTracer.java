// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.ActionStack.Validation.OFF;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ProtoWriterTools;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;

/**
 * Measures the trace data the consensus node would externalize, using its own {@link ActionStack} like its
 * EvmActionTracer, and fails with INSUFFICIENT_GAS once it exceeds contracts.maxSerializedTraceDataBytes.
 */
public abstract class AbstractActionTracer implements ActionSidecarContentTracer {

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
            case CODE_SUSPENDED -> actionStack().pushActionOfIntermediate(frame);
            default -> finalizeAction(frame, () -> actionStack().finalizeLastAction(frame, OFF));
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
        actionStack().pushActionOfIntermediate(parent, child, opCall);
    }

    @Override
    public void traceNotExecuting(MessageFrame child) {
        finalizeAction(child, () -> actionStack().finalizeLastAction(child, OFF));
    }

    @Override
    public void traceAccountCreationResult(
            @NonNull final MessageFrame frame, @NonNull final Optional<ExceptionalHaltReason> haltReason) {
        // Only a failed creation skips tracePostExecution, so only then is its action finalized here
        if (haltReason.isPresent()) {
            finalizeAction(frame, () -> actionStack().finalizeLastAction(frame, OFF));
        }
    }

    @Override
    public void traceOriginAction(@NonNull final MessageFrame frame) {
        final var context = ContractCallContext.get();
        context.resetTraceData();
        context.getActionStack().pushActionOfTopLevel(frame);
    }

    @Override
    public void sanitizeTracedActions(@NonNull final MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull final MessageFrame frame, @NonNull final ContractActionType type) {
        finalizeAction(frame, () -> actionStack().finalizeLastStackActionAsPrecompile(frame, type, OFF));
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    private static ActionStack actionStack() {
        return ContractCallContext.get().getActionStack();
    }

    private static void finalizeAction(final MessageFrame frame, final Runnable finalizer) {
        final var openAction = ContractCallContext.get().getOpenActions().peek();
        if (openAction == null) {
            return;
        }

        finalizer.run();
        final var action = openAction.get();
        final var actionBytes = ContractAction.PROTOBUF.measureRecord(action);
        // Each action is a length-delimited entry of the ContractActions sidecar
        addTraceDataBytes(frame, 1L + ProtoWriterTools.sizeOfVarInt32(actionBytes) + actionBytes);

        // A successful creation also externalizes its bytecode, with the initcode only for a child creation
        if (frame.getType() == CONTRACT_CREATION && frame.getState() == CODE_SUCCESS) {
            final var bytecode = ContractBytecode.newBuilder()
                    .contractId(action.recipientContract())
                    .runtimeBytecode(tuweniToPbjBytes(frame.getOutputData()));
            if (frame.getDepth() > 0) {
                bytecode.initcode(tuweniToPbjBytes(frame.getCode().getBytes()));
            }
            addTraceDataBytes(frame, ContractBytecode.PROTOBUF.measureRecord(bytecode.build()));
        }
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
