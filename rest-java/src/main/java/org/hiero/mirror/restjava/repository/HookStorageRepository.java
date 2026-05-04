// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageRepository extends PagingAndSortingRepository<HookStorage, Id> {

    @Query("""
            select created_timestamp,
                   deleted,
                   owner_id,
                   hook_id,
                   key,
                   modified_timestamp,
                   value
            from hook_storage
            where owner_id = :ownerId
              and hook_id = :hookId
              and key in (:keys)
              and deleted = false
            """)
    List<HookStorage> findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
            long ownerId, long hookId, Collection<byte[]> keys, Pageable pageable);

    @Query("""
            select created_timestamp,
                   deleted,
                   owner_id,
                   hook_id,
                   key,
                   modified_timestamp,
                   value
            from hook_storage
            where owner_id = :ownerId
              and hook_id = :hookId
              and key between :fromKey and :toKey
              and deleted = false
            """)
    List<HookStorage> findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
            long ownerId, long hookId, byte[] fromKey, byte[] toKey, Pageable pageable);
}
