// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;

public record OpcodesProcessingResult(
        @NotNull HederaEvmTransactionProcessingResult transactionProcessingResult, @NotNull List<Opcode> opcodes) {}
