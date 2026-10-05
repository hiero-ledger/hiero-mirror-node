// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractResult;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractResultRepository extends CrudRepository<ContractResult, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from contract_result where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
