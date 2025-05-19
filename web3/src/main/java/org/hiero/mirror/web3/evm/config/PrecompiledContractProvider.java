// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import java.util.Map;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

public interface PrecompiledContractProvider {
    Map<String, PrecompiledContract> getHederaPrecompiles();
}
