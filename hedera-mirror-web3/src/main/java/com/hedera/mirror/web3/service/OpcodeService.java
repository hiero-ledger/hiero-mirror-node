// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import com.hedera.mirror.rest.model.OpcodesResponse;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.springframework.lang.NonNull;

public interface OpcodeService {

    /**
     * @param transactionIdOrHash the {@link TransactionIdOrHashParameter}
     * @param options the {@link OpcodeTracerOptions}
     * @param isModularized whether the opcode call will follow the modularized or the mono
     *                      txn execution workflow
     * @return the {@link OpcodesResponse} holding the result of the opcode call
     */
    OpcodesResponse processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash,
            @NonNull OpcodeTracerOptions options,
            boolean isModularized);
}
