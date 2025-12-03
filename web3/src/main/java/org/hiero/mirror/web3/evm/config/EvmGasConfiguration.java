// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lightweight configuration that provides the Besu GasCalculator bean.
 *
 * Isolated from EvmConfiguration to avoid circular initialization paths during
 * Spring context bootstrap.
 */
@Configuration(proxyBeanMethods = false)
public class EvmGasConfiguration {

    @Bean
    public GasCalculator provideGasCalculator() {
        return new CustomGasCalculator();
    }
}
