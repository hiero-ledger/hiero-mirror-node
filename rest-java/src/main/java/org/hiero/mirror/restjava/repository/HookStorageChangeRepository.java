// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageChangeRepository extends PagingAndSortingRepository<HookStorageChange, Id> {

    List<HookStorageChange> findByOwnerIdAndHookIdAndConsensusTimestampIn(
            long ownerId, long hookId, List<Long> timestamp, PageRequest page);

    List<HookStorageChange> findByOwnerIdAndHookIdAndConsensusTimestampBetween(
            long ownerId, long hookId, long timestampLowerBound, long timestampUpperBound, PageRequest page);

    List<HookStorageChange> findByOwnerIdAndHookIdAndKeyInAndConsensusTimestampIn(
            long ownerId, long hookId, List<byte[]> keys, List<Long> timestamps, PageRequest page);

    List<HookStorageChange> findByOwnerIdAndHookIdAndKeyInAndConsensusTimestampBetween(
            long ownerId,
            long hookId,
            List<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest page);

    List<HookStorageChange> findByOwnerIdAndHookIdAndKeyBetweenAndConsensusTimestampIn(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            List<Long> timestamps,
            PageRequest page);

    List<HookStorageChange> findByOwnerIdAndHookIdAndKeyBetweenAndConsensusTimestampBetween(
            long id,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            PageRequest page);
}
