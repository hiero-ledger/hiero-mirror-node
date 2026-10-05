// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends CrudRepository<Transaction, Long>, RetentionRepository {

    List<Transaction> findByConsensusTimestampBetween(long startInclusive, long endInclusive, Pageable pageable);

    @Modifying
    @Override
    @Query("delete from transaction where consensus_timestamp <= :consensusTimestamp")
    int prune(@Param("consensusTimestamp") long consensusTimestamp);
}
