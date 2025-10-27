// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateChangeRepository
        extends CrudRepository<ContractStateChange, ContractStateChange.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ContractStateChange where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);

    List<ContractStateChange> findByConsensusTimestamp(long consensusTimestamp);
}
