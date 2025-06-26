// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerUtils.captureMemory;
import static org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerUtils.captureStack;
import static org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerUtils.getRevertReasonFromContractActions;
import static org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerUtils.isCallToHederaPrecompile;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.service.contract.impl.exec.tracers.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.config.PrecompiledContractProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

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
    public void tracePostExecution(@NonNull final MessageFrame frame, @NonNull final OperationResult operationResult) {
        final var context = ContractCallContext.get();

        final var options = context.getOpcodeTracerOptions();
        final var memory = captureMemory(frame, options);
        final var stack = captureStack(frame, options);
        final var storage = captureStorage(frame, options);
        final var opcode = Opcode.builder()
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
    public void tracePrecompileResult(@NonNull final MessageFrame frame, @NonNull final ContractActionType type) {
        final var context = ContractCallContext.get();
        final var revertReason = isCallToHederaPrecompile(frame, hederaPrecompiles)
                ? getRevertReasonFromContractActions(context)
                : frame.getRevertReason();

        final var opcode = Opcode.builder()
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
}
