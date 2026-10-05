// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.springframework.data.domain.Pageable;

interface HookStorageRepositoryCustom {

    List<HookStorage> findByOwnerIdAndHookIdAndKeyInAndDeletedIsFalse(
            long ownerId, long hookId, Collection<byte[]> keys, Pageable pageable);

    List<HookStorage> findByOwnerIdAndHookIdAndKeyBetweenAndDeletedIsFalse(
            long ownerId, long hookId, byte[] fromKey, byte[] toKey, Pageable pageable);
}
