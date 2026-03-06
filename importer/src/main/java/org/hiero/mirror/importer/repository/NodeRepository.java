// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.node.Node;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NodeRepository extends CrudRepository<Node, Long> {

    @Query(value = """
            select distinct unnest(associated_registered_nodes) as id
            from node
            where deleted = false
            and associated_registered_nodes is not null
            """, nativeQuery = true)
    List<Long> findAllAssociatedRegisteredNodeIds();
}
