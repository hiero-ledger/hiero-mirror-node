// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.web3.repository.projections.ContractTransactionHashLookup;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTransactionHashRepository extends CrudRepository<ContractTransactionHash, byte[]> {

    @Query(value = """
            select consensus_timestamp, entity_id, payer_account_id, transaction_result
            from contract_transaction_hash
            where hash = :hash
            order by (transaction_result = 22) desc, consensus_timestamp desc
            """, rowMapperClass = ContractTransactionHashLookupRowMapper.class)
    List<ContractTransactionHashLookup> findAllByHash(byte[] hash);
}
