// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

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
        MutableBytes memory,
        Map<Bytes, Bytes> storage,
        String frameRevertReason) {}
