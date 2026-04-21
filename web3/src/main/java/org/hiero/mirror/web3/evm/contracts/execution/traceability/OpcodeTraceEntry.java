// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jspecify.annotations.Nullable;

/**
 * Opcode trace row before REST hex string materialization. Uses compact binary fields during EVM replay.
 */
public record OpcodeTraceEntry(
        int pc,
        String op,
        long gas,
        long gasCost,
        int depth,
        List<Bytes> stack,
        List<Bytes> memory,
        Map<Bytes, Bytes> storage,
        /**
         * Raw revert data from the Besu frame when present (non–system-contract precompile path uses this).
         */
        @Nullable Bytes frameRevertReason,
        /**
         * Fully formatted revert string from contract actions (system contract precompile path only).
         */
        @Nullable String systemContractRevertReason) {}
