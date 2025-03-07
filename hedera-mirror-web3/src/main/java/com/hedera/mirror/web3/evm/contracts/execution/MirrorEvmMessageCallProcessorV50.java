// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.mirror.web3.evm.config.PrecompiledContractProvider;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import jakarta.inject.Named;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

@Named
@SuppressWarnings("java:S110")
public class MirrorEvmMessageCallProcessorV50 extends MirrorEvmMessageCallProcessor {
    public MirrorEvmMessageCallProcessorV50(
            final AbstractAutoCreationLogic autoCreationLogic,
            final EntityAddressSequencer entityAddressSequencer,
            @Named("evm050") EVM v50,
            final PrecompileContractRegistry precompiles,
            final PrecompiledContractProvider precompilesHolder,
            final GasCalculator gasCalculator,
            final Predicate<Address> systemAccountDetector) {
        super(
                autoCreationLogic,
                entityAddressSequencer,
                v50,
                precompiles,
                precompilesHolder,
                gasCalculator,
                systemAccountDetector);

        MainnetPrecompiledContracts.populateForCancun(precompiles, gasCalculator);
    }
}
