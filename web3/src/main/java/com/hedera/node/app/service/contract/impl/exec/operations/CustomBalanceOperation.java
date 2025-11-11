// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractRequired;
import static com.hedera.node.app.service.contract.impl.exec.utils.OperationUtils.isDeficientGas;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.exec.utils.InvalidAddressContext;
import org.apache.tuweni.units.bigints.UInt256;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.BalanceOperation;
import org.jspecify.annotations.NullMarked;

/**
 * A Hedera customization of the Besu {@link org.hyperledger.besu.evm.operation.BalanceOperation}.
 */
@NullMarked
public class CustomBalanceOperation extends BalanceOperation {
    private final AddressChecks addressChecks;
    private final FeatureFlags featureFlags;

    public CustomBalanceOperation(
            final GasCalculator gasCalculator, final AddressChecks addressChecks, final FeatureFlags featureFlags) {
        super(gasCalculator);
        this.addressChecks = addressChecks;
        this.featureFlags = featureFlags;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        try {
            // NOTE: This line is the ONLY modification from the upstream class.
            // It sets a flag indicating a balance read, allowing custom behavior downstream.
            // The rest of this method is an exact copy of the upstream implementation.
            ContractCallContext.get().setBalanceCall(true);
            final long cost = cost(false);
            if (isDeficientGas(frame, cost)) {
                return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
            }
            // Make system contract balance invisible to EVM (added in v0.38)
            final var address = Words.toAddress(frame.getStackItem(0));
            if (addressChecks.isSystemAccount(address)) {
                frame.popStackItem();
                frame.pushStackItem(UInt256.ZERO);
                return new OperationResult(cost, null);
            }
            // Otherwise continue to enforce existence checks for backward compatibility
            if (contractRequired(frame, address, featureFlags) && !addressChecks.isPresent(address, frame)) {
                FrameUtils.invalidAddressContext(frame)
                        .set(address, InvalidAddressContext.InvalidAddressType.NonCallTarget);
                return new OperationResult(cost, INVALID_SOLIDITY_ADDRESS);
            }
            return super.execute(frame, evm);
        } catch (UnderflowException ignore) {
            return new OperationResult(cost(false), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        }
    }
}
