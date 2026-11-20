// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface HookStorageChangeRepository
        extends CrudRepository<HookStorageChange, HookStorageChange.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            value = "delete from hook_storage_change where consensus_timestamp <= :consensusTimestamp",
            nativeQuery = true)
    int prune(long consensusTimestamp);
}
