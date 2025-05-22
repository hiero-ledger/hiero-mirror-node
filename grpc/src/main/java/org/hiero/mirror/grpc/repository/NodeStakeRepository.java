// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.grpc.config.CacheConfiguration.NODE_STAKE_CACHE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NodeStakeRepository extends CrudRepository<NodeStake, NodeStake.Id> {
    @Query(value = "select max(consensus_timestamp) from node_stake", nativeQuery = true)
    Optional<Long> findLatestTimestamp();

    List<NodeStake> findAllByConsensusTimestamp(long consensusTimestamp);

    // An empty map may be cached, indicating the node_stake table is empty
    @Cacheable(cacheManager = NODE_STAKE_CACHE, cacheNames = CACHE_NAME)
    default Map<Long, Long> findAllStakeByConsensusTimestamp(long consensusTimestamp) {
        return findAllByConsensusTimestamp(consensusTimestamp).stream()
                .collect(Collectors.toUnmodifiableMap(NodeStake::getNodeId, NodeStake::getStake));
    }
}
