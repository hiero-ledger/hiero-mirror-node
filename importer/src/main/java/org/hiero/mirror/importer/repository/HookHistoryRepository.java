// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.domain.hook.HookHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface HookHistoryRepository extends CrudRepository<HookHistory, AbstractHook.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from hook_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
