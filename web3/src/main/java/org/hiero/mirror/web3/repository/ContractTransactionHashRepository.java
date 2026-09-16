// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTransactionHashRepository extends CrudRepository<ContractTransactionHash, byte[]> {

    @Query(
            value = "select cth.* from contract_transaction_hash cth where cth.hash = ?1 "
                    + "order by (cth.transaction_result = 22) desc, "
                    + "exists (select 1 from contract_transaction ct "
                    + "where ct.consensus_timestamp = cth.consensus_timestamp and ct.entity_id = cth.entity_id) desc, "
                    + "cth.consensus_timestamp desc limit 1",
            nativeQuery = true)
    Optional<ContractTransactionHash> findByHash(byte[] hash);
}
