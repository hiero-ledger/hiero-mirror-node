// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.evm.contracts.operations;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 *
 * <p>Performs an existence check on the beneficiary {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist, or it is
 * deleted.
 *
 * <p>Halts the execution of the EVM transaction with {@link
 * HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the beneficiary address is the same as the address being
 * destructed.
 */
public class HederaSelfDestructOperationV050 extends HederaSelfDestructOperationV046 {

    public HederaSelfDestructOperationV050(
            GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Predicate<Address> systemAccountDetector) {
        super(gasCalculator, addressValidator, systemAccountDetector, true);
    }
}
