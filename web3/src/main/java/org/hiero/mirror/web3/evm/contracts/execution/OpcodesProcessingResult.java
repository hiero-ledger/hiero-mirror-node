// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hiero.mirror.web3.EvmTransactionResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;
import org.hyperledger.besu.datatypes.Address;

public record OpcodesProcessingResult(
        @NotNull EvmTransactionResult transactionProcessingResult,
        @NotNull Address recipient,
        @NotNull List<Opcode> opcodes) {}
