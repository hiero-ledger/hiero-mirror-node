// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.contract.ContractLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractLogRepository extends CrudRepository<ContractLog, ContractLog.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ContractLog where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
