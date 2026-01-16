// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TransactionSignatureRepository
        extends CrudRepository<TransactionSignature, TransactionSignature.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from TransactionSignature where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
