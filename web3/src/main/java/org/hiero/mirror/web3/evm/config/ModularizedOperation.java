// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import org.hyperledger.besu.evm.operation.Operation;

/**
 * This interface identifies classes that are applicable to the modularized EVM library. The code may still be
 * applicable to the mono code base as well. This can be deleted once modularization is complete.
 */
public interface ModularizedOperation extends Operation {}
