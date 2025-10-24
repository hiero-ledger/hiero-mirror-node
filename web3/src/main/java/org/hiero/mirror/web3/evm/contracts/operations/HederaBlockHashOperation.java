// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;

/**
 * Custom version of the Besu's BlockHashOperation class. The difference is
 * that in the mirror node we have the block hash values of all the blocks
 * so the restriction for the latest 256 blocks is removed. The latest
 * block value can be returned as well.
 */
@Named
public class HederaBlockHashOperation extends BlockHashOperation {
    /**
     * Instantiates a new Block hash operation.
     *
     * @param gasCalculator the gas calculator
     */
    public HederaBlockHashOperation(GasCalculator gasCalculator) {
        super(gasCalculator);
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final long cost = gasCalculator().getBlockHashOperationGasCost();
        if (frame.getRemainingGas() < cost) {
            return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
        }

        final Bytes blockArg = frame.popStackItem().trimLeadingZeros();
        // Short-circuit if value is unreasonably large
        if (blockArg.size() > 8) {
            frame.pushStackItem(UInt256.ZERO);
            return new OperationResult(cost, null);
        }

        final long soughtBlock = blockArg.toLong();
        final BlockValues blockValues = frame.getBlockValues();
        final long currentBlockNumber = blockValues.getNumber();
        final BlockHashLookup blockHashLookup = frame.getBlockHashLookup();

        if (currentBlockNumber <= 0 || soughtBlock > currentBlockNumber) {
            frame.pushStackItem(Bytes32.ZERO);
        } else {
            final Hash blockHash = blockHashLookup.apply(frame, soughtBlock);
            frame.pushStackItem(blockHash);
        }

        return new OperationResult(cost, null);
    }
}
