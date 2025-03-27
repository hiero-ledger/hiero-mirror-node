// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Options for tracing opcodes
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class OpcodeTracerOptions {

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

    /**
     * Modularized or mono workflow
     */
    boolean modularized;

    public OpcodeTracerOptions() {
        this(true, false, false, true);
    }

    public OpcodeTracerOptions(boolean stack, boolean memory, boolean storage) {
        this(stack, memory, storage, true);
    }
}
