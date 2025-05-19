// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.apache.tuweni.bytes.Bytes;

@Builder
public record Opcode(
        int pc,
        String op,
        long gas,
        long gasCost,
        int depth,
        List<Bytes> stack,
        List<Bytes> memory,
        Map<Bytes, Bytes> storage,
        String reason) {}
