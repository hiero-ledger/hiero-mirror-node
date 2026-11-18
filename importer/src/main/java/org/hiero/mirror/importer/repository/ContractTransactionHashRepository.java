// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTransactionHashRepository
        extends CrudRepository<ContractTransactionHash, byte[]>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            value = "delete from contract_transaction_hash where consensus_timestamp <= :consensusTimestamp",
            nativeQuery = true)
    int prune(long consensusTimestamp);
}
