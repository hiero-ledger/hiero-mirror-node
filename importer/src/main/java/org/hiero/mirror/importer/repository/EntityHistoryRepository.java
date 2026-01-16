// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityHistoryRepository extends CrudRepository<EntityHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from entity_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);

    @Modifying
    @Query(
            value = "update entity_history set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'",
            nativeQuery = true)
    int updateContractType(Iterable<Long> ids);
}
