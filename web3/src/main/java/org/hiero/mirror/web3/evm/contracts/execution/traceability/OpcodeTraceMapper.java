// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.Opcode;

/**
 * Converts compact {@link OpcodeTraceEntry} rows to OpenAPI {@link Opcode} models (hex strings) in one pass.
 */
public final class OpcodeTraceMapper {

    private OpcodeTraceMapper() {}

    public static List<Opcode> toApiOpcodes(final List<OpcodeTraceEntry> entries, final HexStringPool pool) {
        if (entries.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<Opcode>(entries.size());
        for (var e : entries) {
            out.add(toApiOpcode(e, pool));
        }
        return out;
    }

    private static Opcode toApiOpcode(final OpcodeTraceEntry e, final HexStringPool pool) {
        var apiReason = e.systemContractRevertReason();
        if (apiReason == null && e.frameRevertReason() != null) {
            apiReason = pool.hex(e.frameRevertReason());
        }
        return new Opcode()
                .pc(e.pc())
                .op(e.op())
                .gas(e.gas())
                .gasCost(e.gasCost())
                .depth(e.depth())
                .stack(toHexList(e.stack(), pool))
                .memory(toHexList(e.memory(), pool))
                .storage(toHexStorage(e.storage(), pool))
                .reason(apiReason);
    }

    private static List<String> toHexList(final List<Bytes> bytes, final HexStringPool pool) {
        if (bytes.isEmpty()) {
            return List.of();
        }
        var list = new ArrayList<String>(bytes.size());
        for (var b : bytes) {
            list.add(pool.hex(b));
        }
        return list;
    }

    /**
     * Hex-encodes keys and values. Input is expected to be a {@link java.util.TreeMap} so iteration order matches the
     * previous string-key {@link java.util.TreeMap} ordering.
     */
    private static Map<String, String> toHexStorage(final Map<Bytes, Bytes> storage, final HexStringPool pool) {
        if (storage.isEmpty()) {
            return Map.of();
        }
        var out = new LinkedHashMap<String, String>(storage.size());
        for (var en : storage.entrySet()) {
            out.put(pool.hex(en.getKey()), pool.hex(en.getValue()));
        }
        return out;
    }
}
