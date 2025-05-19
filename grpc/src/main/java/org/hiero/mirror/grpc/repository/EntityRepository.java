// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;
import static org.hiero.mirror.grpc.config.CacheConfiguration.ENTITY_CACHE;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {
    @Cacheable(cacheNames = CACHE_NAME, cacheManager = ENTITY_CACHE, unless = "#result == null")
    Optional<Entity> findById(long entityId);
}
