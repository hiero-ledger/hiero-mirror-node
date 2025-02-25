// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.mirror.web3.evm.config.PrecompiledContractProvider;
import jakarta.inject.Named;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

@Named
public class MirrorEvmMessageCallProcessorV30 extends AbstractEvmMessageCallProcessor {

    public MirrorEvmMessageCallProcessorV30(
            @Named("evm030") EVM v30,
            PrecompileContractRegistry precompiles,
            final PrecompiledContractProvider precompilesHolder,
            final GasCalculator gasCalculator,
            final Predicate<Address> systemAccountDetector) {
        super(v30, precompiles, precompilesHolder.getHederaPrecompiles(), systemAccountDetector);
        MainnetPrecompiledContracts.populateForIstanbul(precompiles, gasCalculator);
    }
}
