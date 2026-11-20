// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.GasLimitOperation;

/**
 * {@code GASLIMIT} that returns {@code block_override.gas_limit} when it is set. The override changes only the value
 * the contract observes, not the gas available to the call.
 */
@Named
final class MirrorGasLimitOperation extends GasLimitOperation implements VersionedOperation {

    MirrorGasLimitOperation(final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult executeFixedCostOperation(final MessageFrame frame) {
        final var gasLimit = ContractCallContext.getBlockOverrideSafe().gasLimit();
        if (gasLimit == null) {
            return super.executeFixedCostOperation(frame);
        }
        frame.pushStackItem(Words.longBytes(gasLimit));
        return successResponse;
    }

    @Override
    public SemanticVersion minimumEvmVersion() {
        return EVM_VERSION_0_30;
    }
}
