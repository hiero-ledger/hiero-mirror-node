// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    /**
     * Returns the parent contract-related transaction for a transaction ID.
     * Uses {@code nonce = 0} so preceding hollow-account {@code CryptoCreateAccount} children are excluded.
     */
    @Query(value = """
            select *
            from transaction
            where payer_account_id = :payerAccountId
              and valid_start_ns = :validStartNs
              and consensus_timestamp >= :consensusTimestampStart
              and consensus_timestamp <= :consensusTimestampEnd
              and nonce = 0
              and type in (7, 8, 50)
            order by consensus_timestamp desc
            limit 1
            """, nativeQuery = true)
    Optional<Transaction> findByPayerAccountIdAndValidStartNs(
            @Param("payerAccountId") long payerAccountId,
            @Param("validStartNs") long validStartNs,
            @Param("consensusTimestampStart") long consensusTimestampStart,
            @Param("consensusTimestampEnd") long consensusTimestampEnd);
}
