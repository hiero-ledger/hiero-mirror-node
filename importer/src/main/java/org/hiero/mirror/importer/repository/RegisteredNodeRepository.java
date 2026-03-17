// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredNodeServiceEndpoints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface RegisteredNodeRepository extends CrudRepository<RegisteredNode, Long> {

    @Query(
            value = "SELECT service_endpoints FROM registered_node WHERE deleted = false AND :typeId = ANY(type)",
            nativeQuery = true)
    List<RegisteredNodeServiceEndpoints> findServiceEndpointsByDeletedFalseAndTypeContains(short typeId);
}
