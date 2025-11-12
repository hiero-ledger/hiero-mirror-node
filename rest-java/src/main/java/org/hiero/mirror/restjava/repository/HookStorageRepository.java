// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorage.Id;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface HookStorageRepository extends PagingAndSortingRepository<HookStorage, Id> {

    List<HookStorage> findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
            long ownerId, long hookId, List<byte[]> key, Pageable pageable);

    List<HookStorage> findByOwnerIdAndHookIdAndKeyBetween(
            long ownerId, long hookId, byte[] fromKey, byte[] toKey, Pageable pageable);
}
