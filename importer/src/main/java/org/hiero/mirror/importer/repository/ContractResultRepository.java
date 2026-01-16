// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractResult;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractResultRepository extends CrudRepository<ContractResult, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ContractResult where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
