// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.grpc.config.CacheConfiguration.NODE_ACCOUNT_CACHE;

import java.util.HashMap;
import java.util.Map;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.Node;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NodeRepository extends CrudRepository<Node, Long> {

    @Query(value = "select * from node where deleted is false and account_id is not null", nativeQuery = true)
    Iterable<Node> findNodesWithAccountIds();

    @Cacheable(
            cacheManager = NODE_ACCOUNT_CACHE,
            cacheNames = CACHE_NAME,
            unless = "#result == null or #result.isEmpty()")
    default Map<Long, EntityId> findNodesWithAccountIdsMap() {
        var map = new HashMap<Long, EntityId>();
        findNodesWithAccountIds().forEach(node -> map.putIfAbsent(node.getNodeId(), node.getAccountId()));
        return map;
    }
}
