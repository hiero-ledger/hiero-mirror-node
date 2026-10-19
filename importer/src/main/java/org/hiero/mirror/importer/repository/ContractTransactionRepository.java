// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface ContractTransactionRepository
        extends CrudRepository<ContractTransaction, ContractTransaction.Id>, RetentionRepository {
    @Modifying
    @Override
    @Query("delete from contract_transaction where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
