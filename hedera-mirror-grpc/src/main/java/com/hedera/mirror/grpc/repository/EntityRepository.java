// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.repository;

import static com.hedera.mirror.grpc.config.CacheConfiguration.CACHE_NAME;
import static com.hedera.mirror.grpc.config.CacheConfiguration.ENTITY_CACHE;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {
    @Cacheable(cacheNames = CACHE_NAME, cacheManager = ENTITY_CACHE, unless = "#result == null")
    Optional<Entity> findById(long entityId);
}
