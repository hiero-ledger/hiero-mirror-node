// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.utils.HexUtils.convertLongToHexString;
import static org.hiero.mirror.web3.utils.HexUtils.convertValueToHexString;
import static org.hiero.mirror.web3.utils.HexUtils.parseHexLong;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@NullMarked
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
        haltIfDeadlineExceeded(frame);
    }

    @Override
    public void traceContextReEnter(@NonNull final MessageFrame frame) {
        haltIfDeadlineExceeded(frame);
    }

    @Override
    public void traceOriginAction(@NonNull final MessageFrame frame) {
        final var actionContext = actionContext();
        if (actionContext == null) {
            return;
        }
        haltIfDeadlineExceeded(frame);
        actionContext.addAction(buildActionResponse(frame, topLevelCallType(frame)), frame.getDepth());
    }

    @Override
    public void sanitizeTracedActions(@NonNull final MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull final MessageFrame frame, @NonNull final ContractActionType type) {
        final var actionContext = actionContext();
        if (actionContext == null) {
            return;
        }
        haltIfDeadlineExceeded(frame);
        if (actionContext.isOnlyTopCall() && frame.getDepth() > 0) {
            return;
        }
        if (!actionContext.hasActionAt(frame.getDepth())) {
            actionContext.addAction(buildActionResponse(frame, typeEnumFrom(type)), frame.getDepth());
        }
        finalizeCurrentAction(actionContext, frame);
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
        final var actionContext = actionContext();
        if (actionContext == null) {
            return;
        }

        final var state = frame.getState();
        if (state == CODE_EXECUTING) {
            if (actionContext.shouldCheckDeadline()) {
                haltIfDeadlineExceeded(frame);
            }
            return;
        }
        if (actionContext.isOnlyTopCall() && (state == CODE_SUSPENDED || frame.getDepth() > 0)) {
            return;
        }
        if (state == CODE_SUSPENDED) {
            recordNestedCall(actionContext, frame);
            return;
        }
        finalizeCurrentAction(actionContext, frame);
    }

    @Override
    public void tracePerOpcode(
            final MessageFrame frame, final long gas, final ExceptionalHaltReason halt, final Operation op) {
        // NO-OP
    }

    @Override
    public void traceSuspended(final MessageFrame parent, final MessageFrame child, final CallOperationType opCall) {
        // NO-OP
    }

    @Override
    public void traceNotExecuting(final MessageFrame child) {
        // NO-OP
    }

    private void recordNestedCall(final ActionContext actionContext, final MessageFrame frame) {
        final var child = frame.getMessageFrameStack().peek();
        if (child == null) {
            return;
        }
        actionContext.addAction(buildActionResponse(child, callTypeFromParent(frame)), child.getDepth());
    }

    private void finalizeCurrentAction(final ActionContext actionContext, final MessageFrame frame) {
        final var action = actionContext.getCurrentAction(frame.getDepth());
        if (action == null) {
            return;
        }
        final var gasUsed = Math.max(0L, parseHexLong(action.getGas()) - frame.getRemainingGas());
        actionContext.finalizeAction(
                frame.getDepth(),
                haltError(frame),
                convertLongToHexString(gasUsed),
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
                .value(convertValueToHexString(frame.getValue()))
                .calls(new ArrayList<>());
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

    private TypeEnum typeEnumFrom(final ContractActionType type) {
        return type == ContractActionType.CREATE ? TypeEnum.CREATE : TypeEnum.CALL;
    }

    private void haltIfDeadlineExceeded(final MessageFrame frame) {
        final var ctx = ContractCallContext.get();
        final var actionContext = ctx.getActionContext();
        if (actionContext == null || !ctx.isDeadlineExceeded()) {
            return;
        }
        actionContext.setTimedOut(true);
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(TIMEOUT_HALT_REASON));
    }

    private @Nullable ActionContext actionContext() {
        return ContractCallContext.get().getActionContext();
    }

    private TypeEnum toTypeEnum(final CallOperationType callOperationType) {
        try {
            return TypeEnum.fromValue(callOperationType.toString().replace(OPCODE_PREFIX, StringUtils.EMPTY));
        } catch (IllegalArgumentException e) {
            return TypeEnum.UNKNOWN;
        }
    }

    private @Nullable String haltError(final MessageFrame frame) {
        final var halt = frame.getExceptionalHaltReason().orElse(null);
        if (halt == null || halt == ExceptionalHaltReason.NONE) {
            return null;
        }
        return halt.toString();
    }

    private @Nullable String revertReason(final MessageFrame frame) {
        final var reason = frame.getRevertReason().orElse(null);
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        return reason.toHexString();
    }
}
