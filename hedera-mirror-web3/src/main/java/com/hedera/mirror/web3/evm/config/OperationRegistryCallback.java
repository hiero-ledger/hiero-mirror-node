// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.config;

import java.math.BigInteger;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.OperationRegistry;

@FunctionalInterface
public interface OperationRegistryCallback {
    void register(OperationRegistry registry, GasCalculator gasCalculator, BigInteger chainId);
}
