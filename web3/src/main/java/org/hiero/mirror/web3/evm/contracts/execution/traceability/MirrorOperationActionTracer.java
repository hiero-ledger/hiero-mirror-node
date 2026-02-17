// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;

import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.ActionSidecarContentTracer;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hiero.base.utility.CommonUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.jspecify.annotations.NonNull;

@Named
@CustomLog
@RequiredArgsConstructor
public class MirrorOperationActionTracer implements ActionSidecarContentTracer {

    private final TraceProperties traceProperties;
    private final CommonEntityAccessor commonEntityAccessor;

    @Override
    public void tracePreExecution(final @NonNull MessageFrame frame) {
        if (frame.getCurrentOperation() != null
                && BALANCE_OPERATION_NAME.equals(frame.getCurrentOperation().getName())) {
            ContractCallContext.get().setBalanceCall(true);
        }
    }

    @Override
    public void tracePostExecution(
            final @NonNull MessageFrame frame, final Operation.@NonNull OperationResult operationResult) {
        if (!traceProperties.isEnabled()) {
            return;
        }

        if (traceProperties.stateFilterCheck(frame.getState())) {
            return;
        }

        final var recipientAddress = frame.getRecipientAddress();
        final var recipientNum = recipientAddress != null
                ? commonEntityAccessor.get(recipientAddress, Optional.empty())
                : Optional.empty();

        if (recipientNum.isPresent()
                && traceProperties.contractFilterCheck(
                        CommonUtils.hex(toEvmAddress(((Entity) recipientNum.get()).getId())))) {
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
    public List<ContractAction> contractActions() {
        return List.of();
    }
}
