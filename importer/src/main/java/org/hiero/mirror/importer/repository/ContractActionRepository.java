// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractActionRepository
        extends CrudRepository<ContractAction, ContractAction.Id>, RetentionRepository {

    @Query("select * from contract_action where consensus_timestamp = :consensusTimestamp")
    List<ContractAction> findByConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query("delete from contract_action where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
