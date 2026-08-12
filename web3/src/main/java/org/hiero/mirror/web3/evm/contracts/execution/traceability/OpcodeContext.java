// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.service.model.OpcodeRequest;

/**
 * Properties for tracing opcodes
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public final class OpcodeContext {

    /**
     * Upper bound for the initial opcode list capacity. The list still grows as needed
     * for genuinely large traces, this only caps the up-front allocation so a single request cannot force a huge
     * backing array before any opcode executes.
     */
    private static final int MAX_INITIAL_OPCODES_CAPACITY = 10_000;

    /**
     * Default value is applied when a limit is not supplied. Production
     * requests receive the configured values from {@code OpcodesProperties}.
     */
    static final int DEFAULT_MAX_OPCODES = 100_000;

    static final int DEFAULT_MAX_MEMORY_WORDS_PER_OPCODE = 2_048;

    static final int DEFAULT_MAX_STACK_ITEMS_PER_OPCODE = 1_024;

    static final int DEFAULT_MAX_STORAGE_ENTRIES_PER_OPCODE = 1_024;

    /**
     * Name used for the marker opcode appended when a trace is truncated at maxOpcodes.
     */
    static final String TRUNCATED_OP = "TRUNCATED";

    /**
     * Actions pre-grouped by call depth and sorted by index within each depth.
     * Populated once via {@link #setActions(List)} to avoid repeated filtering and sorting.
     */
    @Setter(AccessLevel.NONE)
    private Map<Integer, List<ContractAction>> actionsByDepth = new HashMap<>();

    private List<Opcode> opcodes;

    /**
     * Per-depth counter of system contract calls seen so far at each call depth.
     * Used to correlate EVM re-execution system calls with preloaded reverted sidecar actions.
     */
    @Setter(AccessLevel.NONE)
    private Map<Integer, Integer> precompileCallCountByDepth = new HashMap<>();

    private long gasRemaining;

    private RootProxyWorldUpdater rootProxyWorldUpdater;

    /**
     * Include stack information
     */
    private final boolean stack;

    /**
     * Include memory information
     */
    private final boolean memory;

    /**
     * Include storage information
     */
    private final boolean storage;

    /**
     * Maximum number of opcodes recorded for this request.
     */
    private final int maxOpcodes;

    /**
     * Maximum number of 32-byte memory words captured per opcode.
     */
    private final int maxMemoryWordsPerOpcode;

    /**
     * Maximum number of stack items captured per opcode.
     */
    private final int maxStackItemsPerOpcode;

    /**
     * Maximum number of storage entries captured per opcode.
     */
    private final int maxStorageEntriesPerOpcode;

    /**
     * Total number of opcodes offered for this request, including the ones dropped once {@link #maxOpcodes} was
     * reached. Kept so truncation can be reported (see {@link #isTruncated()}) without walking the opcode list.
     */
    @Setter(AccessLevel.NONE)
    private long executedOpcodes;

    public OpcodeContext(final OpcodeRequest opcodeRequest, final int initialOpcodesCapacity) {
        this(
                opcodeRequest,
                initialOpcodesCapacity,
                DEFAULT_MAX_OPCODES,
                DEFAULT_MAX_MEMORY_WORDS_PER_OPCODE,
                DEFAULT_MAX_STACK_ITEMS_PER_OPCODE,
                DEFAULT_MAX_STORAGE_ENTRIES_PER_OPCODE);
    }

    public OpcodeContext(
            final OpcodeRequest opcodeRequest,
            final int initialOpcodesCapacity,
            final int maxOpcodes,
            final int maxMemoryWordsPerOpcode) {
        this(
                opcodeRequest,
                initialOpcodesCapacity,
                maxOpcodes,
                maxMemoryWordsPerOpcode,
                DEFAULT_MAX_STACK_ITEMS_PER_OPCODE,
                DEFAULT_MAX_STORAGE_ENTRIES_PER_OPCODE);
    }

    public OpcodeContext(
            final OpcodeRequest opcodeRequest,
            final int initialOpcodesCapacity,
            final int maxOpcodes,
            final int maxMemoryWordsPerOpcode,
            final int maxStackItemsPerOpcode,
            final int maxStorageEntriesPerOpcode) {
        this.stack = opcodeRequest.isStack();
        this.memory = opcodeRequest.isMemory();
        this.storage = opcodeRequest.isStorage();
        this.maxOpcodes = maxOpcodes;
        this.maxMemoryWordsPerOpcode = maxMemoryWordsPerOpcode;
        this.maxStackItemsPerOpcode = maxStackItemsPerOpcode;
        this.maxStorageEntriesPerOpcode = maxStorageEntriesPerOpcode;
        this.opcodes = new ArrayList<>(Math.min(Math.max(initialOpcodesCapacity, 0), MAX_INITIAL_OPCODES_CAPACITY));
    }

    public void addOpcodes(Opcode opcode) {
        if (opcodes.size() < maxOpcodes) {
            executedOpcodes++;
            opcodes.add(opcode);
        } else {
            addTruncatedOpcode();
        }
    }

    /**
     * Whether the recorded-opcode list has reached maxOpcodes.
     */
    public boolean isAtCapacity() {
        return opcodes.size() >= maxOpcodes;
    }

    /**
     * Append a marker to the opcodes list when maxOpcodes is reached. Still counted in {@link #executedOpcodes}
     * so the caller can report how much was omitted.
     */
    public void addTruncatedOpcode() {
        executedOpcodes++;
        // Append a single marker so clients can detect the trace was truncated; further opcodes add nothing to the
        // list.
        if (opcodes.size() == maxOpcodes) {
            opcodes.add(truncationMarker());
        }
    }

    /**
     * Whether the trace was truncated because the number of executed opcodes exceeded {@link #maxOpcodes}.
     */
    public boolean isTruncated() {
        return executedOpcodes > maxOpcodes;
    }

    private Opcode truncationMarker() {
        return new Opcode()
                .pc(0)
                .op(TRUNCATED_OP)
                .gas(0L)
                .gasCost(0L)
                .depth(0)
                .stack(List.of())
                .memory(List.of())
                .storage(Map.of())
                .reason("Trace truncated after %d opcodes".formatted(maxOpcodes));
    }

    /**
     * Groups the given actions by call depth and sorts each group by index.
     * This pre-processing is done once so that {@link #consumeNextFailedActionAtDepth(int)} is a simple lookup.
     */
    public void setActions(final List<ContractAction> actions) {
        for (final var action : actions) {
            actionsByDepth
                    .computeIfAbsent(action.getCallDepth(), _ -> new ArrayList<>())
                    .add(action);
        }
        for (final var list : actionsByDepth.values()) {
            list.sort(Comparator.comparingInt(ContractAction::getIndex));
        }
    }

    /**
     * Returns the reverted sidecar {@link ContractAction} that corresponds to the n-th system-contract
     * call at the given {@code depth}, where n is the current per-depth call counter, or {@code null}
     * if no such action exists (i.e., the call succeeded or no actions were loaded for that depth).
     * <p>
     * This method advances the per-depth call counter as a side effect.
     *
     * @param depth the EVM call depth at the time of the system-contract invocation
     * @return the matching reverted action, or {@code null}
     */
    public ContractAction consumeNextFailedActionAtDepth(final int depth) {
        // Increments the system-contract call counter for the given call depth and returns the previous value
        // (i.e., the 0-based position of the current call among all system-contract calls seen so far at that depth).
        final var counter = precompileCallCountByDepth.merge(depth, 1, Integer::sum) - 1;
        final var actionsAtDepth = actionsByDepth.getOrDefault(depth, List.of());
        if (counter >= actionsAtDepth.size()) {
            return null;
        }
        return actionsAtDepth.get(counter);
    }
}
