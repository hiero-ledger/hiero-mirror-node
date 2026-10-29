// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static org.hiero.mirror.web3.utils.HexUtils.convertHexStringToLong;
import static org.hiero.mirror.web3.utils.HexUtils.convertLongToHexString;
import static org.hiero.mirror.web3.utils.HexUtils.convertValueToHexString;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_FAILED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@NullMarked
public class ActionTracer implements ActionSidecarContentTracer {

    static final ExceptionalHaltReason TIMEOUT_HALT_REASON = new ExceptionalHaltReason() {
        @Override
        public String getDescription() {
            return "Execution timeout exceeded";
        }

        @Override
        public String name() {
            return "TIMEOUT";
        }

        @Override
        public String toString() {
            return name();
        }
    };

    private static final String EXECUTION_REVERTED = "execution reverted";
    private static final String OPCODE_PREFIX = "OP_";

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    @Override
    public void sanitizeTracedActions(final MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void traceContextEnter(final MessageFrame frame) {
        haltIfDeadlineExceeded(frame);
    }

    @Override
    public void traceContextReEnter(final MessageFrame frame) {
        haltIfDeadlineExceeded(frame);
    }

    @Override
    public void traceNotExecuting(final MessageFrame child) {
        // NO-OP
    }

    @Override
    public void traceOriginAction(final MessageFrame frame) {
        final var actionContext = actionContext();
        if (actionContext == null) {
            return;
        }
        haltIfDeadlineExceeded(frame);
        actionContext.addAction(buildActionResponse(frame, topLevelCallType(frame)), frame.getDepth());
    }

    @Override
    public void tracePerOpcode(
            final MessageFrame frame, final long gas, final ExceptionalHaltReason halt, final Operation op) {
        // NO-OP
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
    public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
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
    public void traceSuspended(final MessageFrame parent, final MessageFrame child, final CallOperationType opCall) {
        // NO-OP
    }

    private @Nullable ActionContext actionContext() {
        return ContractCallContext.get().getActionContext();
    }

    private ActionResponse buildActionResponse(final MessageFrame frame, final TypeEnum type) {
        final var actionContext = actionContext();
        return new ActionResponse()
                .from(frame.getSenderAddress().toHexString())
                .gas(convertLongToHexString(frame.getRemainingGas()))
                .input(actionContext == null ? null : actionContext.hexWithinBudget(frame.getInputData()))
                .to(frame.getRecipientAddress().toHexString())
                .type(type)
                .value(convertValueToHexString(frame.getValue()))
                .calls(new ArrayList<>());
    }

    private boolean burnsFrameGas(final MessageFrame frame) {
        final var actionContext = actionContext();
        if (actionContext != null && actionContext.isTimedOut()) {
            return false;
        }
        final var halt = frame.getExceptionalHaltReason().orElse(null);
        if (halt == TIMEOUT_HALT_REASON) {
            return false;
        }
        final var state = frame.getState();
        return state == EXCEPTIONAL_HALT || state == COMPLETED_FAILED;
    }

    private TypeEnum callTypeFromParent(final MessageFrame parent) {
        final var operation = parent.getCurrentOperation();
        if (operation == null) {
            return TypeEnum.UNKNOWN;
        }
        return toTypeEnum(OpcodeUtils.asCallOperationType(operation.getOpcode()));
    }

    private void finalizeCurrentAction(final ActionContext actionContext, final MessageFrame frame) {
        final var action = actionContext.getCurrentAction(frame.getDepth());
        if (action == null) {
            return;
        }
        final var reverted = frame.getState() == REVERT;
        final var output = actionContext.hexWithinBudget(
                reverted ? frame.getRevertReason().orElse(frame.getOutputData()) : frame.getOutputData());
        actionContext.finalizeAction(
                frame.getDepth(),
                reverted ? EXECUTION_REVERTED : haltError(frame),
                gasUsedHex(frame, action.getGas()),
                output,
                reverted && output != null && output.startsWith("0x08c379a0")
                        ? maybeDecodeSolidityErrorStringToReadableMessage(output)
                        : null);
    }

    /**
     * Gas burned by this frame, clamped to the gas recorded when the frame was entered. An exceptional halt consumes
     * the whole frame. A timeout reports only the gas used before the deadline. A revert keeps gas consumed so far.
     */
    private String gasUsedHex(final MessageFrame frame, final @Nullable String gasHex) {
        final long gas = convertHexStringToLong(gasHex);
        if (burnsFrameGas(frame)) {
            return convertLongToHexString(gas);
        }
        final long remaining = Math.max(0L, frame.getRemainingGas());
        final long used = Math.min(gas, Math.max(0L, gas - remaining));
        return convertLongToHexString(used);
    }

    private @Nullable String haltError(final MessageFrame frame) {
        final var halt = frame.getExceptionalHaltReason().orElse(null);
        if (halt != null && halt != ExceptionalHaltReason.NONE) {
            return halt.toString();
        }
        // Besu can clear the halt reason before the frame is finalized. The timeout flag is the record that this
        // call was stopped because the trace deadline passed.
        final var actionContext = actionContext();
        if (actionContext != null && actionContext.isTimedOut()) {
            return TIMEOUT_HALT_REASON.toString();
        }
        return null;
    }

    private void haltIfDeadlineExceeded(final MessageFrame frame) {
        final var context = ContractCallContext.get();
        final var actionContext = context.getActionContext();
        if (actionContext == null || !context.isDeadlineExceeded()) {
            return;
        }
        actionContext.setTimedOut(true);
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(TIMEOUT_HALT_REASON));
        // The executor can return without a later finalize callback once the frame is halted.
        final var action = actionContext.getCurrentAction(frame.getDepth());
        if (action != null && !ActionContext.TRUNCATED_ERROR.equals(action.getError())) {
            action.error(TIMEOUT_HALT_REASON.toString());
            if (action.getGasUsed() == null && action.getGas() != null) {
                action.gasUsed(gasUsedHex(frame, action.getGas()));
            }
        }
    }

    private void recordNestedCall(final ActionContext actionContext, final MessageFrame frame) {
        final var child = frame.getMessageFrameStack().peek();
        if (child == null) {
            return;
        }
        actionContext.addAction(buildActionResponse(child, callTypeFromParent(frame)), child.getDepth());
    }

    private TypeEnum toTypeEnum(final CallOperationType callOperationType) {
        try {
            return TypeEnum.fromValue(callOperationType.toString().replace(OPCODE_PREFIX, StringUtils.EMPTY));
        } catch (IllegalArgumentException _) {
            return TypeEnum.UNKNOWN;
        }
    }

    private TypeEnum topLevelCallType(final MessageFrame frame) {
        // eth_call with an empty `to` is always CREATE. CREATE2 only appears as a nested opcode.
        return frame.getType() == CONTRACT_CREATION ? TypeEnum.CREATE : TypeEnum.CALL;
    }

    private TypeEnum typeEnumFrom(final ContractActionType type) {
        return type == ContractActionType.CREATE ? TypeEnum.CREATE : TypeEnum.CALL;
    }
}
