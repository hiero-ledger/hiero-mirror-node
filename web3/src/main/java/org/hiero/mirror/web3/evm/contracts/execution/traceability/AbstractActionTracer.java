// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.ProtoWriterTools;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;

public abstract class AbstractActionTracer implements ActionSidecarContentTracer {

    // Serialized size of a contract action without its input and result, including its ContractActions entry overhead.
    // Value is left unset as it is usually zero, which protobuf omits.
    @VisibleForTesting
    static final int ACTION_BYTES = delimitedSize(ContractAction.PROTOBUF.measureRecord(ContractAction.newBuilder()
            .callType(ContractActionType.CALL)
            .callingContract(ContractID.newBuilder().contractNum(Integer.MAX_VALUE))
            .gas(15_000_000L)
            .recipientContract(ContractID.newBuilder().contractNum(Integer.MAX_VALUE))
            .gasUsed(100_000L)
            .callDepth(1)
            .callOperationType(CallOperationType.OP_CALL)
            .build()));

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
        addTraceDataBytes(
                frame,
                ACTION_BYTES
                        + shardAndRealmBytes(frame)
                        + delimitedSize(frame.getInputData().size())
                        + initcodeBytes);
    }

    // The result is always set, even when empty: the output, the revert reason or the halt reason
    private static void addActionResult(final MessageFrame frame) {
        final int resultBytes = switch (frame.getState()) {
            case REVERT -> frame.getRevertReason().map(Bytes::size).orElse(0);
            case EXCEPTIONAL_HALT, COMPLETED_FAILED ->
                frame.getExceptionalHaltReason()
                        .map(reason -> reason.name().length())
                        .orElse(0);
            default -> frame.getOutputData().size();
        };
        addTraceDataBytes(frame, 1 + ProtoWriterTools.sizeOfVarInt32(resultBytes) + resultBytes);
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

    // The caller and recipient ids also have shard and realm fields when they are not zero
    @VisibleForTesting
    static int shardAndRealmBytes(final MessageFrame frame) {
        final var hederaConfig = configOf(frame).getConfigData(HederaConfig.class);
        return 2 * (varIntFieldSize(hederaConfig.shard()) + varIntFieldSize(hederaConfig.realm()));
    }

    // Size of a non-zero integer protobuf field with a single byte tag, as zero fields are omitted
    private static int varIntFieldSize(final long value) {
        return value == 0 ? 0 : 1 + ProtoWriterTools.sizeOfVarInt64(value);
    }

    // Size of a non-empty length-delimited protobuf field with a single byte tag, as empty fields are omitted
    private static int delimitedSize(final int length) {
        return length == 0 ? 0 : 1 + ProtoWriterTools.sizeOfVarInt32(length) + length;
    }

    private static boolean isBalanceOperation(final MessageFrame frame) {
        return frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName());
    }
}
