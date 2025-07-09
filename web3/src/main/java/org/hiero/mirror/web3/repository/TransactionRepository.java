// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    List<Transaction> findByPayerAccountIdAndValidStartNsOrderByConsensusTimestampAsc(
            EntityId payerAccountId, long validStartNs);
}
