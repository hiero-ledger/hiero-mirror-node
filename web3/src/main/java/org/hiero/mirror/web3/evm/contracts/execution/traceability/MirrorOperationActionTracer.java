// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import com.hedera.services.utils.EntityIdUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

@Named
@CustomLog
public class MirrorOperationActionTracer implements ActionSidecarContentTracer {

    private final TraceProperties traceProperties;
    private final CommonEntityAccessor commonEntityAccessor;

    public MirrorOperationActionTracer(
            @NonNull final TraceProperties traceProperties, @NonNull final CommonEntityAccessor commonEntityAccessor) {
        this.traceProperties = traceProperties;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    public void tracePostExecution(
            @NonNull final MessageFrame frame, @NonNull final Operation.OperationResult operationResult) {
        if (!traceProperties.isEnabled()) {
            return;
        }

        if (traceProperties.stateFilterCheck(frame.getState())) {
            return;
        }

        final var recipientAddress = frame.getRecipientAddress();
        final var recipientNum = recipientAddress != null
                ? commonEntityAccessor.get(
                        com.hedera.pbj.runtime.io.buffer.Bytes.wrap(recipientAddress.toArray()), Optional.empty())
                : Optional.empty();

        if (recipientNum.isPresent()
                && traceProperties.contractFilterCheck(
                        EntityIdUtils.asHexedEvmAddress(((Entity) recipientNum.get()).getId()))) {
            return;
        }

        log.info(
                "type={} operation={}, callDepth={}, contract={}, sender={}, recipient={}, remainingGas={}, revertReason={}, input={}, output={}, return={}",
                frame.getType(),
                frame.getCurrentOperation() != null
                        ? frame.getCurrentOperation().getName()
                        : StringUtils.EMPTY,
                frame.getDepth(),
                frame.getContractAddress().toShortHexString(),
                frame.getSenderAddress().toShortHexString(),
                frame.getRecipientAddress().toShortHexString(),
                frame.getRemainingGas(),
                frame.getRevertReason()
                        .orElse(org.apache.tuweni.bytes.Bytes.EMPTY)
                        .toHexString(),
                frame.getInputData().toShortHexString(),
                frame.getOutputData().toShortHexString(),
                frame.getReturnData().toShortHexString());
    }

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
        // NO-OP
    }

    @Override
    public ContractActions contractActions() {
        return null;
    }
}
