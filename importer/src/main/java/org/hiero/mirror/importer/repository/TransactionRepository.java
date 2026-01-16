// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TransactionRepository extends CrudRepository<Transaction, Long>, RetentionRepository {

    List<Transaction> findByConsensusTimestampBetween(long startInclusive, long endInclusive, Pageable pageable);

    @Modifying
    @Override
    @Query("delete from Transaction where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
