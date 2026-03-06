// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodesResponseDto;
import org.jspecify.annotations.NonNull;

public interface OpcodeService {

    /**
     * @param transactionIdOrHash the {@link TransactionIdOrHashParameter}
     * @param options the {@link OpcodeProperties}
     * @return the {@link OpcodesResponseDto} holding the result of the opcode call
     */
    OpcodesResponseDto processOpcodeCall(
            @NonNull TransactionIdOrHashParameter transactionIdOrHash, @NonNull OpcodeProperties options);
}
