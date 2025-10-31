// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.AbstractHook.Id;
import org.hiero.mirror.common.domain.hook.Hook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookRepository extends PagingAndSortingRepository<Hook, Id> {
    List<Hook> findByOwnerIdAndHookIdIn(long ownerId, Collection<Long> hookIds, Pageable pageable);

    List<Hook> findByOwnerId(long ownerId, Pageable pageable);

    List<Hook> findByOwnerIdAndHookIdBetween(long ownerId, long lowerBound, long upperBound, Pageable pageable);
}
