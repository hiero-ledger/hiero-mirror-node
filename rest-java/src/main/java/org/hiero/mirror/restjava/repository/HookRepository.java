// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.AbstractHook.Id;
import org.hiero.mirror.common.domain.hook.Hook;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * The order parameter is either 'ASC' or 'DESC'. An owner has few hooks, so ordering by a case expression instead of
 * reading the primary key index in order is cheap.
 */
public interface HookRepository extends PagingAndSortingRepository<Hook, Id> {

    @Query(value = """
            select * from hook
            where owner_id = :ownerId
              and hook_id >= :lowerBound
              and hook_id <= :upperBound
            order by
              case when :order = 'ASC' then hook_id end asc,
              case when :order = 'DESC' then hook_id end desc
            limit :limit
            """, nativeQuery = true)
    List<Hook> findByOwnerIdAndHookIdBetween(long ownerId, long lowerBound, long upperBound, String order, int limit);

    @Query(value = """
            select * from hook
            where owner_id = :ownerId
              and hook_id in (:hookIds)
            order by
              case when :order = 'ASC' then hook_id end asc,
              case when :order = 'DESC' then hook_id end desc
            limit :limit
            """, nativeQuery = true)
    List<Hook> findByOwnerIdAndHookIdIn(long ownerId, Collection<Long> hookIds, String order, int limit);
}
