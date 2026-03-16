// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.rest.model.Opcode;

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

    public OpcodeContext(boolean stack, boolean memory, boolean storage, int opcodesSize) {
        this.stack = stack;
        this.memory = memory;
        this.storage = storage;
        this.opcodes = new ArrayList<>(opcodesSize);
    }

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
    }
}
