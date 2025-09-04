// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import jakarta.annotation.Nonnull;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.Operation;

public class HederaCustomCallOperation extends CallOperation {
    private static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

    public HederaCustomCallOperation(@Nonnull final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult execute(@Nonnull final MessageFrame frame, @Nonnull final EVM evm) {
        try {
            final long cost = cost(frame, false);
            if (frame.getRemainingGas() < cost) {
                return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
            }

            return super.execute(frame, evm);
        } catch (final UnderflowException ignore) {
            return UNDERFLOW_RESPONSE;
        }
    }
}
