// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

@Named
@CustomLog
public class PrestateActionTracer extends AbstractOpcodeTracer implements ActionSidecarContentTracer {

    @Override
    public void traceOriginAction(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void sanitizeTracedActions(@NonNull MessageFrame frame) {
        // NO-OP
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        final var context = ContractCallContext.get();
        if (context.getPrestateContext() != null) {
            context.getPrestateContext().getTouchedAccounts().add(frame.getSenderAddress());
            context.getPrestateContext().getTouchedAccounts().add(frame.getRecipientAddress());
            context.getPrestateContext().getTouchedAccounts().add(frame.getContractAddress());
        }
    }

    @Override
    public List<ContractAction> contractActions() {
        return List.of();
    }

    @Override
    public void tracePostExecution(MessageFrame frame, OperationResult operationResult) {
        final var context = ContractCallContext.get();
        if (context.getPrestateContext() != null) {
            context.getPrestateContext().getTouchedAccounts().add(frame.getSenderAddress());
            context.getPrestateContext().getTouchedAccounts().add(frame.getRecipientAddress());
            context.getPrestateContext().getTouchedAccounts().add(frame.getContractAddress());
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
}
