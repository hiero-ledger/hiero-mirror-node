// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.config;

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract.EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PRNG_PRECOMPILE_ADDRESS;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

@Named
@Getter
public class PrecompilesHolder implements PrecompiledContractProvider {

    public final Map<String, PrecompiledContract> hederaPrecompiles;

    PrecompilesHolder(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final PrngSystemPrecompiledContract prngSystemPrecompiledContract,
            final GasCalculator gasCalculator,
            final BasicHbarCentExchange basicHbarCentExchange,
            final HTSPrecompiledContract htsPrecompiledContract) {
        hederaPrecompiles = new HashMap<>();
        hederaPrecompiles.put(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS, htsPrecompiledContract);
        hederaPrecompiles.put(PRNG_PRECOMPILE_ADDRESS, prngSystemPrecompiledContract);
        hederaPrecompiles.put(
                EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS,
                new ExchangeRatePrecompiledContract(
                        gasCalculator, basicHbarCentExchange, mirrorNodeEvmProperties, Instant.now()));
    }
}
