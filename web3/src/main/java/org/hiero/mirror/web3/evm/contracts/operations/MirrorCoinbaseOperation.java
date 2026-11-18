// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CoinbaseOperation;

/** {@code COINBASE} that returns {@code block_override.fee_recipient} when it is set. */
@Named
final class MirrorCoinbaseOperation extends CoinbaseOperation implements VersionedOperation {

    MirrorCoinbaseOperation(final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult executeFixedCostOperation(final MessageFrame frame) {
        final var feeRecipient = ContractCallContext.getEvmBlockOverrideSafe().feeRecipient();
        if (feeRecipient == null) {
            return super.executeFixedCostOperation(frame);
        }
        frame.pushStackItem(feeRecipient.getBytes());
        return successResponse;
    }

    @Override
    public SemanticVersion minimumEvmVersion() {
        return EVM_VERSION_0_30;
    }
}
