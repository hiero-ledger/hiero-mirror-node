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

    @Builder.Default
    private List<ContractAction> actions = List.of();

    private List<Opcode> opcodes;

    /**
     * Per-depth counter of system contract calls seen so far at each call depth.
     * Used to correlate EVM re-execution system calls with preloaded reverted sidecar actions.
     */
    @Setter(AccessLevel.NONE)
    @Builder.Default
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

    public OpcodeContext(final OpcodeRequest opcodeRequest, final int opcodesSize) {
        this.stack = opcodeRequest.isStack();
        this.memory = opcodeRequest.isMemory();
        this.storage = opcodeRequest.isStorage();
        this.opcodes = new ArrayList<>(opcodesSize);
        this.precompileCallCountByDepth = new HashMap<>();
    }

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
    }

    /**
     * Increments the system-contract call counter for the given call depth and returns the
     * previous value (i.e., the 0-based position of the current call among all system-contract
     * calls seen so far at that depth).
     */
    public int incrementPrecompileCallCountAtDepth(final int depth) {
        return precompileCallCountByDepth.merge(depth, 1, Integer::sum) - 1;
    }

    /**
     * Returns the reverted sidecar {@link ContractAction} that corresponds to the n-th system-contract
     * call at the given {@code depth}, where n is the current per-depth call counter, or {@code null}
     * if no such action exists (i.e., the call succeeded or no actions were loaded for that depth).
     *
     * @param depth the EVM call depth at the time of the system-contract invocation
     * @return the matching reverted action, or {@code null}
     */
    public ContractAction getNextFailedActionAtDepth(final int depth) {
        final int counter = incrementPrecompileCallCountAtDepth(depth);
        final var actionsAtDepth = actions.stream()
                .filter(a -> a.getCallDepth() == depth)
                .sorted(Comparator.comparingInt(ContractAction::getIndex))
                .toList();
        if (counter >= actionsAtDepth.size()) {
            return null;
        }
        return actionsAtDepth.get(counter);
    }
}
