// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractLog;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractLogRepository extends CrudRepository<ContractLog, ContractLog.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from contract_log where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
