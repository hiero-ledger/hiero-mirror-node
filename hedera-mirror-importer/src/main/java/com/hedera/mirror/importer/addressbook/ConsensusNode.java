// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.addressbook;

import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.annotation.Nonnull;
import java.security.PublicKey;

/**
 * Represents a consensus node while abstracting away the possible different sources of node information.
 */
public interface ConsensusNode extends Comparable<ConsensusNode> {

    default int compareTo(@Nonnull ConsensusNode other) {
        return Long.compare(getNodeId(), other.getNodeId());
    }

    EntityId getNodeAccountId();

    long getNodeId();

    PublicKey getPublicKey();

    /**
     * The node's current stake in tinybars. If staking is not activated, this will be set to one.
     *
     * @return The current node stake in tinybars
     */
    long getStake();

    /**
     * The network's current total aggregate stake in tinybars. If staking is not activated, this will be set to the
     * number of nodes in the address book.
     *
     * @return the current total network stake in tinybars
     */
    long getTotalStake();
}
