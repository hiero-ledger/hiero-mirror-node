// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.services.stream.proto.ContractActionType;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.config.PrecompiledContractProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Named
@CustomLog
@Getter
public class OpcodeTracer extends AbstractOpcodeTracer implements HederaOperationTracer {

    private final Map<Address, PrecompiledContract> hederaPrecompiles;

    public OpcodeTracer(final PrecompiledContractProvider precompiledContractProvider) {
        this.hederaPrecompiles = precompiledContractProvider.getHederaPrecompiles().entrySet().stream()
                .collect(Collectors.toMap(e -> Address.fromHexString(e.getKey()), Map.Entry::getValue));
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        final ContractCallContext context = ContractCallContext.get();

        final OpcodeTracerOptions options = context.getOpcodeTracerOptions();
        final List<Bytes> memory = captureMemory(frame, options);
        final List<Bytes> stack = captureStack(frame, options);
        final Map<Bytes, Bytes> storage = captureStorage(frame, options);
        final Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(frame.getCurrentOperation().getName())
                .gas(frame.getRemainingGas())
                .gasCost(operationResult.getGasCost())
                .depth(frame.getDepth())
                .stack(stack)
                .memory(memory)
                .storage(storage)
                .reason(frame.getRevertReason().map(Bytes::toString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    @Override
    public void tracePrecompileCall(final MessageFrame frame, final long gasRequirement, final Bytes output) {
        final ContractCallContext context = ContractCallContext.get();
        final Optional<Bytes> revertReason = isCallToHederaPrecompile(frame, hederaPrecompiles)
                ? getRevertReasonFromContractActions(context)
                : frame.getRevertReason();
        final Opcode opcode = Opcode.builder()
                .pc(frame.getPC())
                .op(
                        frame.getCurrentOperation() != null
                                ? frame.getCurrentOperation().getName()
                                : StringUtils.EMPTY)
                .gas(frame.getRemainingGas())
                .gasCost(output != null ? gasRequirement : 0L)
                .depth(frame.getDepth())
                .stack(Collections.emptyList())
                .memory(Collections.emptyList())
                .storage(Collections.emptyMap())
                .reason(revertReason.map(Bytes::toHexString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    private Map<Bytes, Bytes> captureStorage(final MessageFrame frame, OpcodeTracerOptions options) {
        if (!options.isStorage()) {
            return Collections.emptyMap();
        }

        try {
            final Address address = frame.getRecipientAddress();
            final MutableAccount account = frame.getWorldUpdater().getAccount(address);

            if (account == null) {
                log.warn("Failed to retrieve storage contents. Account not found in WorldUpdater");
                return Collections.emptyMap();
            }

            return new TreeMap<>(account.getUpdatedStorage());
        } catch (final ModificationNotAllowedException e) {
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
        // Empty body
    }

    private boolean isCallToHederaPrecompile(
            final MessageFrame frame, final Map<Address, PrecompiledContract> hederaPrecompiles) {
        final var recipientAddress = frame.getRecipientAddress();
        return hederaPrecompiles.containsKey(recipientAddress);
    }
}
