// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.springframework.util.CollectionUtils;

public abstract class AbstractOpcodeTracer {

    @Getter
    @Setter
    protected Map<Address, HederaSystemContract> systemContracts = new HashMap<>();

    protected Map<Address, PrecompiledContract> hederaPrecompiles = new HashMap<>();

    protected List<Bytes> captureMemory(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }

        int size = frame.memoryWordSize();
        var memory = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; i++) {
            memory.add(frame.readMemory(i * 32L, 32));
        }

        return memory;
    }

    protected List<Bytes> captureStack(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        int size = frame.stackSize();
        var stack = new ArrayList<Bytes>(size);
        for (int i = 0; i < size; ++i) {
            stack.add(frame.getStackItem(size - 1 - i));
        }

        return stack;
    }

    protected Optional<Bytes> getRevertReasonFromContractActions(final ContractCallContext context) {
        final var contractActions = context.getContractActions();

        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }

        return contractActions.stream()
                .filter(ContractAction::hasRevertReason)
                .map(action -> Bytes.of(action.getResultData()))
                .map(this::formatRevertReason)
                .findFirst();
    }

    protected boolean isCallToHederaPrecompile(
            final MessageFrame frame, final Map<Address, PrecompiledContract> hederaPrecompiles) {
        final var recipientAddress = frame.getRecipientAddress();
        return hederaPrecompiles.containsKey(recipientAddress);
    }

    protected boolean isCallToSystemContracts(
            final MessageFrame frame, final Map<Address, HederaSystemContract> systemContracts) {
        final var recipientAddress = frame.getRecipientAddress();
        return systemContracts.containsKey(recipientAddress);
    }

    /**
     * Formats the revert reason to be consistent with the revert reason format in the EVM. <a
     * href="https://besu.hyperledger.org/23.10.2/private-networks/how-to/send-transactions/revert-reason#revert-reason-format">...</a>
     *
     * @param revertReason the revert reason
     * @return the formatted revert reason
     */
    protected Bytes formatRevertReason(final Bytes revertReason) {
        if (revertReason == null || revertReason.isZero()) {
            return Bytes.EMPTY;
        }

        // covers an edge case where the reason in the contract actions is a response code number (as a plain string)
        // so we convert this number to an ABI-encoded string of the corresponding response code name,
        // to at least give some relevant information to the user in the valid EVM format
        final var trimmedReason = revertReason.trimLeadingZeros();
        if (trimmedReason.size() <= Integer.BYTES) {
            final var responseCode = ResponseCodeEnum.forNumber(trimmedReason.toInt());
            if (responseCode != null) {
                return BytesDecoder.getAbiEncodedRevertReason(responseCode.name());
            }
        }

        return BytesDecoder.getAbiEncodedRevertReason(revertReason);
    }
}
