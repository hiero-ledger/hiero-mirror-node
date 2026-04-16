// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.addressbook;

import java.util.Collection;

/**
 * Maintains state about the current consensus nodes.
 */
public interface ConsensusNodeService {

    /**
     * Gets a consensus node by its node id.
     *
     * @param nodeId - The consensus node id
     * @return The matching consensus node, {@code null} if not found
     */
    ConsensusNode getNode(long nodeId);

    /**
     * Retrieves a list of consensus nodes. The data may be cached and not always reflect the current state of the
     * database.
     *
     * @return an unmodifiable list of consensus nodes
     */
    Collection<ConsensusNode> getNodes();

    /**
     * Requests that the service refreshes its node information. The implementation may choose to ignore this or
     * execute it lazily on the next request.
     */
    void refresh();
}
