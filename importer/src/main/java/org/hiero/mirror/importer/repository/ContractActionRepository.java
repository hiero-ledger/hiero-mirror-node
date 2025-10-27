// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractActionRepository
        extends CrudRepository<ContractAction, ContractAction.Id>, RetentionRepository {

    List<ContractAction> findByConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query("delete from ContractAction where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
