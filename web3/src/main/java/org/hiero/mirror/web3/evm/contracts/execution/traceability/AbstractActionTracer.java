// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ProtoWriterTools;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.jspecify.annotations.NonNull;

public abstract class AbstractActionTracer implements ActionSidecarContentTracer {

    // A contract action with every always-set field populated, as protobuf omits default values
    @VisibleForTesting
    static final ContractAction ACTION_TEMPLATE = newActionTemplate();

    // The number of a missing account is unknown, so it is assumed to have the largest number in practice
    private static final int MISSING_NUM_BYTES = 4;
    private static final int TEMPLATE_CALL_DEPTH = 1;
    private static final long TEMPLATE_CONTRACT_NUM = 1L;

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

    // The input, call depth and caller and recipient numbers vary per action, so they adjust the template's size
    private static void addActionInput(final MessageFrame frame) {
        final int actionBytes = ACTION_TEMPLATE.protobufSize()
                + callDepthBytes(frame.getDepth())
                + contractNumBytes(frame, frame.getSenderAddress())
                + contractNumBytes(frame, frame.getContractAddress())
                + delimitedSize(frame.getInputData().size());
        // Each action is a length-delimited entry of the ContractActions sidecar
        addTraceDataBytes(frame, delimitedSize(actionBytes));
    }

    // The result is a oneof, so unlike the other fields it is written even when empty
    private static void addActionResult(final MessageFrame frame) {
        final int resultBytes = switch (frame.getState()) {
            case REVERT -> frame.getRevertReason().map(Bytes::size).orElse(0);
            case EXCEPTIONAL_HALT, COMPLETED_FAILED ->
                frame.getExceptionalHaltReason()
                        .map(reason -> reason.name().length())
                        .orElse(0);
            default -> frame.getOutputData().size();
        };
        final int actionResultBytes = 1 + ProtoWriterTools.sizeOfVarInt32(resultBytes) + resultBytes;
        addTraceDataBytes(frame, actionResultBytes + initcodeBytes(frame));
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

    // Size of the actual call depth relative to the template's, as the top-level depth of zero is omitted
    private static int callDepthBytes(final int depth) {
        final int depthBytes = depth == 0 ? 0 : 1 + ProtoWriterTools.sizeOfVarInt32(depth);
        return depthBytes - (1 + ProtoWriterTools.sizeOfVarInt32(TEMPLATE_CALL_DEPTH));
    }

    // Size of the actual caller or recipient number relative to the template's. An alias is resolved to its account,
    // which the EVM has already loaded.
    private static int contractNumBytes(final MessageFrame frame, final Address address) {
        return contractNumBytesOf(frame, address) - ProtoWriterTools.sizeOfVarInt64(TEMPLATE_CONTRACT_NUM);
    }

    private static int contractNumBytesOf(final MessageFrame frame, final Address address) {
        if (isLongZero(address)) {
            return ProtoWriterTools.sizeOfVarInt64(numberOfLongZero(address));
        }

        final var account = proxyUpdaterFor(frame).getHederaAccount(address);
        return account != null
                ? ProtoWriterTools.sizeOfVarInt64(account.hederaId().accountNumOrElse(0L))
                : MISSING_NUM_BYTES;
    }

    // Size of a non-empty length-delimited protobuf field with a single byte tag, as empty fields are omitted
    private static int delimitedSize(final int length) {
        return length == 0 ? 0 : 1 + ProtoWriterTools.sizeOfVarInt32(length) + length;
    }

    // A successful child creation also externalizes its initcode in a bytecode sidecar, which the consensus node counts
    // towards the same limit
    private static int initcodeBytes(final MessageFrame frame) {
        final var state = frame.getState();
        final boolean isSuccessfulChildCreation = frame.getType() == CONTRACT_CREATION
                && frame.getDepth() > 0
                && (state == CODE_SUCCESS || state == COMPLETED_SUCCESS);
        return isSuccessfulChildCreation ? delimitedSize(frame.getCode().getSize()) : 0;
    }

    private static boolean isBalanceOperation(final MessageFrame frame) {
        return frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName());
    }

    // The value is usually zero, so it is left unset
    private static ContractAction newActionTemplate() {
        final var commonProperties = CommonProperties.getInstance();
        final var contractId = ContractID.newBuilder()
                .shardNum(commonProperties.getShard())
                .realmNum(commonProperties.getRealm())
                .contractNum(TEMPLATE_CONTRACT_NUM)
                .build();
        return ContractAction.newBuilder()
                .callType(ContractActionType.CALL)
                .callingContract(contractId)
                .gas(15_000_000L)
                .recipientContract(contractId)
                .gasUsed(100_000L)
                .callDepth(TEMPLATE_CALL_DEPTH)
                .callOperationType(CallOperationType.OP_CALL)
                .build();
    }
}
