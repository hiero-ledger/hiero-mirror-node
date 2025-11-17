// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageChangeRepository extends PagingAndSortingRepository<HookStorageChange, Id> {

    @Query(
            value =
                    """
        SELECT DISTINCT ON (key) *
        FROM hook_storage_change
        WHERE owner_id = :ownerId
          AND hook_id = :hookId
          AND key >= :keyLowerBound
          AND key <= :keyUpperBound
          AND consensus_timestamp BETWEEN :timestampLowerBound AND :timestampUpperBound
        ORDER BY key DESC, consensus_timestamp DESC
        """,
            nativeQuery = true)
    List<HookStorageChange> findLatestChangePerKeyInTimestampRangeForKeyRangeOrderByKeyDesc(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest pageable);

    @Query(
            value =
                    """
        SELECT DISTINCT ON (key) *
        FROM hook_storage_change
        WHERE owner_id = :ownerId
          AND hook_id = :hookId
          AND key >= :keyLowerBound
          AND key <= :keyUpperBound
          AND consensus_timestamp BETWEEN :timestampLowerBound AND :timestampUpperBound
        ORDER BY key ASC, consensus_timestamp DESC
        """,
            nativeQuery = true)
    List<HookStorageChange> findLatestChangePerKeyInTimestampRangeForKeyRangeOrderByKeyAsc(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest pageable);

    @Query(
            value =
                    """
            SELECT DISTINCT ON (key) *
            FROM hook_storage_change
            WHERE owner_id = :ownerId
              AND hook_id = :hookId
              AND key IN (:keys)
              AND consensus_timestamp BETWEEN :timestampLowerBound AND :timestampUpperBound
            ORDER BY key DESC, consensus_timestamp DESC
            """,
            nativeQuery = true)
    List<HookStorageChange> findLatestChangePerKeyInTimestampRangeForKeysOrderByKeyDesc(
            long ownerId,
            long hookId,
            List<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest pageable);

    @Query(
            value =
                    """
            SELECT DISTINCT ON (key) *
            FROM hook_storage_change
            WHERE owner_id = :ownerId
              AND hook_id = :hookId
              AND key IN (:keys)
              AND consensus_timestamp BETWEEN :timestampLowerBound AND :timestampUpperBound
            ORDER BY key ASC, consensus_timestamp DESC
            """,
            nativeQuery = true)
    List<HookStorageChange> findLatestChangePerKeyInTimestampRangeForKeysOrderByKeyAsc(
            long ownerId,
            long hookId,
            List<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest pageable);
}
