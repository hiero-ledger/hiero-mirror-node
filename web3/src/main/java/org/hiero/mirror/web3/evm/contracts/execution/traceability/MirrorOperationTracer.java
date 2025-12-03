// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

@Named
@CustomLog
@RequiredArgsConstructor
public class MirrorOperationTracer implements HederaEvmOperationTracer {

    private final TraceProperties traceProperties;
    private final CommonEntityAccessor commonEntityAccessor;

    @Override
    public void tracePostExecution(final MessageFrame currentFrame, final Operation.OperationResult operationResult) {
        if (!traceProperties.isEnabled()) {
            return;
        }
        if (traceProperties.stateFilterCheck(currentFrame.getState())) {
            return;
        }

        final var recipientAddress = currentFrame.getRecipientAddress();
        final var recipientEntity = commonEntityAccessor.get(recipientAddress, Optional.empty());
        EntityId recipientNum;
        if (recipientEntity.isPresent()) {
            recipientNum = recipientEntity.get().toEntityId();
        } else {
            recipientNum = EntityId.EMPTY;
        }

        if (traceProperties.contractFilterCheck(
                Bytes.wrap(recipientNum.toAccountID().toByteArray()).toHexString())) {
            return;
        }

        log.info(
                "type={} operation={}, callDepth={}, contract={}, sender={}, recipient={}, remainingGas={}, revertReason={}, input={}, output={}, return={}",
                currentFrame.getType(),
                currentFrame.getCurrentOperation() != null
                        ? currentFrame.getCurrentOperation().getName()
                        : StringUtils.EMPTY,
                currentFrame.getDepth(),
                currentFrame.getContractAddress().toShortHexString(),
                currentFrame.getSenderAddress().toShortHexString(),
                currentFrame.getRecipientAddress().toShortHexString(),
                currentFrame.getRemainingGas(),
                currentFrame.getRevertReason().orElse(Bytes.EMPTY).toHexString(),
                currentFrame.getInputData().toShortHexString(),
                currentFrame.getOutputData().toShortHexString(),
                currentFrame.getReturnData().toShortHexString());
    }
}
