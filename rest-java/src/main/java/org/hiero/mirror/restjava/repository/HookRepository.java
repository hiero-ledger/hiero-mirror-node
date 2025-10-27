// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.AbstractHook.Id;
import org.hiero.mirror.common.domain.hook.Hook;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookRepository extends PagingAndSortingRepository<Hook, Id> {

    List<Hook> findByOwnerIdOrderByHookIdAsc(long ownerId, Pageable pageable);

    List<Hook> findByOwnerIdOrderByHookIdDesc(long ownerId, Pageable pageable);

    List<Hook> findByOwnerIdAndHookIdLessThanOrderByHookIdAsc(long ownerId, long hookId, Pageable pageable);

    List<Hook> findByOwnerIdAndHookIdLessThanOrderByHookIdDesc(long ownerId, long hookId, Pageable pageable);

    List<Hook> findByOwnerIdAndHookIdGreaterThanOrderByHookIdAsc(long ownerId, long hookId, Pageable pageable);

    List<Hook> findByOwnerIdAndHookIdGreaterThanOrderByHookIdDesc(long ownerId, long hookId, Pageable pageable);

    Hook findByOwnerIdAndHookId(long ownerId, long hookId);
}
