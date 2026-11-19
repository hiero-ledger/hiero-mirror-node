// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface HookStorageChangeRepository
        extends CrudRepository<HookStorageChange, HookStorageChange.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from hook_storage_change where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
