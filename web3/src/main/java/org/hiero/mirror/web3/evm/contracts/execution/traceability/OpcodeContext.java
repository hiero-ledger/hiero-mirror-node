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
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.viewmodel.TracerConfig;

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

    private TracerConfig tracerConfig;

    public OpcodeContext(final OpcodeRequest opcodeRequest, final int opcodesSize) {
        this.tracerConfig = TracerConfig.builder()
                .stack(opcodeRequest.isStack())
                .memory(opcodeRequest.isMemory())
                .storage(opcodeRequest.isStorage())
                .tracerType(TracerType.OPCODE)
                .build();
        this.opcodes = new ArrayList<>(opcodesSize);
    }

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
    }
}
