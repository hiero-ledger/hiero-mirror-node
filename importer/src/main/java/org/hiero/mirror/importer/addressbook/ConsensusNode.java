// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.addressbook;

import java.security.PublicKey;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.jspecify.annotations.NonNull;

/**
 * Represents a consensus node while abstracting away the possible different sources of node information.
 */
public interface ConsensusNode extends Comparable<ConsensusNode> {

    default int compareTo(@NonNull ConsensusNode other) {
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
