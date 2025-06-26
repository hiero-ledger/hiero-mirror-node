// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.tracers.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.config.PrecompiledContractProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.springframework.util.CollectionUtils;

@Named
@CustomLog
public class OpcodeActionTracer extends EvmActionTracer {

    private final Map<Address, PrecompiledContract> hederaPrecompiles;

    public OpcodeActionTracer(@NonNull final PrecompiledContractProvider precompiledContractProvider) {
        super(new ActionStack());

        this.hederaPrecompiles = precompiledContractProvider.getHederaPrecompiles().entrySet().stream()
                .collect(Collectors.toMap(e -> Address.fromHexString(e.getKey()), Map.Entry::getValue));
    }

    @Override
    public void tracePostExecution(@NonNull MessageFrame frame, @NonNull OperationResult operationResult) {
        ContractCallContext context = getContext();

        OpcodeTracerOptions options = context.getOpcodeTracerOptions();
        final List<Bytes> memory = captureMemory(frame, options);
        final List<Bytes> stack = captureStack(frame, options);
        final Map<Bytes, Bytes> storage = captureStorage(frame, options);
        Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(frame.getCurrentOperation().getName())
                .gas(frame.getRemainingGas())
                // TODO add gas cost for post-execution
                .gasCost(0L)
                .depth(frame.getDepth())
                .stack(stack)
                .memory(memory)
                .storage(storage)
                .reason(frame.getRevertReason().map(Bytes::toString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    @Override
    public void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type) {
        ContractCallContext context = getContext();
        Optional<Bytes> revertReason =
                isCallToHederaPrecompile(frame) ? getRevertReasonFromContractActions(context) : frame.getRevertReason();

        Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(
                        frame.getCurrentOperation() != null
                                ? frame.getCurrentOperation().getName()
                                : StringUtils.EMPTY)
                .gas(frame.getRemainingGas())
                // TODO add gas cost for precompile execution
                .gasCost(0L)
                .depth(frame.getDepth())
                .stack(Collections.emptyList())
                .memory(Collections.emptyList())
                .storage(Collections.emptyMap())
                .reason(revertReason.map(Bytes::toHexString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    private List<Bytes> captureMemory(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isMemory()) {
            return Collections.emptyList();
        }

        final var size = frame.memoryWordSize();
        final var memory = new ArrayList<Bytes>(size);
        for (var i = 0; i < size; i++) {
            memory.add(frame.readMemory(i * 32L, 32));
        }

        return memory;
    }

    private List<Bytes> captureStack(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStack()) {
            return Collections.emptyList();
        }

        final var size = frame.stackSize();
        final var stack = new ArrayList<Bytes>(size);
        for (var i = 0; i < size; ++i) {
            stack.add(frame.getStackItem(size - 1 - i));
        }

        return stack;
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame, final OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            final var updates = ((ProxyWorldUpdater) frame.getWorldUpdater()).pendingStorageUpdates();
            return updates.stream()
                    .flatMap(storageAccesses ->
                            storageAccesses.accesses().stream()) // Properly flatten the nested structure
                    .collect(Collectors.toMap(
                            e -> Bytes.wrap(e.key().toArray()),
                            e -> Bytes.wrap(e.value().toArray()),
                            (v1, v2) -> v1, // in case of duplicates, keep the first value
                            TreeMap::new));

        } catch (final ModificationNotAllowedException e) {
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    private Optional<Bytes> getRevertReasonFromContractActions(final ContractCallContext context) {
        List<ContractAction> contractActions = context.getContractActions();

        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }

        return contractActions.stream()
                .filter(ContractAction::hasRevertReason)
                .map(action -> Bytes.of(action.getResultData()))
                .map(this::formatRevertReason)
                .findFirst();
    }

    public ContractCallContext getContext() {
        return ContractCallContext.get();
    }

    private boolean isCallToHederaPrecompile(final MessageFrame frame) {
        Address recipientAddress = frame.getRecipientAddress();
        return hederaPrecompiles.containsKey(recipientAddress);
    }

    /**
     * Formats the revert reason to be consistent with the revert reason format in the EVM. <a
     * href="https://besu.hyperledger.org/23.10.2/private-networks/how-to/send-transactions/revert-reason#revert-reason-format">...</a>
     *
     * @param revertReason the revert reason
     * @return the formatted revert reason
     */
    private Bytes formatRevertReason(final Bytes revertReason) {
        if (revertReason == null || revertReason.isZero()) {
            return Bytes.EMPTY;
        }

        // covers an edge case where the reason in the contract actions is a response code number (as a plain string)
        // so we convert this number to an ABI-encoded string of the corresponding response code name,
        // to at least give some relevant information to the user in the valid EVM format
        Bytes trimmedReason = revertReason.trimLeadingZeros();
        if (trimmedReason.size() <= Integer.BYTES) {
            ResponseCodeEnum responseCode = ResponseCodeEnum.forNumber(trimmedReason.toInt());
            if (responseCode != null) {
                return BytesDecoder.getAbiEncodedRevertReason(responseCode.name());
            }
        }

        return BytesDecoder.getAbiEncodedRevertReason(revertReason);
    }
}
