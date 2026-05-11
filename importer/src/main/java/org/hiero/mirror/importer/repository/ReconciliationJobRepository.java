// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.job.ReconciliationJob;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ReconciliationJobRepository extends CrudRepository<ReconciliationJob, Long>, RetentionRepository {

    @Query("select * from reconciliation_job order by timestamp_start desc limit 1")
    Optional<ReconciliationJob> findLatest();

    @Modifying
    @Override
    @Query("delete from reconciliation_job where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
