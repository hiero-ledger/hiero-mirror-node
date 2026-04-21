// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.service.model.OpcodeRequest;

/**
 * Properties for tracing opcodes
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
public final class OpcodeContext {

    @Builder.Default
    private List<ContractAction> actions = List.of();

    /**
     * Compact trace rows accumulated during EVM replay. Cleared in {@link #finalizeTrace(HexStringPool)}.
     */
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private List<OpcodeTraceEntry> traceEntries = new ArrayList<>();

    /**
     * REST opcodes after {@link #finalizeTrace(HexStringPool)}. When unset, {@link #getOpcodes()} maps from
     * {@link #traceEntries} for test and diagnostic use.
     */
    private List<Opcode> mappedOpcodes;

    @Builder.Default
    private long gasRemaining = 0L;

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

    public static OpcodeContext forTracing(final OpcodeRequest opcodeRequest, final int opcodesSize) {
        return OpcodeContext.builder()
                .actions(List.of())
                .traceEntries(new ArrayList<>(opcodesSize))
                .stack(opcodeRequest.isStack())
                .memory(opcodeRequest.isMemory())
                .storage(opcodeRequest.isStorage())
                .build();
    }

    public void addOpcodes(final OpcodeTraceEntry opcode) {
        traceEntries.add(opcode);
    }

    /**
     * Materializes REST {@link Opcode} models and releases compact trace rows.
     */
    public void finalizeTrace(final HexStringPool pool) {
        this.mappedOpcodes = OpcodeTraceMapper.toApiOpcodes(traceEntries, pool);
        this.traceEntries.clear();
    }

    public List<Opcode> getOpcodes() {
        if (mappedOpcodes != null) {
            return mappedOpcodes;
        }
        return OpcodeTraceMapper.toApiOpcodes(traceEntries, new HexStringPool());
    }
}
