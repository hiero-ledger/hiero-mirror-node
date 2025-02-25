// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTransactionHashRepository
        extends CrudRepository<ContractTransactionHash, byte[]>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ContractTransactionHash where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
