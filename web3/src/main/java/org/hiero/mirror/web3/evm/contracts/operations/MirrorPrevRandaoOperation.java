// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.contract.impl.exec.operations.CustomPrevRandaoOperation;
import jakarta.inject.Named;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Hedera's {@code PREVRANDAO} that returns {@code block_override.prev_randao}, zero-extended to 32 bytes, when it is
 * set. Paris (v0.34) and later; earlier versions keep {@code DIFFICULTY}.
 */
@Named
final class MirrorPrevRandaoOperation extends CustomPrevRandaoOperation implements VersionedOperation {

    MirrorPrevRandaoOperation(final GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final var prevRandao = ContractCallContext.get().getBlockOverride().prevRandaoBytes();
        if (prevRandao == null) {
            return super.execute(frame, evm);
        }
        final long gasCost = gasCalculator().getBaseTierGasCost();
        if (frame.getRemainingGas() < gasCost) {
            return new OperationResult(gasCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
        }
        frame.pushStackItem(prevRandao);
        return new OperationResult(gasCost, null);
    }

    @Override
    public SemanticVersion minimumEvmVersion() {
        return EVM_VERSION_0_34;
    }
}
