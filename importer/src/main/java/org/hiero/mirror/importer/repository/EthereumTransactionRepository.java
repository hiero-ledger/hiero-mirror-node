// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EthereumTransactionRepository extends CrudRepository<EthereumTransaction, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            value = "delete from ethereum_transaction where consensus_timestamp <= :consensusTimestamp",
            nativeQuery = true)
    int prune(long consensusTimestamp);
}
