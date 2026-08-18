// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface CryptoTransferRepository
        extends CrudRepository<CryptoTransfer, CryptoTransfer.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from crypto_transfer where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
