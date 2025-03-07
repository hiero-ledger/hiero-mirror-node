// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.service.mono.contracts.execution.traceability;

import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import com.hedera.services.stream.proto.ContractActionType;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Hedera-specific EVM operation tracer interface with added functionality for contract actions
 * traceability.
 */
public interface HederaOperationTracer extends HederaEvmOperationTracer {

    /**
     * Trace the result from a precompile execution. Must be called after the result has been
     * reflected in the associated message frame.
     *
     * @param frame the frame associated with this precompile call
     * @param type the type of precompile called; expected values are {@code PRECOMPILE} and {@code
     *     SYSTEM}
     */
    void tracePrecompileResult(final MessageFrame frame, final ContractActionType type);
}
