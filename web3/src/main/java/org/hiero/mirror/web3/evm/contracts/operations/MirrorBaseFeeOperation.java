// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BaseFeeOperation;

/**
 * {@code BASEFEE} that returns {@code block_override.base_fee_per_gas} when it is set. The override changes only the
 * value the contract observes, not the gas price charged for the call.
 */
@Named
final class MirrorBaseFeeOperation extends BaseFeeOperation implements VersionedOperation {

    MirrorBaseFeeOperation(final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult executeFixedCostOperation(final MessageFrame frame) {
        final var baseFee = ContractCallContext.get().getBlockOverride().baseFeePerGas();
        if (baseFee == null) {
            return super.executeFixedCostOperation(frame);
        }
        frame.pushStackItem(Bytes.ofUnsignedLong(baseFee));
        return successResponse;
    }

    @Override
    public SemanticVersion minimumEvmVersion() {
        return EVM_VERSION_0_30;
    }
}
