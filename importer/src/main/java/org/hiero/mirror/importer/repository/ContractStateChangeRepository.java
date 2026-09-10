// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateChangeRepository
        extends CrudRepository<ContractStateChange, ContractStateChange.Id>, RetentionRepository {

    @Query("select * from contract_state_change where consensus_timestamp = :consensusTimestamp")
    List<ContractStateChange> findByConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query("delete from contract_state_change where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
