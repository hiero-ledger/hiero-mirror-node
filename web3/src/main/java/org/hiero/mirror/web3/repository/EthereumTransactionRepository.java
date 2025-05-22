// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EthereumTransactionRepository extends CrudRepository<EthereumTransaction, Long> {

    Optional<EthereumTransaction> findByConsensusTimestampAndPayerAccountId(
            long consensusTimestamp, EntityId payerAccountId);
}
