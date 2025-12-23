// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

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

    public OpcodeTracerOptions() {
        this.stack = true;
        this.memory = false;
        this.storage = false;
    }
}
