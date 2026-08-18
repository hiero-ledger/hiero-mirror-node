// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityStakeHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityStakeHistoryRepository extends CrudRepository<EntityStakeHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from entity_stake_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
