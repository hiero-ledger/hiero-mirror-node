// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RegisteredNodeRepository extends PagingAndSortingRepository<RegisteredNode, Long> {

    /**
     * Finds the non-deleted registered nodes with an id within the inclusive range and, optionally, of a given type.
     *
     * @param lowerBound the inclusive lower bound of the registered node id
     * @param upperBound the inclusive upper bound of the registered node id
     * @param type       the registered node type id to filter by, or null to not filter by type
     * @param order      the sort order by registered node id, either 'ASC' or 'DESC'
     * @param limit      the maximum number of registered nodes to return
     * @return the matching registered nodes
     */
    @Query(value = """
            select * from registered_node
            where registered_node_id >= :lowerBound
              and registered_node_id <= :upperBound
              and deleted is false
              and (cast(:type as smallint) is null or type @> array[cast(:type as smallint)])
            order by
              case when :order = 'ASC' then registered_node_id end asc,
              case when :order = 'DESC' then registered_node_id end desc
            limit :limit
            """)
    List<RegisteredNode> findRegisteredNodes(
            long lowerBound, long upperBound, @Nullable Short type, String order, int limit);
}
