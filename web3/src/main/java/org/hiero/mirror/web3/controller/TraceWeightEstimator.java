// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Estimates the worst-case heap bytes an {@code /opcodes} trace can retain, from the per-trace budgets and the
 * requested {@code stack}/{@code memory}/{@code storage} flags. An unrecognized flag value counts as enabled.
 */
record TraceWeightEstimator(int baseOpcodeBytes, int memoryBytes, int stackBytes, int storageBytes) {

    private static final int HEX_STRING_BYTES_PER_ITEM = 117;
    private static final int STORAGE_ENTRY_BYTES = 234;
    private static final int BASE_OPCODE_BYTES = 100;

    static TraceWeightEstimator of(final OpcodesProperties properties) {
        return new TraceWeightEstimator(
                properties.getMaxOpcodes() * BASE_OPCODE_BYTES,
                properties.getMaxMemoryWords() * HEX_STRING_BYTES_PER_ITEM,
                properties.getMaxStack() * HEX_STRING_BYTES_PER_ITEM,
                properties.getMaxStorage() * STORAGE_ENTRY_BYTES);
    }

    int estimate(final HttpServletRequest request) {
        var weight = baseOpcodeBytes;
        if (flag(request, "stack", true)) {
            weight += stackBytes;
        }
        if (flag(request, "memory", false)) {
            weight += memoryBytes;
        }
        if (flag(request, "storage", false)) {
            weight += storageBytes;
        }
        return weight;
    }

    private static boolean flag(final HttpServletRequest request, final String name, final boolean defaultValue) {
        final var value = request.getParameter(name);
        return value == null ? defaultValue : !"false".equalsIgnoreCase(value);
    }
}
