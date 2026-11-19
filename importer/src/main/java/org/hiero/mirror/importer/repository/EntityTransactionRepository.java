// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityTransactionRepository
        extends CrudRepository<EntityTransaction, EntityTransaction.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from entity_transaction where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
