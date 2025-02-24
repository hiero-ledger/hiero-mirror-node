// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.mirror.web3.evm.contracts.execution.traceability.Opcode;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OpcodesProcessingResult(
        @NotNull HederaEvmTransactionProcessingResult transactionProcessingResult, @NotNull List<Opcode> opcodes) {}
