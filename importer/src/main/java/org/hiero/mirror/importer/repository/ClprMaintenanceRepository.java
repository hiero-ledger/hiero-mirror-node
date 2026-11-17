// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprMaintenance;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprMaintenanceRepository extends CrudRepository<ClprMaintenance, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ClprMaintenance where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
