// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface RegisteredNodeRepository extends CrudRepository<RegisteredNode, Long> {

    @Query(
            value = "select * from registered_node where deleted is false and type @> array[:typeId]::smallint[]",
            nativeQuery = true)
    List<RegisteredNode> findAllByDeletedFalseAndTypeContains(short typeId);
}
