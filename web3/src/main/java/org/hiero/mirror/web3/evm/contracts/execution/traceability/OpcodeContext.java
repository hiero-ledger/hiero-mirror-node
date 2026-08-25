// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.service.model.OpcodeRequest;

/**
 * Properties for tracing opcodes
 */
@Data
public final class OpcodeContext {

    /**
     * Upper bound for the initial opcode list capacity. The list still grows as needed
     * for genuinely large traces, this only caps the up-front allocation so a single request cannot force a huge
     * backing array before any opcode executes.
     */
    private static final int MAX_INITIAL_OPCODES_CAPACITY = 10_000;

    /**
     * Name used for the marker opcode appended when a trace is truncated at maxOpcodes.
     */
    static final String TRUNCATED_OP = "TRUNCATED";

    /**
     * Immutable marker opcode appended once when a trace reaches maxOpcodes. Shared across requests since it carries no
     * per-request state and is never mutated after construction.
     */
    private static final Opcode TRUNCATED_OPCODE = new Opcode()
            .pc(0)
            .op(TRUNCATED_OP)
            .gas(0L)
            .gasCost(0L)
            .depth(0)
            .stack(List.of())
            .memory(List.of())
            .storage(Map.of())
            .reason("Trace truncated after reaching the configured maxOpcodes limit");

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

    private final OpcodesProperties properties;

    /**
     * Pre-transaction state timestamp for opcode replay ({@code consensusTimestamp - 1}). Used so entity/token/storage
     * reads exclude the replayed transaction's own writes.
     */
    private Optional<Long> previousBlockTimestamp = Optional.empty();

    /**
     * Running total of memory words captured so far across all recorded opcodes.
     */
    @Setter(AccessLevel.NONE)
    private long capturedMemoryWords;

    /**
     * Running total of stack items captured so far across all recorded opcodes.
     */
    @Setter(AccessLevel.NONE)
    private long capturedStack;

    /**
     * Running total of storage entries captured so far across all recorded opcodes.
     */
    @Setter(AccessLevel.NONE)
    private long capturedStorage;

    /**
     * Whether the trace has been truncated because the opcode-count or one of the memory/stack/storage budgets was
     * reached. Once set, the single {@link #TRUNCATED_OPCODE} marker has been appended and further opcodes are dropped.
     */
    @Setter(AccessLevel.NONE)
    private boolean truncated;

    /**
     * Total number of opcodes offered for this request, including the ones dropped once a budget was reached. Kept so
     * truncation can be reported without walking the opcode list.
     */
    @Setter(AccessLevel.NONE)
    private long executedOpcodes;

    public OpcodeContext(
            final OpcodeRequest opcodeRequest, final int initialOpcodesCapacity, final OpcodesProperties properties) {
        this.stack = opcodeRequest.isStack();
        this.memory = opcodeRequest.isMemory();
        this.storage = opcodeRequest.isStorage();
        this.properties = properties;
        this.opcodes = new ArrayList<>(Math.min(Math.max(initialOpcodesCapacity, 0), MAX_INITIAL_OPCODES_CAPACITY));
    }

    /**
     * Records an offered opcode. Every opcode is counted in {@link #executedOpcodes}. While within all budgets the opcode
     * is stored and its captured memory/stack/storage added to the running totals; once a budget would be exceeded (see
     * {@link #isAtCapacity()} and {@link #exceedsCaptureBudget(Opcode)}) the opcode is dropped and a single
     * {@link #TRUNCATED_OPCODE} marker is appended the first time truncation occurs so clients can detect it. Callers
     * that already know the cap is reached may pass {@code null} to avoid building an opcode that would be dropped.
     */
    public void addOpcodes(Opcode opcode) {
        executedOpcodes++;
        if (isAtCapacity() || exceedsCaptureBudget(opcode)) {
            if (!truncated) {
                truncated = true;
                opcodes.add(TRUNCATED_OPCODE);
            }
            return;
        }
        opcodes.add(opcode);
        capturedMemoryWords += size(opcode.getMemory());
        capturedStack += size(opcode.getStack());
        capturedStorage += size(opcode.getStorage());
    }

    /**
     * Whether recording another opcode would exceed one of the configured budgets: the opcode count, or the cumulative
     * memory/stack/storage captured so far. Once true the trace is truncated and no further opcodes are recorded.
     */
    public boolean isAtCapacity() {
        return truncated || opcodes.size() + 1 >= properties.getMaxOpcodes();
    }

    private boolean exceedsCaptureBudget(final Opcode opcode) {
        return opcode != null
                && (capturedMemoryWords + size(opcode.getMemory()) > properties.getMaxMemoryWords()
                        || capturedStack + size(opcode.getStack()) > properties.getMaxStack()
                        || capturedStorage + size(opcode.getStorage()) > properties.getMaxStorage());
    }

    private static int size(final List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static int size(final Map<?, ?> map) {
        return map == null ? 0 : map.size();
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
