// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.annotation.Nullable;
import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RegisteredNodeRepository extends PagingAndSortingRepository<RegisteredNode, Long> {

    @Query(value = """
            select * from registered_node
            where registered_node_id >= :lowerBound
            and registered_node_id <= :upperBound
            and deleted is false
            and (:type is null or type @> array[:type]::smallint[])
            """, nativeQuery = true)
    List<RegisteredNode> findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIs(
            long lowerBound, long upperBound, @Nullable Short type, Pageable pageable);
}
