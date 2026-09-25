// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import jakarta.inject.Named;
import java.util.concurrent.Semaphore;
import org.hiero.mirror.web3.controller.OpcodesProperties;

/** Shared byte budget across all in-flight opcode traces; see {@link OpcodeContext#addOpcodes}. */
@Named
public class TraceMemoryBudget {

    private final Semaphore budget;

    public TraceMemoryBudget(final OpcodesProperties properties) {
        this.budget = new Semaphore(properties.getMaxConcurrentTraceBytes());
    }

    /** A fresh, never-shared budget, so unrelated usage can never exhaust it. */
    static TraceMemoryBudget unlimited() {
        return new TraceMemoryBudget(unlimitedProperties());
    }

    private static OpcodesProperties unlimitedProperties() {
        final var properties = new OpcodesProperties();
        properties.setMaxConcurrentTraceBytes(Integer.MAX_VALUE);
        return properties;
    }

    boolean tryReserve(final int bytes) {
        return bytes <= 0 || budget.tryAcquire(bytes);
    }

    void release(final int bytes) {
        if (bytes > 0) {
            budget.release(bytes);
        }
    }
}
