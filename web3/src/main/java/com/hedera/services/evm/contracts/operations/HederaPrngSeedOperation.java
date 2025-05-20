// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.evm.contracts.operations;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import com.hedera.services.txns.util.PrngLogic;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

public class HederaPrngSeedOperation extends AbstractOperation {

    private final OperationResult successResponse;
    private final OperationResult oogResponse;
    private final PrngLogic prngLogic;

    private final long gasCost;

    public HederaPrngSeedOperation(GasCalculator gasCalculator, PrngLogic prngLogic) {
        super(0x44, "PRNGSEED", 0, 1, gasCalculator);
        this.prngLogic = prngLogic;
        this.gasCost = gasCalculator.getBaseTierGasCost();
        this.successResponse = new OperationResult(gasCost, null);
        this.oogResponse = new OperationResult(gasCost, INSUFFICIENT_GAS);
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        if (frame.getRemainingGas() < gasCost) {
            return oogResponse;
        }
        Bytes seed = Bytes.wrap(prngLogic.getLatestRecordRunningHashBytes());
        if (seed.size() > Bytes32.SIZE) {
            frame.pushStackItem(seed.slice(0, Bytes32.SIZE));
        } else {
            frame.pushStackItem(seed);
        }
        return successResponse;
    }
}
