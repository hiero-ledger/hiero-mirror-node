// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.stream.proto.ContractActionType.PRECOMPILE;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_FAILED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.convert.BytesDecoder;
import com.hedera.mirror.web3.evm.config.PrecompiledContractProvider;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.state.State;
import jakarta.inject.Named;
import java.util.ArrayList;
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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.springframework.util.CollectionUtils;

@Named
@CustomLog
@Getter
public class OpcodeTracer implements HederaOperationTracer {

    private final Map<Address, PrecompiledContract> hederaPrecompiles;
    private final MirrorNodeEvmProperties evmProperties;
    private final State mirrorNodeState;

    public OpcodeTracer(
            final PrecompiledContractProvider precompiledContractProvider,
            MirrorNodeEvmProperties evmProperties,
            State mirrorNodeState) {
        this.hederaPrecompiles = precompiledContractProvider.getHederaPrecompiles().entrySet().stream()
                .collect(Collectors.toMap(e -> Address.fromHexString(e.getKey()), Map.Entry::getValue));
        this.evmProperties = evmProperties;
        this.mirrorNodeState = mirrorNodeState;
    }

    @Override
    public void init(final MessageFrame frame) {
        getContext().incrementContractActionsCounter();
    }

    @Override
    public void tracePostExecution(final MessageFrame frame, final Operation.OperationResult operationResult) {
        ContractCallContext context = getContext();
        if (frame.getState() == CODE_SUSPENDED) {
            context.incrementContractActionsCounter();
        }
        OpcodeTracerOptions options = context.getOpcodeTracerOptions();
        final List<Bytes> memory = captureMemory(frame, options);
        final List<Bytes> stack = captureStack(frame, options);
        final Map<Bytes, Bytes> storage = captureStorage(frame, options);
        Opcode opcode = Opcode.builder()
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
                .gasCost(output != null ? gasRequirement : 0L)
                .depth(frame.getDepth())
                .stack(Collections.emptyList())
                .memory(Collections.emptyList())
                .storage(Collections.emptyMap())
                .reason(revertReason.map(Bytes::toHexString).orElse(null))
                .build();

        context.addOpcodes(opcode);
    }

    @Override
    public void traceAccountCreationResult(MessageFrame frame, Optional<ExceptionalHaltReason> haltReason) {
        if (haltReason.isPresent() && existsSyntheticActionForFrame(frame)) {
            getContext().incrementContractActionsCounter();
        }
    }

    @Override
    public void tracePrecompileResult(MessageFrame frame, ContractActionType type) {
        if (type.equals(PRECOMPILE) && frame.getState().equals(EXCEPTIONAL_HALT)) {
            // if an ETH precompile call exceptional halted, the action is already finalized
            return;
        }
        if (existsSyntheticActionForFrame(frame)) {
            getContext().incrementContractActionsCounter();
        }
    }

    private List<Bytes> captureMemory(final MessageFrame frame, OpcodeTracerOptions options) {
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

    private List<Bytes> captureStack(final MessageFrame frame, OpcodeTracerOptions options) {
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

            if (options.modularized) {
                return getModularizedUpdatedStorage(address);
            }

            return new TreeMap<>(account.getUpdatedStorage());
        } catch (final ModificationNotAllowedException e) {
            log.warn("Failed to retrieve storage contents", e);
            return Collections.emptyMap();
        }
    }

    private Optional<Bytes> getRevertReasonFromContractActions(ContractCallContext context) {
        List<ContractAction> contractActions = context.getContractActions();

        if (CollectionUtils.isEmpty(contractActions)) {
            return Optional.empty();
        }

        int currentActionIndex = context.getContractActionIndexOfCurrentFrame();

        return contractActions.stream()
                .filter(action -> action.hasRevertReason() && action.getIndex() == currentActionIndex)
                .map(action -> Bytes.of(action.getResultData()))
                .map(this::formatRevertReason)
                .findFirst();
    }

    public ContractCallContext getContext() {
        return ContractCallContext.get();
    }

    private boolean isCallToHederaPrecompile(MessageFrame frame) {
        Address recipientAddress = frame.getRecipientAddress();
        return hederaPrecompiles.containsKey(recipientAddress);
    }

    /**
     * When a contract tries to call a non-existing address (resulting in a
     * {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} failure), a synthetic action is created to record
     * this, otherwise the details of the intended call (e.g. the targeted invalid address) and sequence of events
     * leading to the failure are lost
     */
    private boolean existsSyntheticActionForFrame(MessageFrame frame) {
        return (frame.getState() == EXCEPTIONAL_HALT || frame.getState() == COMPLETED_FAILED)
                && frame.getType().equals(MESSAGE_CALL)
                && frame.getExceptionalHaltReason().isPresent()
                && frame.getExceptionalHaltReason().get().equals(INVALID_SOLIDITY_ADDRESS);
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

    private Map<Bytes, Bytes> getModularizedUpdatedStorage(Address accountAddress) {
        Map<Bytes, Bytes> storageUpdates = new TreeMap<>();
        MapWritableStates states = (MapWritableStates) mirrorNodeState.getWritableStates(ContractService.NAME);

        try {
            var accountContractID = EntityIdUtils.toContractID(accountAddress);
            var storageState = states.get(ContractStorageReadableKVState.KEY);
            storageState.modifiedKeys().stream()
                    .filter(SlotKey.class::isInstance)
                    .map(SlotKey.class::cast)
                    .filter(slotKey -> accountContractID.equals(slotKey.contractID()))
                    .forEach(slotKey -> {
                        SlotValue slotValue = (SlotValue) storageState.get(slotKey);
                        if (slotValue != null) {
                            storageUpdates.put(
                                    Bytes.wrap(slotKey.key().toByteArray()),
                                    Bytes.wrap(slotValue.value().toByteArray()));
                        }
                    });
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Failed to retrieve modified storage keys for service: {}, key: {}",
                    ContractService.NAME,
                    ContractStorageReadableKVState.KEY,
                    e);
        }

        return storageUpdates;
    }
}
