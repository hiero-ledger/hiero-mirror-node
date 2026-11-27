// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.rest.model.SimulateLog;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.jspecify.annotations.NonNull;

// Emits ERC20 Transfer logs for value transfers, as Besu's EthTransferLogOperationTracer does for eth_simulateV1.
@Named
public class SimulateActionTracer implements ActionSidecarContentTracer {

    private static final int SELFDESTRUCT_OPCODE = 0xFF;
    private static final String TRANSFER_EVENT_EMITTER = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String TRANSFER_EVENT_TOPIC0 = Hash.keccak256(
                    Bytes.wrap("Transfer(address,address,uint256)".getBytes(StandardCharsets.UTF_8)))
            .toHexString();

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
        context.getTransferLogSnapshots().push(context.getTransferLogs().size());
        addTransferLog(context, frame.getSenderAddress(), frame.getRecipientAddress(), frame.getValue());
    }

    // A frame's final state is only known on exit.
    @Override
    public void traceContextExit(@NonNull final MessageFrame frame) {
        final var context = ContractCallContext.get();
        if (context.getTransferLogSnapshots().isEmpty()) {
            return;
        }

        final int snapshot = context.getTransferLogSnapshots().pop();
        if (frame.getState() != MessageFrame.State.COMPLETED_SUCCESS) {
            // A failed frame reverts its own and its sub-calls' transfers.
            final var transferLogs = context.getTransferLogs();
            transferLogs.subList(snapshot, transferLogs.size()).clear();
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

    // SELFDESTRUCT sends the originator's whole balance to the beneficiary.
    @Override
    public void tracePreExecution(final @NonNull MessageFrame frame) {
        final var operation = frame.getCurrentOperation();
        // With an empty stack the operation itself halts, so there is no beneficiary to read.
        if (operation == null || operation.getOpcode() != SELFDESTRUCT_OPCODE || frame.stackSize() == 0) {
            return;
        }
        final var originator = frame.getRecipientAddress();
        final var account = frame.getWorldUpdater().get(originator);
        if (account == null) {
            return;
        }
        final var beneficiary = Words.toAddress(frame.getStackItem(0));
        addTransferLog(ContractCallContext.get(), originator, beneficiary, account.getBalance());
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        // NO-OP
    }

    @Override
    public void traceSuspended(MessageFrame parent, MessageFrame child, CallOperationType opCall) {
        // NO-OP
    }

    private static void addTransferLog(
            final ContractCallContext context, final Address sender, final Address recipient, final Wei value) {
        if (value.compareTo(Wei.ZERO) <= 0 || recipient.equals(sender)) {
            return;
        }
        context.getTransferLogs()
                .add(new SimulateLog()
                        .address(TRANSFER_EVENT_EMITTER)
                        .data(value.toHexString())
                        .topics(List.of(TRANSFER_EVENT_TOPIC0, addressTopic(sender), addressTopic(recipient))));
    }

    private static String addressTopic(final Address address) {
        return Bytes32.leftPad(address.getBytes()).toHexString();
    }
}
