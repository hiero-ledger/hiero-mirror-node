// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.hook.HookStorageChange.Id;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Returns the latest change of each key within the timestamp range as a hook storage slot. Each query has an ascending
 * and a descending variant since PostgreSQL requires the leading order by expression to match distinct on (key), so
 * the order can't be a case expression.
 */
public interface HookStorageChangeRepository extends PagingAndSortingRepository<HookStorageChange, Id> {

    String KEYS_CONDITION = """
              and key in (:keys)
            """;

    String KEY_RANGE_CONDITION = """
              and key >= :keyLowerBound
              and key <= :keyUpperBound
            """;

    String ORDER_ASC = "order by key asc, consensus_timestamp desc limit :limit";

    String ORDER_DESC = "order by key desc, consensus_timestamp desc limit :limit";

    String SELECT_LATEST_CHANGE = """
            select distinct on (key)
                 owner_id,
                 hook_id,
                 key,
                 value_written       as "value",
                 consensus_timestamp as "modified_timestamp",
                 consensus_timestamp as "consensus_timestamp",
                 0                   as "created_timestamp",
                 (value_written is null or length(value_written) = 0) as "deleted"
            from hook_storage_change
            where owner_id = :ownerId
              and hook_id = :hookId
              and consensus_timestamp >= :timestampLowerBound
              and consensus_timestamp <= :timestampUpperBound
            """;

    default List<HookStorage> findByKeyBetweenAndTimestampBetween(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            Direction order,
            int limit) {
        return order.isAscending()
                ? findByKeyBetweenAsc(
                        ownerId, hookId, keyLowerBound, keyUpperBound, timestampLowerBound, timestampUpperBound, limit)
                : findByKeyBetweenDesc(
                        ownerId, hookId, keyLowerBound, keyUpperBound, timestampLowerBound, timestampUpperBound, limit);
    }

    @Query(value = SELECT_LATEST_CHANGE + KEY_RANGE_CONDITION + ORDER_ASC)
    List<HookStorage> findByKeyBetweenAsc(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            int limit);

    @Query(value = SELECT_LATEST_CHANGE + KEY_RANGE_CONDITION + ORDER_DESC)
    List<HookStorage> findByKeyBetweenDesc(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            int limit);

    default List<HookStorage> findByKeyInAndTimestampBetween(
            long ownerId,
            long hookId,
            Collection<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            Direction order,
            int limit) {
        return order.isAscending()
                ? findByKeyInAsc(ownerId, hookId, keys, timestampLowerBound, timestampUpperBound, limit)
                : findByKeyInDesc(ownerId, hookId, keys, timestampLowerBound, timestampUpperBound, limit);
    }

    @Query(value = SELECT_LATEST_CHANGE + KEYS_CONDITION + ORDER_ASC)
    List<HookStorage> findByKeyInAsc(
            long ownerId,
            long hookId,
            Collection<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            int limit);

    @Query(value = SELECT_LATEST_CHANGE + KEYS_CONDITION + ORDER_DESC)
    List<HookStorage> findByKeyInDesc(
            long ownerId,
            long hookId,
            Collection<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            int limit);
}
