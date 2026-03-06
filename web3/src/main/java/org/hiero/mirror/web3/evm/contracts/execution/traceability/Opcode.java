// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record Opcode(
        int pc,
        String op,
        long gas,
        @JsonProperty("gas_cost") long gasCost,
        int depth,
        List<String> stack,
        List<String> memory,
        Map<String, String> storage,
        String reason) {}
