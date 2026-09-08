// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.utils.HexUtils.convertLongToHexString;
import static org.hiero.mirror.web3.utils.HexUtils.convertValueToHexString;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

    static final ExceptionalHaltReason TIMEOUT_HALT_REASON = new ExceptionalHaltReason() {
        @Override
        public String name() {
            return "TIMEOUT";
        }

        @Override
        public String getDescription() {
            return "Execution timeout exceeded";
        }

        @Override
        public String toString() {
            return name();
        }
    };

    @Override
    public void traceContextEnter(@NonNull final MessageFrame frame) {
        // Starting processing a newly created nested MessageFrame, we should set the remainingGas to match the newly
        // allocated gas for the new frame
        haltIfDeadlineExceeded(frame);
        ContractCallContext.get().getActionContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceContextReEnter(@NonNull final MessageFrame frame) {
        // Returning to the parent MessageFrame, we should reset the gas to reflect the existing remaining gas of
        // the parent frame
        haltIfDeadlineExceeded(frame);
        ContractCallContext.get().getActionContext().setGasRemaining(frame.getRemainingGas());
    }

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        haltIfDeadlineExceeded(frame);
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
        haltIfDeadlineExceeded(frame);
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    @Override
    public void tracePostExecution(MessageFrame frame, OperationResult operationResult) {
        final var actionContext = ContractCallContext.get().getActionContext();
        if (actionContext == null) {
            return;
        }

        if (haltIfDeadlineExceeded(frame)) {
            finalizeCurrentAction(actionContext, frame);
            return;
        }

        final var state = frame.getState();
        if (state == CODE_EXECUTING) {
            return;
        }

        final var onlyTopCall = onlyTopCall(actionContext);
        if (state == CODE_SUSPENDED) {
            if (onlyTopCall) {
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
        if (onlyTopCall && frame.getDepth() > 0) {
            return;
        }

        finalizeCurrentAction(actionContext, frame);
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

    private void finalizeCurrentAction(final ActionContext actionContext, final MessageFrame frame) {
        actionContext.finalizeAction(
                frame.getDepth(),
                haltError(frame),
                convertLongToHexString(actionContext.getGasRemaining() - frame.getRemainingGas()),
                frame.getOutputData().toHexString(),
                revertReason(frame));
    }

    private ActionResponse buildActionResponse(final MessageFrame frame, final TypeEnum type) {
        return new ActionResponse()
                .from(frame.getSenderAddress().toHexString())
                .gas(convertLongToHexString(frame.getRemainingGas()))
                .input(frame.getInputData().toHexString())
                .to(frame.getRecipientAddress().toHexString())
                .type(type)
                .value(convertValueToHexString(frame.getValue()));
    }

    private TypeEnum callTypeFromParent(final MessageFrame parent) {
        final var operation = parent.getCurrentOperation();
        if (operation == null) {
            return TypeEnum.UNKNOWN;
        }
        return toTypeEnum(OpcodeUtils.asCallOperationType(operation.getOpcode()));
    }

    private TypeEnum topLevelCallType(final MessageFrame frame) {
        // eth_call with an empty `to` is always CREATE. CREATE2 only appears as a nested opcode.
        return frame.getType() == CONTRACT_CREATION ? TypeEnum.CREATE : TypeEnum.CALL;
    }

    private boolean onlyTopCall(final ActionContext actionContext) {
        final var tracerConfig = actionContext.getTracerConfig();
        return tracerConfig != null && tracerConfig.onlyTopCall();
    }

    private boolean haltIfDeadlineExceeded(final MessageFrame frame) {
        final var ctx = ContractCallContext.get();
        final var actionContext = ctx.getActionContext();
        if (actionContext == null || !ctx.isDeadlineExceeded()) {
            return false;
        }
        actionContext.setTimedOut(true);
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(TIMEOUT_HALT_REASON));
        return true;
    }

    private TypeEnum toTypeEnum(final CallOperationType callOperationType) {
        try {
            return TypeEnum.fromValue(callOperationType.toString().replace(OPCODE_PREFIX, StringUtils.EMPTY));
        } catch (IllegalArgumentException e) {
            return TypeEnum.UNKNOWN;
        }
    }

    private String haltError(final MessageFrame frame) {
        final var halt = frame.getExceptionalHaltReason().orElse(null);
        if (halt == null || halt == ExceptionalHaltReason.NONE) {
            return null;
        }
        return halt.toString();
    }

    private String revertReason(final MessageFrame frame) {
        final var reason = frame.getRevertReason().orElse(null);
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        return reason.toHexString();
    }
}
