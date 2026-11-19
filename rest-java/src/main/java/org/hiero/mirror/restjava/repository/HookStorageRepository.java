// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Each query has an ascending and a descending variant instead of a case expression on the order, so PostgreSQL reads
 * the (owner_id, hook_id, key) primary key index in order and stops at the limit instead of sorting all the slots of
 * the hook.
 */
public interface HookStorageRepository extends PagingAndSortingRepository<HookStorage, Id> {

    String SELECT_BY_KEYS = """
            select * from hook_storage
            where owner_id = :ownerId
              and hook_id = :hookId
              and key in (:keys)
              and deleted is false
            """;

    String SELECT_BY_KEY_RANGE = """
            select * from hook_storage
            where owner_id = :ownerId
              and hook_id = :hookId
              and key >= :fromKey
              and key <= :toKey
              and deleted is false
            """;

    @Query(value = SELECT_BY_KEY_RANGE + "order by key asc limit :limit")
    List<HookStorage> findByKeyBetweenAsc(long ownerId, long hookId, byte[] fromKey, byte[] toKey, int limit);

    @Query(value = SELECT_BY_KEY_RANGE + "order by key desc limit :limit")
    List<HookStorage> findByKeyBetweenDesc(long ownerId, long hookId, byte[] fromKey, byte[] toKey, int limit);

    @Query(value = SELECT_BY_KEYS + "order by key asc limit :limit")
    List<HookStorage> findByKeyInAsc(long ownerId, long hookId, List<byte[]> keys, int limit);

    @Query(value = SELECT_BY_KEYS + "order by key desc limit :limit")
    List<HookStorage> findByKeyInDesc(long ownerId, long hookId, List<byte[]> keys, int limit);

    default List<HookStorage> findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
            long ownerId, long hookId, byte[] fromKey, byte[] toKey, Direction order, int limit) {
        return order.isAscending()
                ? findByKeyBetweenAsc(ownerId, hookId, fromKey, toKey, limit)
                : findByKeyBetweenDesc(ownerId, hookId, fromKey, toKey, limit);
    }

    default List<HookStorage> findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
            long ownerId, long hookId, List<byte[]> keys, Direction order, int limit) {
        return order.isAscending()
                ? findByKeyInAsc(ownerId, hookId, keys, limit)
                : findByKeyInDesc(ownerId, hookId, keys, limit);
    }
}
