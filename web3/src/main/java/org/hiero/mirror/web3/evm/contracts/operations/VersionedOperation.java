// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.operations;

import com.hedera.hapi.node.base.SemanticVersion;
import org.hyperledger.besu.evm.operation.Operation;

/** A mirror node opcode override that is registered only on EVM versions where its opcode exists. */
public interface VersionedOperation extends Operation {

    /** The first EVM version that defines this opcode. */
    SemanticVersion minimumEvmVersion();

    default boolean isSupported(final SemanticVersion evmVersion) {
        final var minimum = minimumEvmVersion();
        if (evmVersion.major() != minimum.major()) {
            return evmVersion.major() > minimum.major();
        }
        if (evmVersion.minor() != minimum.minor()) {
            return evmVersion.minor() > minimum.minor();
        }
        return evmVersion.patch() >= minimum.patch();
    }
}
