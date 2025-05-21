// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractAction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractActionRepository
        extends CrudRepository<ContractAction, ContractAction.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ContractAction where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
