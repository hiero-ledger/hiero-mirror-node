// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;

public interface EthereumTransactionParser {

    /**
     * Decodes raw ethereum transaction bytes into an {@link EthereumTransaction} object
     *
     * @param transactionBytes The raw ethereum transaction bytes
     * @return {@link EthereumTransaction} object
     */
    EthereumTransaction decode(byte[] transactionBytes);

    /**
     * Gets the keccak256 hash of the ethereum transaction
     *
     * @param callData The call data decoded from the ethereum transaction
     * @param callDataId The file id of the call data when it is offloaded from the ethereum transaction
     * @param consensusTimestamp The consensus timestamp of the ethereum transaction
     * @param transactionBytes The raw bytes of the ethereum transaction, note the call data might be offloaded to
     *                         callDataId
     * @param useCurrentState Whether to use the current state or the historical state for loading call data from file
     * @return The keccak256 hash of the ethereum transaction, or an empty byte array if the hash cannot be computed
     */
    byte[] getHash(
            byte[] callData,
            EntityId callDataId,
            long consensusTimestamp,
            byte[] transactionBytes,
            boolean useCurrentState);
}
