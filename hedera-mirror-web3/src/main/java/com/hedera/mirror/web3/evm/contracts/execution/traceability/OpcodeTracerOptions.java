// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

/**
 * Options for tracing opcodes
 */
@Data
@Value
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
        this(true, false, false);
    }
}
