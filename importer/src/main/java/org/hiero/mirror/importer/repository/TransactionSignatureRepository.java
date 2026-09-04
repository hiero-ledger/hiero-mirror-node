// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TransactionSignatureRepository
        extends CrudRepository<TransactionSignature, TransactionSignature.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from transaction_signature where consensus_timestamp <= :consensusTimestamp")
    int prune(@Param("consensusTimestamp") long consensusTimestamp);
}
