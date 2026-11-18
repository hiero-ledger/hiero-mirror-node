// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

public interface RegisteredNodeRepositoryCustom {

    /**
     * Finds the non-deleted registered nodes with an id within the inclusive range and, optionally, of a given type.
     *
     * @param lowerBound the inclusive lower bound of the registered node id
     * @param upperBound the inclusive upper bound of the registered node id
     * @param type       the registered node type id to filter by, or null to not filter by type
     * @param order      the sort order by registered node id
     * @param limit      the maximum number of registered nodes to return
     * @return the matching registered nodes
     */
    List<RegisteredNode> findRegisteredNodes(
            long lowerBound, long upperBound, @Nullable Short type, Direction order, int limit);
}
