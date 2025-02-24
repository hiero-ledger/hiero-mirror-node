// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EthereumTransactionRepository extends CrudRepository<EthereumTransaction, Long> {

    Optional<EthereumTransaction> findByConsensusTimestampAndPayerAccountId(
            long consensusTimestamp, EntityId payerAccountId);
}
