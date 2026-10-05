// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface EthereumTransactionRepository extends CrudRepository<EthereumTransaction, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ethereum_transaction where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
