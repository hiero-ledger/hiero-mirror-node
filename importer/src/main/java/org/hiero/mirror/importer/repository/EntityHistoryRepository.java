// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityHistoryRepository extends CrudRepository<EntityHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from entity_history where timestamp_range << int8range(?, null)")
    int prune(long consensusTimestamp);

    @Modifying
    @Query(value = "update entity_history set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'")
    int updateContractType(Iterable<Long> ids);
}
