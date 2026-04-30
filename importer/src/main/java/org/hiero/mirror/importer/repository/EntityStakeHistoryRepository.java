// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityStakeHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityStakeHistoryRepository extends CrudRepository<EntityStakeHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from entity_stake_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
