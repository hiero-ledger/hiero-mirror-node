// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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

    // Prevents over-allocation when gas limit is large (e.g. 15M gas / 3 = 5M entries = ~40MB).
    // Most transactions produce far fewer opcodes; the list will grow as needed.
    private static final int MAX_INITIAL_OPCODE_CAPACITY = 10_000;

    @Builder.Default
    private List<ContractAction> actions = List.of();

    private List<Opcode> opcodes;

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

    // Cached storage snapshot to avoid rebuilding TreeMap on every opcode in captureStorage().
    // lastStorageAccessCount tracks the total number of storage accesses seen when the snapshot
    // was last built; if the count hasn't changed there is no need to rebuild.
    @Builder.Default
    private int lastStorageAccessCount = -1;

    @Builder.Default
    private Map<String, String> cachedStorageSnapshot = Collections.emptyMap();

    public OpcodeContext(final OpcodeRequest opcodeRequest, final int opcodesSize) {
        this.stack = opcodeRequest.isStack();
        this.memory = opcodeRequest.isMemory();
        this.storage = opcodeRequest.isStorage();
        this.opcodes = new ArrayList<>(Math.min(opcodesSize, MAX_INITIAL_OPCODE_CAPACITY));
        this.cachedStorageSnapshot = Collections.emptyMap();
    }

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
    }
}
