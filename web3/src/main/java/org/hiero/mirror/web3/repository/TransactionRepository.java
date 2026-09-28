// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    List<Transaction> findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
            EntityId payerAccountId, long validStartNs);

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
            order by
              (result = 22) desc,
              consensus_timestamp desc
            limit 1
            """, nativeQuery = true)
    Optional<Transaction> findByTransactionId(
            @Param("payerAccountId") long payerAccountId,
            @Param("validStartNs") long validStartNs,
            @Param("consensusTimestampStart") long consensusTimestampStart,
            @Param("consensusTimestampEnd") long consensusTimestampEnd);

    /**
     * Preceding hollow {@code CryptoCreateAccount} children use {@code parent - 1}, {@code parent - 2}, …
     * Bounded so Postgres can use {@code transaction__type_consensus_timestamp} instead of scanning
     * {@code parent_consensus_timestamp} (unindexed). Network default {@code maxPrecedingRecords} is 3.
     */
    long PRECEDING_CRYPTO_CREATE_WINDOW_NS = 10_000L;

    /**
     * Entity IDs created by successful child {@code CryptoCreateAccount} transactions of the given parent,
     * including preceding hollow-account creates.
     */
    default List<Long> findSuccessfulCryptoCreateChildEntityIds(final long parentConsensusTimestamp) {
        return findSuccessfulCryptoCreateChildEntityIds(
                parentConsensusTimestamp, parentConsensusTimestamp - PRECEDING_CRYPTO_CREATE_WINDOW_NS);
    }

    @Query(value = """
            select entity_id
            from transaction
            where type = 11
              and result = 22
              and consensus_timestamp >= :consensusTimestampStart
              and consensus_timestamp <= :parentConsensusTimestamp
              and parent_consensus_timestamp = :parentConsensusTimestamp
              and entity_id is not null
            """, nativeQuery = true)
    List<Long> findSuccessfulCryptoCreateChildEntityIds(
            @Param("parentConsensusTimestamp") long parentConsensusTimestamp,
            @Param("consensusTimestampStart") long consensusTimestampStart);
}
