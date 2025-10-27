// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.jspecify.annotations.NonNull;

public interface OpcodeService {

    /**
     * @param transactionIdOrHash the {@link TransactionIdOrHashParameter}
     * @param options the {@link OpcodeTracerOptions}
     * @return the {@link OpcodesResponse} holding the result of the opcode call
     */
    OpcodesResponse processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash, @NonNull OpcodeTracerOptions options);
}
