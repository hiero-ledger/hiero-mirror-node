// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.springframework.data.domain.Pageable;

interface HookStorageChangeRepositoryCustom {

    List<HookStorage> findByKeyBetweenAndTimestampBetween(
            long ownerId,
            long hookId,
            byte[] keyLowerBound,
            byte[] keyUpperBound,
            long timestampLowerBound,
            long timestampUpperBound,
            Pageable pageable);

    List<HookStorage> findByKeyInAndTimestampBetween(
            long ownerId,
            long hookId,
            Collection<byte[]> keys,
            long timestampLowerBound,
            long timestampUpperBound,
            Pageable pageable);
}
