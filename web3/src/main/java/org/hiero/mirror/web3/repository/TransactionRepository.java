// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    @Query(value = """
            select * from transaction
            where payer_account_id = :payerAccountId
              and valid_start_ns = :validStartNs
              and consensus_timestamp >= :consensusTimestampStart
              and consensus_timestamp <= :consensusTimestampEnd
            order by consensus_timestamp asc
            """, nativeQuery = true)
    List<Transaction> findByPayerAccountIdAndValidStartNs(
            @Param("payerAccountId") long payerAccountId,
            @Param("validStartNs") long validStartNs,
            @Param("consensusTimestampStart") long consensusTimestampStart,
            @Param("consensusTimestampEnd") long consensusTimestampEnd);
}
