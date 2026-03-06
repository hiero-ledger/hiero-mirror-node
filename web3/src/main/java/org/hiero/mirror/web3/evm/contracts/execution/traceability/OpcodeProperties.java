// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.common.domain.contract.ContractAction;

/**
 * Properties for tracing opcodes
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class OpcodeProperties {

    @Builder.Default
    private List<ContractAction> contractActions = List.of();

    @Builder.Default
    private List<Opcode> opcodes = new ArrayList<>();

    private long gasRemaining;

    private RootProxyWorldUpdater rootProxyWorldUpdater;

    /**
     * Include stack information
     */
    boolean stack;

    /**
     * Include memory information
     */
    boolean memory;

    /**
     * Include storage information
     */
    boolean storage;

    public OpcodeProperties() {
        this.stack = true;
        this.memory = false;
        this.storage = false;
    }

    public OpcodeProperties(boolean stack, boolean memory, boolean storage) {
        this.stack = stack;
        this.memory = memory;
        this.storage = storage;
    }
}
