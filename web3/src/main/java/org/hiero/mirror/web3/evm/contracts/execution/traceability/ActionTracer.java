// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;

@Named
@CustomLog
@RequiredArgsConstructor
public class ActionTracer implements ActionSidecarContentTracer {

    private static final String OPCODE_PREFIX = "OP_";

    @Override
    public void traceContextEnter(@NonNull final MessageFrame frame) {
        // Starting processing a newly created nested MessageFrame, we should set the remainingGas to match the newly
        // allocated gas for the new frame
        ContractCallContext.get().getActionContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceContextReEnter(@NonNull final MessageFrame frame) {
        // Returning to the parent MessageFrame, we should reset the gas to reflect the existing remaining gas of
        // the parent frame
        ContractCallContext.get().getActionContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        final var actionContext = ContractCallContext.get().getActionContext();
        actionContext.setGasRemaining(frame.getRemainingGas());
        actionContext.addAction(buildActionResponse(frame, topLevelCallType(frame)), frame.getDepth());
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

    @Override
    public void tracePostExecution(MessageFrame frame, OperationResult operationResult) {
        final var state = frame.getState();
        final var actionContext = ContractCallContext.get().getActionContext();

        if (actionContext == null || state == CODE_EXECUTING) {
            return;
        }

        if (state == CODE_SUSPENDED) {
            if (actionContext.getTracerConfig().isOnlyTopCall()) {
                return;
            }
            final var child = frame.getMessageFrameStack().peek();
            if (child == null) {
                return;
            }
            // Nested call starts here: record the child under its parent using the child's depth.
            actionContext.addAction(buildActionResponse(child, callTypeFromParent(frame)), child.getDepth());
            return;
        }

        // Skip finalizing nested frames when only the top-level call should be traced.
        if (actionContext.getTracerConfig().isOnlyTopCall() && frame.getDepth() > 0) {
            return;
        }

        // Frame completed — finalize the action opened for this depth.
        actionContext.finalizeAction(frame.getDepth(), buildFinalizedAction(frame));
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

    private ActionResponse buildActionResponse(final MessageFrame frame, final TypeEnum type) {
        return new ActionResponse()
                .from(frame.getSenderAddress().toHexString())
                .gas(convertLongToHexString(frame.getRemainingGas()))
                .input(frame.getInputData().toHexString())
                .to(frame.getRecipientAddress().toHexString())
                .type(type);
    }

    private ActionResponse buildFinalizedAction(final MessageFrame frame) {
        final var actionContext = ContractCallContext.get().getActionContext();
        return new ActionResponse()
                .error(frame.getExceptionalHaltReason()
                        .orElse(ExceptionalHaltReason.NONE)
                        .toString())
                .gasUsed(convertLongToHexString(actionContext.getGasRemaining() - frame.getRemainingGas()))
                .output(frame.getOutputData().toHexString())
                .revertReason(frame.getRevertReason().orElse(Bytes.EMPTY).toHexString());
    }

    private TypeEnum callTypeFromParent(final MessageFrame parent) {
        final var operation = parent.getCurrentOperation();
        if (operation == null) {
            return TypeEnum.UNKNOWN;
        }
        return toTypeEnum(OpcodeUtils.asCallOperationType(operation.getOpcode()));
    }

    private TypeEnum topLevelCallType(final MessageFrame frame) {
        return frame.getType() == CONTRACT_CREATION ? TypeEnum.CREATE : TypeEnum.CALL;
    }

    private TypeEnum toTypeEnum(final CallOperationType callOperationType) {
        return TypeEnum.fromValue(callOperationType.toString().replace(OPCODE_PREFIX, StringUtils.EMPTY));
    }

    private String convertLongToHexString(final Long number) {
        return Bytes.wrap(String.valueOf(number).getBytes()).toHexString();
    }
}
