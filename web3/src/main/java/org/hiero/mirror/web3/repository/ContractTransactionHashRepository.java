// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTransactionHashRepository extends CrudRepository<ContractTransactionHash, byte[]> {

    @Query("select * from contract_transaction_hash where hash = :hash order by consensus_timestamp asc limit 1")
    Optional<ContractTransactionHash> findByHash(byte[] hash);
}
