// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityTransactionRepository
        extends CrudRepository<EntityTransaction, EntityTransaction.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from EntityTransaction where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
